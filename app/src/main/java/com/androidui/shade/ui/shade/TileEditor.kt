package com.androidui.shade.ui.shade

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.androidui.shade.qs.QuickSettingsController
import com.androidui.shade.qs.TileId
import com.androidui.shade.qs.TileState
import kotlin.math.roundToInt

private val EditTileHeight = 64.dp

/** 1段目（通知の上）の4つと、それ以外の間に入れる区切りの高さ */
private val DividerHeight = 32.dp

/** 1段目に表示されるタイルの数 */
private const val QUICK_TILE_COUNT = 4

/**
 * クイック設定のタイルの編集画面。
 * 表示中のタイルは長押ししてドラッグで並べ替え、「−」で非表示にする。
 * 追加できるタイルは「＋」で表示中の末尾に追加する。
 */
@Composable
fun TileEditor(
    tiles: List<TileState>,
    hiddenTiles: List<TileState>,
    controller: QuickSettingsController,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val states = remember(tiles, hiddenTiles) { (tiles + hiddenTiles).associateBy { it.id } }
    // 並べ替え中の順番（保存された並びが変わったときだけ作り直す）
    val ids = remember(tiles.map { it.id }) { tiles.map { it.id }.toMutableStateList() }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "編集",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            TextButton(onClick = controller::resetOrder) { Text("リセット") }
            TextButton(onClick = onDone) { Text("完了") }
        }
        SectionLabel("表示中のタイル（長押しして並べ替え）")
        ReorderableTileGrid(
            ids = ids,
            stateOf = { states[it] },
            onOrderChanged = { controller.saveOrder(ids.toList()) },
            onRemove = { id -> controller.saveOrder(ids.filter { it != id }) },
        )
        Spacer(Modifier.height(24.dp))
        SectionLabel("追加できるタイル")
        if (hiddenTiles.isEmpty()) {
            Text(
                text = "すべてのタイルを表示中です",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 16.dp),
            )
        } else {
            Column(
                Modifier.padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(TileSpacing),
            ) {
                hiddenTiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                        row.forEach { tile ->
                            val add = { controller.saveOrder(ids.toList() + tile.id) }
                            EditTile(
                                tile = tile,
                                adding = true,
                                onBadgeClick = add,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(EditTileHeight)
                                    .clickable(onClick = add),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
    )
}

/** 並べ替えの計算に使う、マス目の大きさ */
private class GridGeometry(
    val cellWidth: Float,
    val cellHeight: Float,
    val spacing: Float,
    val divider: Float,
) {
    fun topLeft(index: Int): Offset {
        val row = index / 2
        val col = index % 2
        val y = row * (cellHeight + spacing) + if (index >= QUICK_TILE_COUNT) divider else 0f
        return Offset(col * (cellWidth + spacing), y)
    }

    /** 指の位置（マスの中心）に一番近いマスの番号 */
    fun indexAt(point: Offset, count: Int): Int {
        val quickBottom = (QUICK_TILE_COUNT / 2) * (cellHeight + spacing)
        val y = if (point.y >= quickBottom + divider / 2) point.y - divider else point.y
        val row = (y / (cellHeight + spacing)).toInt().coerceAtLeast(0)
        val col = if (point.x < cellWidth + spacing / 2) 0 else 1
        return (row * 2 + col).coerceIn(0, (count - 1).coerceAtLeast(0))
    }
}

/** 長押しドラッグで並べ替えられるタイルのマス目 */
@Composable
private fun ReorderableTileGrid(
    ids: SnapshotStateList<TileId>,
    stateOf: (TileId) -> TileState?,
    onOrderChanged: () -> Unit,
    onRemove: (TileId) -> Unit,
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val spacingPx = with(density) { TileSpacing.toPx() }
    val cellHeightPx = with(density) { EditTileHeight.toPx() }
    val dividerPx = with(density) { DividerHeight.toPx() }
    val cellWidthPx = ((widthPx - spacingPx) / 2f).coerceAtLeast(0f)
    val geometry by rememberUpdatedState(GridGeometry(cellWidthPx, cellHeightPx, spacingPx, dividerPx))
    val currentOnOrderChanged by rememberUpdatedState(onOrderChanged)

    var draggingId by remember { mutableStateOf<TileId?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val rows = (ids.size + 1) / 2
    val totalHeightPx = if (rows == 0) {
        0f
    } else {
        rows * cellHeightPx + (rows - 1) * spacingPx + if (ids.size > QUICK_TILE_COUNT) dividerPx else 0f
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { totalHeightPx.toDp() })
            .onSizeChanged { widthPx = it.width },
    ) {
        if (ids.size > QUICK_TILE_COUNT) {
            // 先頭の4つが通知の上に表示されることを示す区切り
            val dividerTop = (QUICK_TILE_COUNT / 2) * (cellHeightPx + spacingPx) - spacingPx
            Column(
                Modifier
                    .offset { IntOffset(0, dividerTop.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { (dividerPx + spacingPx).toDp() }),
                verticalArrangement = Arrangement.Center,
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "↑ 通知の上に表示されるタイル",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                )
            }
        }

        ids.forEachIndexed { index, id ->
            val tile = stateOf(id)
            if (tile != null) key(id) {
                val dragging = draggingId == id
                val target = geometry.topLeft(index)
                val animated by animateOffsetAsState(target, label = "tilePosition")
                EditTile(
                    tile = tile,
                    adding = false,
                    onBadgeClick = { onRemove(id) },
                    modifier = Modifier
                        .offset {
                            val position = if (dragging) geometry.topLeft(ids.indexOf(id)) + dragOffset else animated
                            IntOffset(position.x.roundToInt(), position.y.roundToInt())
                        }
                        .zIndex(if (dragging) 1f else 0f)
                        .size(
                            width = with(density) { cellWidthPx.toDp() },
                            height = EditTileHeight,
                        )
                        .then(if (dragging) Modifier.shadow(8.dp, RoundedCornerShape(20.dp)) else Modifier)
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = id
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    val g = geometry
                                    val current = ids.indexOf(id)
                                    val center = g.topLeft(current.coerceAtLeast(0)) + dragOffset +
                                        Offset(g.cellWidth / 2f, g.cellHeight / 2f)
                                    val next = g.indexAt(center, ids.size)
                                    if (current >= 0 && next != current) {
                                        val before = g.topLeft(current)
                                        ids.removeAt(current)
                                        ids.add(next, id)
                                        // 入れ替わってもタイルが指の下に残るように補正する
                                        dragOffset += before - g.topLeft(next)
                                    }
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = Offset.Zero
                                    currentOnOrderChanged()
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = Offset.Zero
                                    currentOnOrderChanged()
                                },
                            )
                        },
                )
            }
        }
    }
}

/** 編集画面のタイル（角に「−」または「＋」のボタン） */
@Composable
private fun EditTile(
    tile: TileState,
    adding: Boolean,
    onBadgeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerHighest),
    ) {
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp, end = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(tileIcon(tile.id), contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (adding) scheme.primary else scheme.surfaceContainerLow)
                .clickable(onClick = onBadgeClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (adding) Icons.Rounded.Add else Icons.Rounded.Remove,
                contentDescription = if (adding) "${tile.label}を追加" else "${tile.label}を非表示",
                tint = if (adding) scheme.onPrimary else scheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
