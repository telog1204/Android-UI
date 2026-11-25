package com.androidui.shade.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.androidui.shade.home.AppEntry
import com.androidui.shade.home.DefaultLayout

/**
 * アプリ一覧（Pixel風）。上部に検索欄、その下に名前順のアプリを並べる。
 * 一覧の一番上で下へスワイプすると閉じる。
 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    drawer: DrawerState,
    onLaunch: (AppEntry) -> Unit,
    onWebSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val filtered = remember(apps, query) {
        val text = query.trim()
        if (text.isEmpty()) apps else apps.filter { it.label.contains(text, ignoreCase = true) }
    }
    val nestedScroll = remember(drawer) { DrawerNestedScrollConnection(drawer) }
    val dragState = rememberDraggableState { delta -> drawer.dragBy(-delta) }

    Column(
        modifier
            .background(scheme.surfaceContainer)
            .nestedScroll(nestedScroll)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> drawer.settle(-velocity) },
            )
            .systemBarsPadding()
            .imePadding(),
    ) {
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            onSearch = {
                val first = filtered.firstOrNull()
                if (first != null) onLaunch(first) else if (query.isNotBlank()) onWebSearch(query.trim())
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (filtered.isEmpty() && query.isNotBlank()) {
            Text(
                text = "「${query.trim()}」に一致するアプリはありません。キーボードの検索ボタンでWebを検索します。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(DefaultLayout.COLUMNS),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            items(filtered, key = { it.key }) { app ->
                AppIconItem(
                    app = app,
                    labelColor = scheme.onSurface,
                    onWallpaper = false,
                    onClick = { onLaunch(app) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHighest)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = "アプリを検索",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

/**
 * アプリ一覧のスクロールと開閉を連動させる。
 * 一番上で下へドラッグすると閉じ、途中まで閉じた状態で上へドラッグすると開き直す。
 */
private class DrawerNestedScrollConnection(private val drawer: DrawerState) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y < 0f && drawer.progress < 1f) {
            drawer.dragBy(-available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f) {
            drawer.dragBy(-available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drawer.progress < 1f) {
            drawer.settle(-available.y)
            return available
        }
        return Velocity.Zero
    }
}
