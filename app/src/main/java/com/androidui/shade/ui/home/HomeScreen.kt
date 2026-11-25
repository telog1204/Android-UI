@file:OptIn(ExperimentalFoundationApi::class)

package com.androidui.shade.ui.home

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.androidui.shade.home.AppEntry
import com.androidui.shade.home.AppRepository
import com.androidui.shade.home.DefaultLayout
import com.androidui.shade.home.HomeActions
import com.androidui.shade.ui.shade.PageIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pixel風のホーム画面。
 * 上へスワイプでアプリ一覧、下へスワイプで通知パネルを開く。
 */
@Composable
fun HomeScreen(
    repository: AppRepository,
    homePressed: SharedFlow<Unit>,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val apps by repository.apps.collectAsState()
    val layout = remember(apps) { DefaultLayout.build(context, apps) }
    val drawer = remember { DrawerState(scope, flingThresholdPx = with(density) { 600.dp.toPx() }) }
    val pagerState = rememberPagerState { layout.pages.size.coerceAtLeast(1) }
    var query by remember { mutableStateOf("") }
    val drawerClosed by remember { derivedStateOf { drawer.progress == 0f } }
    val router = remember(drawer) {
        HomeDragRouter(
            drawer = drawer,
            shadeThresholdPx = with(density) { 48.dp.toPx() },
            onOpenShade = { HomeActions.openShade(context) },
        )
    }
    val dragState = rememberDraggableState { delta -> router.onDelta(delta) }

    val launchApp: (AppEntry) -> Unit = { app ->
        HomeActions.launch(context, app)
        // アプリから戻ったときにホーム画面が見えるよう、少し待ってから一覧を閉じる
        scope.launch {
            delay(400)
            drawer.snapClosed()
        }
    }

    // ホームボタン：一覧を閉じて1ページ目に戻る
    LaunchedEffect(Unit) {
        homePressed.collect {
            drawer.close()
            pagerState.animateScrollToPage(0)
        }
    }
    // 一覧が閉じたら、検索の入力を消してキーボードを閉じる
    LaunchedEffect(drawerClosed) {
        if (drawerClosed) {
            query = ""
            focusManager.clearFocus()
        }
    }
    // 戻る操作：一覧が開いていれば閉じる（ホーム画面自体は閉じない）
    BackHandler {
        if (!drawerClosed) drawer.close()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        drawer.distancePx = heightPx * 0.5f

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - drawer.progress }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { router.onStart() },
                    onDragStopped = { velocity -> router.onStop(velocity) },
                )
                .systemBarsPadding(),
        ) {
            AtAGlance()
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                HomePage(layout.pages.getOrNull(page).orEmpty(), launchApp)
            }
            if (layout.pages.size > 1) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PageIndicator(count = layout.pages.size, current = pagerState.currentPage)
                }
            }
            Dock(layout.dock, launchApp)
            SearchBar(
                onSearch = { HomeActions.search(context) },
                onVoiceSearch = { HomeActions.voiceSearch(context) },
            )
        }

        AppDrawer(
            apps = apps,
            query = query,
            onQueryChange = { query = it },
            drawer = drawer,
            onLaunch = launchApp,
            onWebSearch = { HomeActions.webSearch(context, it) },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 閉じているときは画面の外（下）に置き、触れないようにする
                    translationY = (1f - drawer.progress) * heightPx
                    alpha = drawer.progress
                },
        )
    }
}

/** 上部の日付（Pixelの「At a Glance」風） */
@Composable
private fun AtAGlance() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    val date = remember(now) {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMdEEEE")
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
    }
    Text(
        text = date,
        style = MaterialTheme.typography.headlineSmall.copy(shadow = WallpaperTextShadow),
        color = Color.White,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp),
    )
}

/** 1ページ分のアイコン（下に寄せて並べる） */
@Composable
private fun HomePage(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        apps.chunked(DefaultLayout.COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    AppIconItem(
                        app = app,
                        labelColor = Color.White,
                        onWallpaper = true,
                        onClick = { onLaunch(app) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(DefaultLayout.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 下部のドック（名前なしのアイコン） */
@Composable
private fun Dock(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        apps.forEach { app ->
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable { onLaunch(app) },
            )
        }
    }
}

/** 最下部の検索バー */
@Composable
private fun SearchBar(onSearch: () -> Unit, onVoiceSearch: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f))
            .clickable(onClick = onSearch)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "検索",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onVoiceSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = "音声検索", tint = scheme.onSurfaceVariant)
        }
    }
}

/**
 * ホーム画面のドラッグを振り分ける。
 * 上へ動かし始めたらアプリ一覧を開き、下へ動かし始めたら通知パネルを開く。
 */
private class HomeDragRouter(
    private val drawer: DrawerState,
    private val shadeThresholdPx: Float,
    private val onOpenShade: () -> Unit,
) {
    private enum class Mode { UNDECIDED, DRAWER, SHADE }

    private var mode = Mode.UNDECIDED
    private var shadeDistance = 0f
    private var shadeOpened = false

    fun onStart() {
        mode = Mode.UNDECIDED
        shadeDistance = 0f
        shadeOpened = false
    }

    fun onDelta(delta: Float) {
        if (mode == Mode.UNDECIDED) {
            mode = if (delta < 0f || drawer.progress > 0f) Mode.DRAWER else Mode.SHADE
        }
        when (mode) {
            Mode.DRAWER -> drawer.dragBy(-delta)
            Mode.SHADE -> {
                shadeDistance += delta
                if (!shadeOpened && shadeDistance > shadeThresholdPx) {
                    shadeOpened = true
                    onOpenShade()
                }
            }
            Mode.UNDECIDED -> Unit
        }
    }

    fun onStop(velocity: Float) {
        if (mode == Mode.DRAWER) drawer.settle(-velocity)
        mode = Mode.UNDECIDED
    }
}
