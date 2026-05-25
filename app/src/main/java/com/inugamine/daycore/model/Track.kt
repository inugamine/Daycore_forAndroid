package com.inugamine.daycore.model

import android.net.Uri

/**
 * 再生対象となるトラック情報
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumTitle: String? = null,
    val duration: Long = 0L, // milliseconds
    val uri: Uri,
    val artworkUri: Uri? = null,
    val source: TrackSource = TrackSource.FILE
) {
    enum class TrackSource { LIBRARY, FILE }

    val durationFormatted: String
        get() {
            val totalSec = duration / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }
}
