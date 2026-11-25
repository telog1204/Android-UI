package com.androidui.shade.overlay

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * パネルの開き具合を管理する。
 * - expansion：パネル全体（0＝閉じている、1＝全開）
 * - qsExpansion：クイック設定の広がり（0＝通知の画面、1＝クイック設定を全画面表示）
 * 指の移動量に合わせて動かし、指を離したら開くか閉じるかに落ち着かせる。
 */
class ShadeState(
    private val scope: CoroutineScope,
    private val flingThresholdPx: Float,
    private val onClosed: () -> Unit,
) {
    var expansion by mutableFloatStateOf(0f)
        private set

    var qsExpansion by mutableFloatStateOf(0f)
        private set

    /** クイック設定のタイルを編集中か */
    var editingTiles by mutableStateOf(false)

    /** パネルが全開になるまでに指を動かす距離 */
    var openDistancePx by mutableFloatStateOf(1f)

    /** クイック設定が全画面になるまでに指を動かす距離 */
    var qsDistancePx by mutableFloatStateOf(1f)

    private var animationJob: Job? = null
    private var qsAnimationJob: Job? = null
    private var dragStartExpansion = 0f

    // ---- パネル全体 ----

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
        animateTo(targetFor(expansion, velocityPxPerSec), velocityPxPerSec / openDistancePx)
    }

    fun open() = animateTo(1f, 0f)

    fun close() = animateTo(0f, 0f)

    fun snapClosed() {
        animationJob?.cancel()
        qsAnimationJob?.cancel()
        expansion = 0f
        qsExpansion = 0f
        editingTiles = false
    }

    /** 戻る操作：編集中なら編集を終え、クイック設定が広がっていれば戻し、そうでなければパネルを閉じる */
    fun handleBack() {
        when {
            editingTiles -> editingTiles = false
            qsExpansion > 0f -> collapseQs()
            else -> close()
        }
    }

    // ---- クイック設定 ----

    fun qsDragBy(deltaPx: Float) {
        qsAnimationJob?.cancel()
        qsExpansion = (qsExpansion + deltaPx / qsDistancePx).coerceIn(0f, 1f)
    }

    fun qsSettle(velocityPxPerSec: Float) {
        animateQsTo(targetFor(qsExpansion, velocityPxPerSec), velocityPxPerSec / qsDistancePx)
    }

    fun expandQs() = animateQsTo(1f, 0f)

    fun collapseQs() = animateQsTo(0f, 0f)

    // ---- 共通 ----

    private fun targetFor(current: Float, velocity: Float): Float = when {
        velocity > flingThresholdPx -> 1f
        velocity < -flingThresholdPx -> 0f
        current >= 0.5f -> 1f
        else -> 0f
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
            if (target == 0f) {
                qsAnimationJob?.cancel()
                qsExpansion = 0f
                editingTiles = false
                onClosed()
            }
        }
    }

    private fun animateQsTo(target: Float, velocity: Float) {
        qsAnimationJob?.cancel()
        qsAnimationJob = scope.launch {
            animate(
                initialValue = qsExpansion,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
            ) { value, _ -> qsExpansion = value.coerceIn(0f, 1f) }
        }
    }
}
