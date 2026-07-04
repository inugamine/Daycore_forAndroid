package com.inugamine.daycore.util

import android.content.Context
import android.net.Uri
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options

/**
 * ライブラリ曲のアートワーク読み込み用モデル。
 * 曲ファイル自体の埋め込みアートワークだけを信頼する（詳細は ArtworkResolver 参照）。
 * albumart や loadThumbnail へのフォールバックは擬似アルバム問題の温床になるため持たない。
 */
data class AudioArtwork(val trackUri: Uri)

/** Coil のメモリキャッシュキー。曲 URI 単位でキャッシュする */
class AudioArtworkKeyer : Keyer<AudioArtwork> {
    override fun key(data: AudioArtwork, options: Options): String =
        "audio-art:${data.trackUri}"
}

/**
 * AudioArtwork を Bitmap に解決する Coil カスタム Fetcher。
 * 埋め込みアートワークが無い曲は null を返し、UI 側の error プレースホルダーに任せる。
 */
class AudioArtworkFetcher(
    private val data: AudioArtwork,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bitmap = ArtworkResolver.loadEmbeddedArtwork(context, data.trackUri, maxSize = 512)
            ?: return null // 埋め込みアート無し → error プレースホルダーへ
        return DrawableResult(
            drawable = bitmap.toDrawable(context.resources),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    class Factory(context: Context) : Fetcher.Factory<AudioArtwork> {
        private val appContext = context.applicationContext

        override fun create(
            data: AudioArtwork,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = AudioArtworkFetcher(data, appContext)
    }
}
