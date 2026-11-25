@file:OptIn(ExperimentalFoundationApi::class)

package com.androidui.shade.ui.shade

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidui.shade.media.MediaCustomAction
import com.androidui.shade.media.MediaItem
import com.androidui.shade.media.MediaSessionsController
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** 再生カードの一覧（複数のアプリが再生中なら左右にスワイプで切り替え） */
@Composable
fun MediaCarousel(
    items: List<MediaItem>,
    controller: MediaSessionsController,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState { items.size }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 8.dp,
            key = { index -> items.getOrNull(index)?.key ?: index },
        ) { page ->
            items.getOrNull(page)?.let { MediaCard(it, controller) }
        }
        if (items.size > 1) {
            Spacer(Modifier.height(8.dp))
            PageIndicator(count = items.size, current = pagerState.currentPage)
        }
    }
}

/** 再生カード1枚（アルバムアートを背景に、曲名と操作ボタンを表示） */
@Composable
private fun MediaCard(item: MediaItem, controller: MediaSessionsController) {
    val scheme = MaterialTheme.colorScheme
    val hasArt = item.art != null
    val content = if (hasArt) Color.White else scheme.onSurface

    Box(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerHighest)
            .clickable { controller.open(item) },
    ) {
        item.art?.let { art ->
            Image(
                bitmap = art,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            // 文字を読みやすくするための暗い幕
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.45f)),
                        ),
                    ),
            )
        }
        Column(
            Modifier
                .matchParentSize()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.appIcon?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.artist.isNotEmpty()) {
                        Text(
                            text = item.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                PlayPauseButton(item.isPlaying) { controller.playPause(item) }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.customActions.getOrNull(0)?.let { action ->
                    CustomActionButton(action, content) { controller.sendCustomAction(item, action.action) }
                }
                MediaIconButton(Icons.Rounded.SkipPrevious, "前の曲", content, item.canSkipPrevious) {
                    controller.previous(item)
                }
                WavySeekBar(
                    item = item,
                    color = content,
                    onSeek = { position -> controller.seekTo(item, position) },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .padding(horizontal = 4.dp),
                )
                MediaIconButton(Icons.Rounded.SkipNext, "次の曲", content, item.canSkipNext) {
                    controller.next(item)
                }
                item.customActions.getOrNull(1)?.let { action ->
                    CustomActionButton(action, content) { controller.sendCustomAction(item, action.action) }
                }
            }
        }
    }
}

/** 大きな角丸の再生／一時停止ボタン */
@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateFloatAsState(if (playing) 16f else 24f, label = "playButtonCorner")
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(scheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "一時停止" else "再生",
            tint = scheme.onPrimary,
        )
    }
}

@Composable
private fun MediaIconButton(
    icon: ImageVector,
    description: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = color.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun CustomActionButton(action: MediaCustomAction, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        action.icon?.let {
            Image(
                bitmap = it,
                contentDescription = action.name,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(color),
            )
        }
    }
}

/**
 * 波打つシークバー（Android 13以降の見た目）。
 * 再生中は再生済みの部分が波打ち、一時停止中は平らになる。タップやドラッグで再生位置を変える。
 */
@Composable
private fun WavySeekBar(
    item: MediaItem,
    color: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(item.key, item.isPlaying) {
        now = SystemClock.elapsedRealtime()
        while (item.isPlaying) {
            delay(200)
            now = SystemClock.elapsedRealtime()
        }
    }

    var widthPx by remember { mutableIntStateOf(1) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = item.durationMs
    val fraction = dragFraction
        ?: if (duration > 0) item.currentPositionMs(now).toFloat() / duration else 0f
    val amplitude by animateFloatAsState(
        if (item.isPlaying && dragFraction == null) 1f else 0f,
        label = "waveAmplitude",
    )
    val phase: State<Float> = if (item.isPlaying) {
        rememberInfiniteTransition(label = "wave").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing)),
            label = "wavePhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val seekModifier = if (item.canSeek) {
        Modifier
            .pointerInput(item.key, duration) {
                detectTapGestures { offset ->
                    currentOnSeek(((offset.x / widthPx).coerceIn(0f, 1f) * duration).toLong())
                }
            }
            .pointerInput(item.key, duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / widthPx).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { currentOnSeek((it * duration).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                }
            }
    } else {
        Modifier
    }

    Canvas(
        modifier
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .then(seekModifier),
    ) {
        val centerY = size.height / 2f
        val progressX = size.width * fraction.coerceIn(0f, 1f)
        val strokeWidth = 3.dp.toPx()
        val waveHeight = 3.dp.toPx() * amplitude
        val wavelength = 20.dp.toPx()

        // 未再生の部分（平らな線）
        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(progressX, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        // 再生済みの部分（波線）
        if (progressX > 0f) {
            val shift = phase.value
            val path = Path()
            var x = 0f
            path.moveTo(0f, centerY + waveHeight * sin(-shift))
            while (x < progressX) {
                x = min(x + 2f, progressX)
                path.lineTo(x, centerY + waveHeight * sin((2 * PI * x / wavelength).toFloat() - shift))
            }
            drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
        // つまみ（縦の短い線）
        drawLine(
            color = color,
            start = Offset(progressX, centerY - 8.dp.toPx()),
            end = Offset(progressX, centerY + 8.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
