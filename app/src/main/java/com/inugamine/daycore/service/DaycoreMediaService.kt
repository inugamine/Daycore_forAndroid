package com.inugamine.daycore.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.inugamine.daycore.model.AudioPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Media3 MediaSessionService implementation for background playback and media controls.
 */
class DaycoreMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer

    // --- State Flows (moved from DaycorePlayerService) ---
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _speed = MutableStateFlow(0.80f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(-3.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null

    companion object {
        var instance: DaycoreMediaService? = null
            private set
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // オーディオレンダラーの構成をカスタマイズして速度変更時の安定性を高める
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableAudioTrackPlaybackParams(true) // Android 6.0+ のネイティブ速度変更を優先

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        mediaSession = MediaSession.Builder(this, player).build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        _duration.value = player.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        _isPlaying.value = false
                        _currentPosition.value = 0L
                        stopPositionUpdates()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // 曲が切り替わったら準備を確実にする
                player.prepare()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                // UI同期のみ行い、再帰呼び出しを防ぐ
                _speed.value = playbackParameters.speed
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // 再生エラー（音声停止）発生時の自動復旧
                val currentPos = player.currentPosition
                player.prepare()
                player.seekTo(currentPos)
                player.play()
            }
        })
        
        applyPreset(AudioPreset.DAYCORE)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // アプリがタスクから削除されたら必ず停止
        mediaSession?.player?.let {
            it.stop()
            it.clearMediaItems()
        }
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    // --- Playback Helpers ---

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var stuckCounter = 0
            var lastObservedPos = -1L
            
            while (isActive) {
                val pos = player.currentPosition
                val dur = player.duration
                _currentPosition.value = pos.coerceAtLeast(0L)

                // 低速再生（Daycore）時の曲末尾スタック対策
                if (player.playWhenReady && dur > 0) {
                    // 残り 1000ms 以内で位置が進まなくなった、または終端に達した場合
                    if (pos >= dur - 1000 || (pos == lastObservedPos && pos > dur - 2000)) {
                        stuckCounter++
                    } else {
                        stuckCounter = 0
                    }

                    // 3回連続（約600ms）で停滞が確認されたら強制的に次へ
                    if (stuckCounter >= 3) {
                        stuckCounter = 0
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                            player.prepare()
                            player.play()
                        } else if (player.repeatMode == Player.REPEAT_MODE_ALL) {
                            player.seekToDefaultPosition(0)
                            player.prepare()
                            player.play()
                        }
                    }
                }
                
                lastObservedPos = pos
                delay(200)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
    }

    fun applyPreset(preset: AudioPreset) {
        _speed.value = preset.rate
        _pitch.value = preset.semitones
        updatePlaybackParams()
    }

    fun updateParameters(speed: Float, pitch: Float) {
        _speed.value = speed
        _pitch.value = pitch
        
        // UIスレッドで実行されていることを保証し、
        // 速度変更の適用を次のメインループまでわずかに遅延させて
        // 連続的なスライダー操作による Sonic プロセッサのオーバーロードを防ぐ
        scope.launch {
            updatePlaybackParams()
        }
    }

    private fun updatePlaybackParams() {
        val pitchFactor = 2f.pow(_pitch.value / 12f)
        val params = PlaybackParameters(_speed.value, pitchFactor)
        
        // 再生中に速度を大幅に変える際、Sonic (ExoPlayerの速度変更プロセッサ) が
        // バッファの不整合で音を止めてしまうことがあるため、一度 prepare() を呼んで
        // パイプラインをリフレッシュする
        if (player.playbackParameters != params) {
            val wasPlaying = player.isPlaying
            player.playbackParameters = params
            
            // 速度変更後に音が止まった場合の保険として、再生状態なら prepare を叩く
            if (wasPlaying && !player.isPlaying) {
                player.prepare()
                player.play()
            }
        }
    }
}
