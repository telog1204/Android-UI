package com.androidui.shade.ui.volume

import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.androidui.shade.volume.VolumeUiState
import kotlin.math.max

private val SliderWidth = 52.dp
private val SliderHeight = 280.dp

/**
 * Android 15風の音量パネル。
 * 上から順に「着信モードのボタン」「縦長の音量スライダー」「…（音量設定）ボタン」を並べる。
 */
@Composable
fun VolumePanel(
    state: VolumeUiState,
    onChange: (Float) -> Unit,
    onToggleRinger: () -> Unit,
    onOpenSettings: () -> Unit,
    onHidden: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    val currentOnHidden by rememberUpdatedState(onHidden)

    // 右から滑り込むように表示し、消えるときは右へ滑って消える
    LaunchedEffect(state.shown) {
        progress.animateTo(if (state.shown) 1f else 0f)
        if (!state.shown) currentOnHidden()
    }

    Column(
        Modifier
            .graphicsLayer {
                translationX = (1f - progress.value) * with(density) { 48.dp.toPx() }
                alpha = progress.value
            }
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainer)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoundButton(
            icon = ringerIcon(state.ringerMode),
            description = "着信モード",
            onClick = onToggleRinger,
        )
        VerticalVolumeSlider(
            fraction = state.fraction,
            muted = state.level <= state.min,
            onChange = onChange,
        )
        RoundButton(
            icon = Icons.Rounded.MoreHoriz,
            description = "音量の設定",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(SliderWidth)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** 縦長の太いスライダー（下から色が満ち、つまみ部分に音量のアイコン） */
@Composable
private fun VerticalVolumeSlider(
    fraction: Float,
    muted: Boolean,
    onChange: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(1) }
    val currentOnChange by rememberUpdatedState(onChange)
    val animatedFraction by animateFloatAsState(fraction, label = "volumeFill")

    Box(
        Modifier
            .width(SliderWidth)
            .height(SliderHeight)
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHighest)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentOnChange(1f - offset.y / heightPx) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    currentOnChange((1f - change.position.y / heightPx).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val minFillPx = with(density) { SliderWidth.toPx() }
        val fillPx = max(minFillPx, animatedFraction.coerceIn(0f, 1f) * heightPx)
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { fillPx.toDp() })
                .clip(RoundedCornerShape(50))
                .background(scheme.primary),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Icon(
                imageVector = volumeIcon(fraction, muted),
                contentDescription = "音量",
                tint = scheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 14.dp)
                    .size(24.dp),
            )
        }
    }
}

private fun volumeIcon(fraction: Float, muted: Boolean): ImageVector = when {
    muted -> Icons.AutoMirrored.Rounded.VolumeOff
    fraction < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
    else -> Icons.AutoMirrored.Rounded.VolumeUp
}

private fun ringerIcon(mode: Int): ImageVector = when (mode) {
    AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration
    AudioManager.RINGER_MODE_SILENT -> Icons.Rounded.NotificationsOff
    else -> Icons.Rounded.NotificationsActive
}
