package com.inugamine.daycore.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import java.io.ByteArrayOutputStream

/**
 * 曲ファイル自体に埋め込まれたアートワークを取り出すユーティリティ。
 *
 * MediaStore の albumart URI や loadThumbnail は「アルバム」単位のフォールバックを
 * 内包しているため、アルバムタグの無いファイル群（同一フォルダ = 擬似アルバム）では
 * 無関係な曲のジャケットが返ることがある。
 * ここでは MediaMetadataRetriever.embeddedPicture のみを信頼する。
 * = そのファイルに絵が埋まっていなければ、潔く null（プレースホルダー行き）。
 */
object ArtworkResolver {

    /** 通知/ロック画面用 JPEG バイト列のキャッシュ（uri@size → bytes） */
    private val jpegCache = LruCache<String, ByteArray>(32)

    /** 埋め込みアートワークを Bitmap として取得（無ければ null） */
    fun loadEmbeddedArtwork(context: Context, uri: Uri, maxSize: Int = 512): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            decodeSampled(bytes, maxSize)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** 埋め込みアートワークを圧縮 JPEG バイト列として取得（通知/ロック画面用） */
    fun loadEmbeddedArtworkJpeg(
        context: Context,
        uri: Uri,
        maxSize: Int = 256,
        quality: Int = 75
    ): ByteArray? {
        val key = "$uri@$maxSize"
        jpegCache.get(key)?.let { return it }

        val bitmap = loadEmbeddedArtwork(context, uri, maxSize) ?: return null
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        bitmap.recycle()
        return stream.toByteArray().also { jpegCache.put(key, it) }
    }

    /** maxSize に収まるよう inSampleSize を計算して省メモリでデコード */
    private fun decodeSampled(bytes: ByteArray, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxSize &&
            bounds.outHeight / (sampleSize * 2) >= maxSize
        ) {
            sampleSize *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
