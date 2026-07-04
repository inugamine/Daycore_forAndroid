package com.inugamine.daycore.model

import android.net.Uri
import com.inugamine.daycore.util.AudioArtwork

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

    /**
     * UI (Coil) 用のアートワークモデル。
     * LIBRARY 曲は擬似アルバム問題を避けるため曲ファイル自体のサムネイルを優先し、
     * FILE 曲はインポート時に抽出したキャッシュ画像の Uri をそのまま使う。
     */
    val artworkModel: Any?
        get() = when (source) {
            TrackSource.LIBRARY -> AudioArtwork(trackUri = uri)
            TrackSource.FILE -> artworkUri
        }

    val durationFormatted: String
        get() {
            val totalSec = duration / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }
}
