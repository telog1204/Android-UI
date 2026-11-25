package com.androidui.shade.ui.shade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.androidui.shade.overlay.HeadsUpUiState

/**
 * ポップアップ通知。上から滑り込み、上にスワイプするとポップアップだけを閉じる。
 * 左右のスワイプ（通知の消去）やタップは、通知パネルと同じカードで処理する。
 */
@Composable
fun HeadsUpPopup(
    state: HeadsUpUiState,
    actions: ShadePanelActions,
    onSwipeAway: () -> Unit,
    onHidden: () -> Unit,
) {
    val item = state.item ?: return
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val cardColor = if (isSystemInDarkTheme()) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    val shape = RoundedCornerShape(24.dp)
    val progress = remember { Animatable(0f) }
    val currentOnHidden by rememberUpdatedState(onHidden)
    var dragY by remember(item.key) { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(density) { 40.dp.toPx() }
    val flingThresholdPx = with(density) { 800.dp.toPx() }
    val dragState = rememberDraggableState { delta -> dragY = (dragY + delta).coerceAtMost(0f) }

    LaunchedEffect(state.shown) {
        progress.animateTo(if (state.shown) 1f else 0f)
        if (!state.shown) currentOnHidden()
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
            .graphicsLayer {
                translationY = (1f - progress.value) * with(density) { -96.dp.toPx() } + dragY
                alpha = progress.value
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (dragY < -swipeThresholdPx || velocity < -flingThresholdPx) {
                        onSwipeAway()
                    } else {
                        animate(dragY, 0f) { value, _ -> dragY = value }
                    }
                },
            ),
    ) {
        NotificationCard(
            notification = item,
            shape = shape,
            color = cardColor,
            actions = actions,
            modifier = Modifier.shadow(6.dp, shape),
            alwaysShowActions = true,
        )
    }
}
