package com.inugamine.daycore

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.inugamine.daycore.util.AudioArtworkFetcher
import com.inugamine.daycore.util.AudioArtworkKeyer

/**
 * アプリ全体の Application クラス。
 * Coil のデフォルト ImageLoader に AudioArtwork 用のカスタムコンポーネントを登録し、
 * SubcomposeAsyncImage が呼び出し側の変更なしで per-track アートワークを解決できるようにする。
 */
class DaycoreApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkKeyer())
                add(AudioArtworkFetcher.Factory(this@DaycoreApplication))
            }
            .crossfade(true)
            .build()
    }
}
