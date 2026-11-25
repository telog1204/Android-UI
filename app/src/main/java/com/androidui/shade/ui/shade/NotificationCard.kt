@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.androidui.shade.ui.shade

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidui.shade.notification.ShadeNotification
import kotlin.math.abs
import kotlin.math.sign

/** 通知1件分のカード。タップで開き、左右にスワイプで消去する */
@Composable
fun NotificationCard(
    notification: ShadeNotification,
    shape: Shape,
    color: Color,
    actions: ShadePanelActions,
    modifier: Modifier = Modifier,
    /** 折りたたんだ状態でもボタンを表示する（ポップアップ通知用） */
    alwaysShowActions: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember(notification.key) { mutableStateOf(false) }
    // 長押しで表示する「このアプリの消せない通知を隠す」の確認
    var showHideMenu by remember(notification.key) { mutableStateOf(false) }
    var offsetX by remember(notification.key) { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(1) }
    val flingThresholdPx = with(LocalDensity.current) { 1000.dp.toPx() }
    val dragState = rememberDraggableState { delta -> offsetX += delta }
    val expandable = notification.actions.isNotEmpty() ||
        notification.bigText.length > notification.text.length ||
        notification.text.length > 40

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .graphicsLayer {
                translationX = offsetX
                alpha = 1f - (abs(offsetX) / widthPx).coerceIn(0f, 1f)
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = notification.clearable,
                onDragStopped = { velocity ->
                    val fling = abs(velocity) > flingThresholdPx
                    val dismiss = fling || abs(offsetX) > widthPx * 0.4f
                    val target = if (dismiss) {
                        val direction = if (fling) sign(velocity) else sign(offsetX)
                        direction * widthPx
                    } else {
                        0f
                    }
                    animate(offsetX, target, velocity) { value, _ -> offsetX = value }
                    if (dismiss) actions.onDismiss(notification)
                },
            ),
        shape = shape,
        color = color,
    ) {
        if (showHideMenu) {
            HideOngoingMenu(
                notification = notification,
                onHide = {
                    showHideMenu = false
                    actions.onHideApp(notification)
                },
                onCancel = { showHideMenu = false },
            )
        } else Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { actions.onOpen(notification) },
                    // 消せない通知だけ、長押しで「隠す」メニューを出す
                    onLongClick = if (notification.clearable) null else ({ showHideMenu = true }),
                )
                .animateContentSize()
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AppIcon(notification)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val headline = if (expanded) notification.appName else notification.title.ifEmpty { notification.appName }
                    Text(
                        text = headline,
                        modifier = Modifier.weight(1f, fill = false),
                        style = if (expanded) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = " • " + relativeTime(notification.postTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    if (expandable) ExpandButton(expanded) { expanded = !expanded }
                }
                if (expanded && notification.title.isNotEmpty()) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val body = if (expanded) notification.bigText else notification.text
                if (body.isNotEmpty()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = if (expanded) 12 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if ((expanded || alwaysShowActions) && notification.actions.isNotEmpty()) {
                    Row(
                        Modifier
                            .padding(top = 10.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        notification.actions.forEach { action ->
                            Surface(
                                onClick = { actions.onAction(notification, action) },
                                shape = RoundedCornerShape(50),
                                color = scheme.secondaryContainer,
                            ) {
                                Text(
                                    text = action.title,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.onSecondaryContainer,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            val largeIcon = notification.largeIcon
            if (!expanded && largeIcon != null) {
                Spacer(Modifier.width(12.dp))
                Image(
                    bitmap = largeIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** 長押しで表示する、消せない通知を隠すかの確認（Android 15の長押しメニューと同じくカードの中に表示） */
@Composable
private fun HideOngoingMenu(
    notification: ShadeNotification,
    onHide: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Text(
            text = "${notification.appName}の消せない通知を隠す",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
        )
        Text(
            text = "このアプリの消せない通知を、パネルに表示しないようにします。" +
                "消せる通知は今までどおり表示します。設定画面からいつでも戻せます。",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, end = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text("キャンセル") }
            TextButton(onClick = onHide) { Text("隠す") }
        }
    }
}

/** 左側の丸いアプリアイコン */
@Composable
private fun AppIcon(notification: ShadeNotification) {
    val background = if (notification.accentColor != 0) {
        Color(notification.accentColor).copy(alpha = 1f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val tint = if (background.luminance() > 0.5f) Color.Black else Color.White
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        notification.smallIcon?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(tint),
            )
        }
    }
}

/** 展開・折りたたみボタン */
@Composable
private fun ExpandButton(expanded: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(start = 8.dp)
            .size(28.dp),
        shape = CircleShape,
        color = scheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val lineColor = scheme.onSurfaceVariant
            Canvas(
                Modifier
                    .size(12.dp)
                    .graphicsLayer { rotationZ = rotation },
            ) {
                val path = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.35f)
                    lineTo(size.width * 0.5f, size.height * 0.7f)
                    lineTo(size.width * 0.85f, size.height * 0.35f)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private fun relativeTime(time: Long): String {
    val now = System.currentTimeMillis()
    if (now - time < DateUtils.MINUTE_IN_MILLIS) return "今"
    return DateUtils.getRelativeTimeSpanString(
        time,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}
