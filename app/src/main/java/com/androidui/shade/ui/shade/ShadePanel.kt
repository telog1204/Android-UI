@file:OptIn(ExperimentalMaterial3Api::class)

package com.androidui.shade.ui.shade

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.androidui.shade.media.MediaSessionsController
import com.androidui.shade.notification.NotificationRepository
import com.androidui.shade.overlay.ShadeState
import com.androidui.shade.qs.QuickSettingsController
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Android 15風の通知パネル全体 */
@Composable
fun ShadePanel(
    state: ShadeState,
    actions: ShadePanelActions,
    quickSettings: QuickSettingsController,
    media: MediaSessionsController,
    topInset: Dp,
    bottomInset: Dp,
) {
    val items by NotificationRepository.items.collectAsState()
    val connected by NotificationRepository.connected.collectAsState()
    val tiles by quickSettings.tiles.collectAsState()
    val brightness by quickSettings.brightness.collectAsState()
    val mediaItems by media.items.collectAsState()
    // 再生カードを出しているアプリの再生用の通知は、二重表示を防ぐため一覧から外す
    val visibleItems = remember(items, mediaItems) {
        val mediaPackages = mediaItems.mapTo(HashSet()) { it.packageName }
        items.filterNot { it.isMedia && it.packageName in mediaPackages }
    }
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val scrimColor = scheme.surfaceContainer
    val cardColor = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    val nestedScroll = remember(state) { ShadeNestedScrollConnection(state) }
    val router = remember(state) { PanelDragRouter(state) }
    val dragState = rememberDraggableState { delta -> router.onDelta(delta) }
    val showFullQs by remember(state) { derivedStateOf { state.qsExpansion > 0f } }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(scrimColor, alpha = 0.85f * state.expansion) }
            .nestedScroll(nestedScroll)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { position -> router.onStart(position.y) },
                onDragStopped = { velocity -> router.onStop(velocity) },
            )
            .pointerInput(state) { detectTapGestures(onTap = { state.close() }) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 指の動きに合わせて上から下りてくる
                    translationY = -(1f - state.expansion) * state.openDistancePx
                    alpha = state.expansion
                }
                .padding(top = topInset, bottom = bottomInset),
        ) {
            ShadeHeader()
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // 1段目：4つのタイルと通知
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - state.qsExpansion
                            translationY = state.qsExpansion * state.qsDistancePx * 0.5f
                        },
                ) {
                    QuickQuickSettings(
                        tiles = tiles,
                        controller = quickSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { router.qsRegionBottomPx = it.boundsInRoot().bottom },
                    )
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (mediaItems.isNotEmpty()) {
                            item(key = "media") {
                                MediaCarousel(
                                    items = mediaItems,
                                    controller = media,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                        }
                        when {
                            !connected -> item(key = "not_connected") {
                                Message("通知へのアクセスが許可されていません。\nアプリを開いて設定してください。")
                            }

                            visibleItems.isEmpty() && mediaItems.isEmpty() -> item(key = "empty") {
                                Message("通知はありません")
                            }
                        }
                        itemsIndexed(visibleItems, key = { _, n -> n.key }) { index, notification ->
                            NotificationCard(
                                notification = notification,
                                shape = stackShape(index, visibleItems.size),
                                color = cardColor,
                                actions = actions,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (connected) {
                            item(key = "footer") {
                                ShadeFooter(
                                    showClearAll = visibleItems.any { it.clearable },
                                    color = cardColor,
                                    actions = actions,
                                )
                            }
                        }
                    }
                }
                // 2段目：全画面のクイック設定
                if (showFullQs) {
                    FullQuickSettings(
                        tiles = tiles,
                        brightness = brightness,
                        controller = quickSettings,
                        mediaItems = mediaItems,
                        mediaController = media,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = state.qsExpansion
                                translationY = -(1f - state.qsExpansion) * state.qsDistancePx * 0.5f
                            }
                            // 下にある通知に触れないようにする
                            .pointerInput(Unit) {},
                    )
                }
            }
            DragHandle()
        }
    }
}

/** 時計と日付 */
@Composable
private fun ShadeHeader() {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    val time = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now) {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMdEEE")
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
    }
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp)) {
        Text(time, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(date, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 「管理」「すべて消去」ボタン */
@Composable
private fun ShadeFooter(showClearAll: Boolean, color: Color, actions: ShadePanelActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton("管理", color, actions.onManage)
        Spacer(Modifier.weight(1f))
        if (showClearAll) PillButton("すべて消去", color, actions.onClearAll)
    }
}

@Composable
private fun PillButton(label: String, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = color) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 下端のつまみ（上にドラッグして閉じる） */
@Composable
private fun DragHandle() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        )
    }
}

/** 通知を重ねたときの角の丸み（両端は大きく、間は小さく） */
private fun stackShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): RoundedCornerShape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * パネル上のドラッグを「パネル全体の開閉」と「クイック設定の展開」に振り分ける。
 * 全開のときに、時計とタイルの部分を下へ引くとクイック設定を広げる。
 */
private class PanelDragRouter(private val state: ShadeState) {
    private enum class Mode { UNDECIDED, PANEL, QS }

    /** 時計と4つのタイルの部分の下端（この位置より上で下へ引くとクイック設定を広げる） */
    var qsRegionBottomPx = 0f

    private var startY = 0f
    private var mode = Mode.UNDECIDED

    fun onStart(y: Float) {
        startY = y
        mode = Mode.UNDECIDED
    }

    fun onDelta(delta: Float) {
        if (mode == Mode.UNDECIDED) {
            mode = when {
                state.qsExpansion > 0f -> Mode.QS
                state.expansion >= 1f && delta > 0f && startY <= qsRegionBottomPx -> Mode.QS
                else -> Mode.PANEL
            }
        }
        if (mode == Mode.QS) state.qsDragBy(delta) else state.dragBy(delta)
    }

    fun onStop(velocity: Float) {
        when (mode) {
            Mode.QS -> state.qsSettle(velocity)
            Mode.PANEL -> state.settle(velocity)
            Mode.UNDECIDED -> Unit
        }
        mode = Mode.UNDECIDED
    }
}

/**
 * 通知一覧のスクロールと連動させる。
 * - 最後までスクロールしたあとに上へドラッグ：パネルを閉じる
 * - 一番上で下へドラッグ：クイック設定を広げる
 * - 途中まで閉じた状態で下へドラッグ：開き直す
 */
private class ShadeNestedScrollConnection(private val state: ShadeState) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (state.qsExpansion > 0f && state.qsExpansion < 1f) {
            state.qsDragBy(available.y)
            return Offset(0f, available.y)
        }
        if (available.y > 0f && state.expansion < 1f) {
            state.dragBy(available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (available.y < 0f) {
            state.dragBy(available.y)
            return Offset(0f, available.y)
        }
        if (available.y > 0f && state.expansion >= 1f) {
            state.qsDragBy(available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (state.qsExpansion > 0f && state.qsExpansion < 1f) {
            state.qsSettle(available.y)
            return available
        }
        if (state.expansion < 1f) {
            state.settle(available.y)
            return available
        }
        return Velocity.Zero
    }
}
