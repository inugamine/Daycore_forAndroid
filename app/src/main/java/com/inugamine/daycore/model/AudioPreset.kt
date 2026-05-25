package com.inugamine.daycore.model

import kotlin.math.pow

/**
 * Daycore / Nightcore などのプリセット定義
 */
data class AudioPreset(
    val id: String,
    val name: String,
    val icon: String, // Material icon name
    val rate: Float,       // 再生速度 (1.0 = 通常)
    val semitones: Float   // ピッチ (semitones, 0.0 = 通常)
) {
    /** semitones を倍率に変換（ExoPlayer の PlaybackParameters 用） */
    val pitchFactor: Float
        get() = 2f.pow(semitones / 12f)

    companion object {
        val ORIGINAL = AudioPreset("original", "Original", "waveform", 1.0f, 0.0f)
        val DAYCORE_SOFT = AudioPreset("daycore_soft", "Daycore Soft", "sun_dim", 0.85f, -2.0f)
        val DAYCORE = AudioPreset("daycore", "Daycore", "sun", 0.80f, -3.0f)
        val DAYCORE_DEEP = AudioPreset("daycore_deep", "Daycore Deep", "sun_bright", 0.70f, -5.0f)
        val NIGHTCORE = AudioPreset("nightcore", "Nightcore", "moon", 1.25f, 3.0f)

        val ALL = listOf(ORIGINAL, DAYCORE_SOFT, DAYCORE, DAYCORE_DEEP, NIGHTCORE)
    }
}
