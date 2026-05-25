package com.inugamine.daycore.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.inugamine.daycore.model.AudioPreset
import com.inugamine.daycore.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * Media3 ExoPlayer を使った音声再生サービス
 * PlaybackParameters で速度とピッチを独立制御する
 */
class DaycorePlayerService(context: Context) {

    // ExoPlayer
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    // --- State Flows ---
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L) // ms
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L) // ms
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _speed = MutableStateFlow(0.80f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(-3.0f) // semitones
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    // 時間更新用コルーチン
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null

    init {
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

        // 初期プリセット適用
        applyPreset(AudioPreset.DAYCORE)
    }

    // --- Playback Controls ---

    fun loadTrack(track: Track) {
        player.stop()
        player.setMediaItem(MediaItem.fromUri(track.uri))
        player.prepare()
        _currentPosition.value = 0L
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun togglePlayPause() {
        if (player.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun stop() {
        player.stop()
        _currentPosition.value = 0L
        _isPlaying.value = false
    }

    // --- Speed & Pitch ---

    fun setSpeed(speed: Float) {
        _speed.value = speed
        updatePlaybackParams()
    }

    fun setPitch(semitones: Float) {
        _pitch.value = semitones
        updatePlaybackParams()
    }

    fun applyPreset(preset: AudioPreset) {
        _speed.value = preset.rate
        _pitch.value = preset.semitones
        updatePlaybackParams()
    }

    private fun updatePlaybackParams() {
        val pitchFactor = 2f.pow(_pitch.value / 12f)
        player.playbackParameters = PlaybackParameters(_speed.value, pitchFactor)
    }

    // --- Position Updates ---

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                delay(50) // ~20fps
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
    }

    // --- Cleanup ---

    fun release() {
        scope.cancel()
        player.release()
    }
}
