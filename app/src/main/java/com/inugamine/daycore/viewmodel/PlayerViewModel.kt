package com.inugamine.daycore.viewmodel

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.inugamine.daycore.model.AudioPreset
import com.inugamine.daycore.model.Track
import com.inugamine.daycore.service.DaycorePlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val playerService = DaycorePlayerService(application)

    init {
        // Track transition listener
        viewModelScope.launch {
            playerService.currentMediaId.collect { id ->
                if (id != null) {
                    _currentTrack.value = findTrackById(id)
                }
            }
        }
    }

    // --- UI State ---
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _selectedPreset = MutableStateFlow(AudioPreset.DAYCORE)
    val selectedPreset: StateFlow<AudioPreset> = _selectedPreset.asStateFlow()

    private val _libraryTracks = MutableStateFlow<List<Track>>(emptyList())
    val libraryTracks: StateFlow<List<Track>> = _libraryTracks.asStateFlow()

    private val _importedTracks = MutableStateFlow<List<Track>>(emptyList())
    val importedTracks: StateFlow<List<Track>> = _importedTracks.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showLibrary = MutableStateFlow(false)
    val showLibrary: StateFlow<Boolean> = _showLibrary.asStateFlow()

    // Delegate flows
    val isPlaying = playerService.isPlaying
    val currentPosition = playerService.currentPosition
    val duration = playerService.duration
    val speed = playerService.speed
    val pitch = playerService.pitch
    val repeatMode = playerService.repeatMode
    val isShuffled = playerService.isShuffled

    /** 検索フィルタ済みトラック */
    val filteredTracks: StateFlow<List<Track>> = combine(
        _libraryTracks, _importedTracks, _searchQuery
    ) { library, imported, query ->
        val all = library + imported
        if (query.isBlank()) all
        else all.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- Actions ---

    fun selectTrack(track: Track) {
        val tracks = filteredTracks.value
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerService.setPlaylist(tracks, index)
        playerService.applyPreset(_selectedPreset.value)
        playerService.play()
    }

    fun togglePlayPause() = playerService.togglePlayPause()

    fun seekTo(positionMs: Long) = playerService.seekTo(positionMs)

    fun setSpeed(speed: Float) = playerService.setSpeed(speed)

    fun setPitch(semitones: Float) = playerService.setPitch(semitones)

    fun selectPreset(preset: AudioPreset) {
        _selectedPreset.value = preset
        playerService.applyPreset(preset)
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun toggleLibrary() { _showLibrary.value = !_showLibrary.value }
    fun closeLibrary() { _showLibrary.value = false }

    fun toggleRepeatMode() = playerService.toggleRepeatMode()

    fun toggleShuffle() = playerService.toggleShuffle()

    fun skipToNext() = playerService.skipToNext()

    fun skipToPrevious() = playerService.skipToPrevious()

    // --- Helpers ---

    private fun findTrackById(id: String): Track? {
        return filteredTracks.value.find { it.id == id }
            ?: _libraryTracks.value.find { it.id == id }
            ?: _importedTracks.value.find { it.id == id }
    }

    fun loadMusicLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = mutableListOf<Track>()
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            getApplication<Application>().contentResolver.query(
                collection, projection, selection, null, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val albumId = cursor.getLong(albumIdCol)
                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    )

                    tracks.add(
                        Track(
                            id = id.toString(),
                            title = cursor.getString(titleCol) ?: "不明",
                            artist = cursor.getString(artistCol) ?: "不明",
                            albumTitle = cursor.getString(albumCol),
                            duration = cursor.getLong(durationCol),
                            uri = contentUri,
                            artworkUri = artworkUri,
                            source = Track.TrackSource.LIBRARY
                        )
                    )
                }
            }
            _libraryTracks.value = tracks
        }
    }

    /** ファイルインポート（SAF 経由の URI） */
    fun importFile(uri: Uri) {
        val context = getApplication<Application>()
        // 永続的な権限を取得
        context.contentResolver.takePersistableUriPermission(
            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        // メタデータ・埋め込みアートワークの読み出しは重いので IO スレッドで
        viewModelScope.launch(Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            var title: String? = null
            var artist: String? = null
            var durationMs: Long = 0
            var artworkBytes: ByteArray? = null

            try {
                retriever.setDataSource(context, uri)
                title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                durationMs = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
                // ファイル埋め込みのアートワーク（MP3/M4A/FLAC 等）
                artworkBytes = retriever.embeddedPicture
            } catch (_: Exception) {
            } finally {
                retriever.release()
            }

            // 埋め込み画像をキャッシュに保存して Uri 化 → Coil がそのまま読める
            val artworkUri = artworkBytes?.let { saveArtworkToCache(it, uri.toString()) }

            val track = Track(
                id = uri.toString(),
                title = title ?: uri.lastPathSegment?.substringBeforeLast('.') ?: "不明",
                artist = artist ?: "不明",
                duration = durationMs,
                uri = uri,
                artworkUri = artworkUri,
                source = Track.TrackSource.FILE
            )

            val current = _importedTracks.value.toMutableList()
            if (current.none { it.id == track.id }) {
                current.add(track)
                _importedTracks.value = current
            }
        }
    }

    /**
     * 埋め込みアートワークをキャッシュディレクトリに保存して Uri を返す。
     * ファイル名はトラック ID のハッシュなので、同じ曲の再インポートでは再利用される。
     */
    private fun saveArtworkToCache(bytes: ByteArray, key: String): Uri? {
        return try {
            val dir = java.io.File(getApplication<Application>().cacheDir, "artwork")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "${key.hashCode().toUInt()}.jpg")
            if (!file.exists()) {
                file.writeBytes(bytes)
            }
            Uri.fromFile(file)
        } catch (_: Exception) {
            null
        }
    }

    // --- Helpers ---

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    override fun onCleared() {
        super.onCleared()
        playerService.release()
    }
}
