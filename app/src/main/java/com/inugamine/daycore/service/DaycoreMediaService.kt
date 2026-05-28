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

    override fun onCreate() {
        super.onCreate()
        instance = this
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // オーディオフォーカス自動管理（Bluetooth 切り替え対応）
            )
            .setHandleAudioBecomingNoisy(true) // イヤホン/Bluetooth 切断時に自動一時停止
            .build()
        mediaSession = MediaSession.Builder(this, player).build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0L)
                }
                if (state == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    _currentPosition.value = 0L
                    stopPositionUpdates()
                }
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
            while (isActive) {
                _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                delay(500) // Lower frequency for background updates
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

    fun setSpeed(speed: Float) {
        _speed.value = speed
        updatePlaybackParams()
    }

    fun setPitch(semitones: Float) {
        _pitch.value = semitones
        updatePlaybackParams()
    }

    private fun updatePlaybackParams() {
        val pitchFactor = 2f.pow(_pitch.value / 12f)
        player.playbackParameters = PlaybackParameters(_speed.value, pitchFactor)
    }
}
