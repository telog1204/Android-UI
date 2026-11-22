package com.androidui.shade.qs

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 明るさの値とスライダー位置の変換（Android標準と同じ計算式）。
 * 人の目の感じ方に合わせ、暗い側の調整幅を広くとる。
 */
object BrightnessConverter {
    private const val R = 0.5f
    private const val A = 0.17883277f
    private const val B = 0.28466892f
    private const val C = 0.55991073f

    /** 明るさの値 → スライダー位置（0〜1） */
    fun toSlider(value: Float, min: Float, max: Float): Float {
        val normalized = ((value - min) / (max - min)).coerceIn(0f, 1f) * 12f
        val ret = if (normalized <= 1f) sqrt(normalized) * R else A * ln(normalized - B) + C
        return ret.coerceIn(0f, 1f)
    }

    /** スライダー位置（0〜1） → 明るさの値 */
    fun toValue(slider: Float, min: Float, max: Float): Float {
        val s = slider.coerceIn(0f, 1f)
        val ret = if (s <= R) (s / R).pow(2) else exp((s - C) / A) + B
        val normalized = ret.coerceIn(0f, 12f)
        return min + (max - min) * normalized / 12f
    }
}
