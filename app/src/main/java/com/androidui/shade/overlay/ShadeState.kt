package com.androidui.shade.overlay

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * パネルの開き具合（0＝閉じている、1＝全開）を管理する。
 * 指の移動量に合わせて開閉し、指を離したら開くか閉じるかに落ち着かせる。
 */
class ShadeState(
    private val scope: CoroutineScope,
    private val flingThresholdPx: Float,
    private val onClosed: () -> Unit,
) {
    var expansion by mutableFloatStateOf(0f)
        private set

    /** 全開になるまでに指を動かす距離 */
    var openDistancePx by mutableFloatStateOf(1f)

    private var animationJob: Job? = null
    private var dragStartExpansion = 0f

    fun startDrag() {
        animationJob?.cancel()
        dragStartExpansion = expansion
    }

    /** ドラッグ開始位置からの移動量で開き具合を決める */
    fun dragTo(offsetPx: Float) {
        expansion = (dragStartExpansion + offsetPx / openDistancePx).coerceIn(0f, 1f)
    }

    /** 直前からの移動量で開き具合を変える */
    fun dragBy(deltaPx: Float) {
        animationJob?.cancel()
        expansion = (expansion + deltaPx / openDistancePx).coerceIn(0f, 1f)
    }

    /** 指を離したときの速度から、開くか閉じるかを決める */
    fun settle(velocityPxPerSec: Float) {
        val target = when {
            velocityPxPerSec > flingThresholdPx -> 1f
            velocityPxPerSec < -flingThresholdPx -> 0f
            expansion >= 0.5f -> 1f
            else -> 0f
        }
        animateTo(target, velocityPxPerSec / openDistancePx)
    }

    fun open() = animateTo(1f, 0f)

    fun close() = animateTo(0f, 0f)

    fun snapClosed() {
        animationJob?.cancel()
        expansion = 0f
    }

    private fun animateTo(target: Float, velocity: Float) {
        animationJob?.cancel()
        animationJob = scope.launch {
            animate(
                initialValue = expansion,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
            ) { value, _ -> expansion = value.coerceIn(0f, 1f) }
            if (target == 0f) onClosed()
        }
    }
}
