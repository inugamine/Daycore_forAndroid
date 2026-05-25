package com.inugamine.daycore.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.inugamine.daycore.model.AudioPreset
import com.inugamine.daycore.model.Track
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

/**
 * Controller class that connects to DaycoreMediaService via MediaController.
 */
class DaycorePlayerService(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // --- State Flows ---
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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, DaycoreMediaService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            setupController()
        }, MoreExecutors.directExecutor())
    }

    private fun setupController() {
        val controller = mediaController ?: return
        
        // Sync initial state if service is already running
        DaycoreMediaService.instance?.let { service ->
            _speed.value = service.speed.value
            _pitch.value = service.pitch.value
        }

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _duration.value = controller.duration.coerceAtLeast(0L)
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _isShuffled.value = shuffleModeEnabled
            }
        })
        
        _isPlaying.value = controller.isPlaying
        _duration.value = controller.duration.coerceAtLeast(0L)
        _repeatMode.value = controller.repeatMode
        _isShuffled.value = controller.shuffleModeEnabled
        if (controller.isPlaying) startPositionUpdates()
    }

    // --- Playback Controls ---

    fun loadTrack(track: Track) {
        val controller = mediaController ?: return
        controller.stop()

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.albumTitle)
            .apply {
                track.artworkUri?.let { setArtworkUri(it) }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.id)
            .setMediaMetadata(metadata)
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        _currentPosition.value = 0L
    }

    fun play() { mediaController?.play() }

    fun pause() { mediaController?.pause() }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    // --- Speed & Pitch ---

    fun setSpeed(speed: Float) {
        _speed.value = speed
        DaycoreMediaService.instance?.setSpeed(speed)
    }

    fun setPitch(semitones: Float) {
        _pitch.value = semitones
        DaycoreMediaService.instance?.setPitch(semitones)
    }

    fun applyPreset(preset: AudioPreset) {
        _speed.value = preset.rate
        _pitch.value = preset.semitones
        DaycoreMediaService.instance?.applyPreset(preset)
    }

    // --- Position Updates ---

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                delay(50)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
    }

    // --- Cleanup ---

    fun release() {
        scope.cancel()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
    }
}
