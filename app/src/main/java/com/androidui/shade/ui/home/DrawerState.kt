package com.androidui.shade.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** アプリ一覧の開き具合（0＝閉じている、1＝全開）。指の動きに合わせて開閉する */
class DrawerState(
    private val scope: CoroutineScope,
    private val flingThresholdPx: Float,
) {
    var progress by mutableFloatStateOf(0f)
        private set

    /** 全開になるまでに指を動かす距離 */
    var distancePx = 1f

    private var job: Job? = null

    /** 上方向を正として、指の移動量だけ開く */
    fun dragBy(upDeltaPx: Float) {
        job?.cancel()
        progress = (progress + upDeltaPx / distancePx).coerceIn(0f, 1f)
    }

    /** 指を離したときの速度（上方向が正）から、開くか閉じるかを決める */
    fun settle(upVelocityPxPerSec: Float) {
        val target = when {
            upVelocityPxPerSec > flingThresholdPx -> 1f
            upVelocityPxPerSec < -flingThresholdPx -> 0f
            progress >= 0.5f -> 1f
            else -> 0f
        }
        animateTo(target, upVelocityPxPerSec / distancePx)
    }

    fun close() = animateTo(0f, 0f)

    fun snapClosed() {
        job?.cancel()
        progress = 0f
    }

    private fun animateTo(target: Float, velocity: Float) {
        job?.cancel()
        job = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
            ) { value, _ -> progress = value.coerceIn(0f, 1f) }
        }
    }
}
