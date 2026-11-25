@file:OptIn(ExperimentalFoundationApi::class)

package com.androidui.shade.ui.shade

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidui.shade.media.MediaItem
import com.androidui.shade.media.MediaSessionsController
import com.androidui.shade.qs.QuickSettingsController
import com.androidui.shade.qs.TileId
import com.androidui.shade.qs.TileState

private val TileHeight = 72.dp
internal val TileSpacing = 8.dp
private const val TILES_PER_PAGE = 8
private const val ROWS_PER_PAGE = TILES_PER_PAGE / 2

/** タイルの欄の高さ（どのページでも4段分で固定し、下のボタンの位置を変えない） */
private val PageHeight = TileHeight * ROWS_PER_PAGE + TileSpacing * (ROWS_PER_PAGE - 1)

/** 1回目のスワイプで表示する、通知の上の4つのタイル */
@Composable
fun QuickQuickSettings(
    tiles: List<TileState>,
    controller: QuickSettingsController,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(TileSpacing),
    ) {
        tiles.take(4).chunked(2).forEach { row -> TileRow(row, controller) }
    }
}

/** 2回目のスワイプで表示する、全画面のクイック設定 */
@Composable
fun FullQuickSettings(
    tiles: List<TileState>,
    brightness: Float,
    controller: QuickSettingsController,
    mediaItems: List<MediaItem>,
    mediaController: MediaSessionsController,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = tiles.chunked(TILES_PER_PAGE)
    val pagerState = rememberPagerState { pages.size.coerceAtLeast(1) }

    Column(modifier.padding(horizontal = 12.dp)) {
        BrightnessSlider(
            value = brightness,
            onChange = controller::onBrightnessChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(PageHeight),
            pageSpacing = 16.dp,
            verticalAlignment = Alignment.Top,
        ) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
                pages.getOrNull(page).orEmpty().chunked(2).forEach { row -> TileRow(row, controller) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageIndicator(count = pages.size, current = pagerState.currentPage)
            Spacer(Modifier.weight(1f))
            FooterButton(Icons.Rounded.Edit, "編集", onEdit)
            Spacer(Modifier.width(8.dp))
            FooterButton(Icons.Rounded.Settings, "設定", controller::openSettings)
            Spacer(Modifier.width(8.dp))
            FooterButton(Icons.Rounded.PowerSettingsNew, "電源メニュー", controller::openPowerMenu)
        }
        // 再生カードはボタンの下に置き、ボタンの位置が変わらないようにする
        MediaCarousel(
            items = mediaItems,
            controller = mediaController,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun TileRow(row: List<TileState>, controller: QuickSettingsController) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
        row.forEach { tile ->
            LargeTile(
                tile = tile,
                onClick = { controller.onTileClick(tile.id) },
                onLongClick = { controller.onTileLongClick(tile.id) },
                modifier = Modifier.weight(1f),
            )
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** アイコンと名前、状態を表示する横長のタイル */
@Composable
private fun LargeTile(
    tile: TileState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (tile.active) scheme.primary else scheme.surfaceContainerHighest,
        label = "tileBackground",
    )
    val content by animateColorAsState(
        if (tile.active) scheme.onPrimary else scheme.onSurface,
        label = "tileContent",
    )
    Row(
        modifier
            .height(TileHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tileIcon(tile.id), contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = tile.label,
                style = MaterialTheme.typography.titleSmall,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            tile.secondary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (opensPanel(tile.id)) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 太い明るさスライダー（つまみ部分に明るさアイコン） */
@Composable
private fun BrightnessSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val height = 48.dp
    var widthPx by remember { mutableIntStateOf(1) }
    val currentOnChange by rememberUpdatedState(onChange)

    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHighest)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentOnChange((offset.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    currentOnChange((change.position.x / widthPx).coerceIn(0f, 1f))
                }
            },
    ) {
        val heightPx = with(density) { height.toPx() }
        val fillPx = (value.coerceIn(0f, 1f) * widthPx).coerceAtLeast(heightPx)
        Box(
            Modifier
                .fillMaxHeight()
                .width(with(density) { fillPx.toDp() })
                .clip(RoundedCornerShape(50))
                .background(scheme.primary),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                Icons.Rounded.LightMode,
                contentDescription = "明るさ",
                tint = scheme.onPrimary,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(24.dp),
            )
        }
    }
}

@Composable
internal fun PageIndicator(count: Int, current: Int) {
    if (count <= 1) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            val color = if (index == current) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            }
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun FooterButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
    }
}

internal fun tileIcon(id: TileId): ImageVector = when (id) {
    TileId.INTERNET -> Icons.Rounded.Wifi
    TileId.BLUETOOTH -> Icons.Rounded.Bluetooth
    TileId.FLASHLIGHT -> Icons.Rounded.FlashlightOn
    TileId.DND -> Icons.Rounded.DoNotDisturbOn
    TileId.ROTATION -> Icons.Rounded.ScreenRotation
    TileId.AIRPLANE -> Icons.Rounded.AirplanemodeActive
    TileId.LOCATION -> Icons.Rounded.LocationOn
    TileId.DARK_MODE -> Icons.Rounded.DarkMode
    TileId.VIBRATE -> Icons.Rounded.Vibration
    TileId.NFC -> Icons.Rounded.Nfc
    TileId.SCREENSHOT -> Icons.Rounded.Screenshot
    TileId.LOCK -> Icons.Rounded.Lock
}

/** タップで設定パネルを開くタイル（右端に「>」を表示する） */
private fun opensPanel(id: TileId): Boolean = when (id) {
    TileId.INTERNET, TileId.BLUETOOTH, TileId.AIRPLANE, TileId.LOCATION, TileId.DARK_MODE, TileId.NFC -> true
    else -> false
}
