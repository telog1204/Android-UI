package com.androidui.shade.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.androidui.shade.media.MediaSessionsController
import com.androidui.shade.notification.NotificationActions
import com.androidui.shade.notification.NotificationRepository
import com.androidui.shade.qs.QuickSettingsController
import com.androidui.shade.reply.ReplyActivity
import com.androidui.shade.settings.ShadePrefs
import com.androidui.shade.ui.shade.ShadePanel
import com.androidui.shade.ui.shade.ShadePanelActions
import com.androidui.shade.ui.theme.ShadeTheme
import com.androidui.shade.util.startActivitySafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 画面上端の透明な帯（スワイプ検知用）と、パネル本体のウィンドウを管理する。
 */
class ShadeOverlayController(
    private val service: AccessibilityService,
    private val prefs: ShadePrefs,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)

    val state = ShadeState(scope, flingThresholdPx = 600f * density, onClosed = { removePanel() })

    private val quickSettings = QuickSettingsController(
        service = service,
        collapse = { state.close() },
        closeImmediately = { closePanel(animate = false) },
    )

    private val media = MediaSessionsController(
        context = service,
        collapse = { state.close() },
    )

    private var strip: View? = null
    private var panelRoot: FrameLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelOwner: OverlayLifecycleOwner? = null
    private var blurJob: Job? = null

    private val panelActions = ShadePanelActions(
        onOpen = { n ->
            n.contentIntent?.let { NotificationActions.send(service, it) }
            if (n.autoCancel && n.clearable) NotificationRepository.dismiss(n.key)
            state.close()
        },
        onAction = { n, action ->
            if (action.remoteInputs.isNotEmpty()) {
                // 返信はキーボードがパネルの裏に隠れるため、パネルを閉じて入力画面を開く
                service.startActivitySafely(ReplyActivity.createIntent(service, action, n.title.ifEmpty { n.appName }))
                state.close()
            } else {
                NotificationActions.send(service, action.intent)
                // 画面を開くボタンのときだけ閉じる（その場で済む操作では開いたままにする）
                if (opensActivity(action.intent)) state.close()
            }
        },
        onDismiss = { n -> NotificationRepository.dismiss(n.key) },
        onClearAll = { NotificationRepository.dismissAll() },
        onManage = {
            service.startActivitySafely(Intent("android.settings.NOTIFICATION_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
            state.close()
        },
    )

    fun start() {
        quickSettings.start()
        media.start()
        applySettings()
    }

    /** 設定に合わせて帯を表示・更新・削除する */
    fun applySettings() {
        if (prefs.enabled) {
            showStrip()
        } else {
            removeStrip()
            closePanel(animate = false)
        }
    }

    fun onConfigurationChanged() {
        closePanel(animate = false)
        quickSettings.refresh()
        strip?.let { runCatching { windowManager.updateViewLayout(it, stripLayoutParams()) } }
    }

    fun openPanel() {
        if (!prefs.enabled) return
        showPanel()
        state.open()
    }

    fun closePanel(animate: Boolean) {
        if (panelRoot == null) return
        if (animate) {
            state.close()
        } else {
            state.snapClosed()
            removePanel()
        }
    }

    fun stop() {
        quickSettings.stop()
        media.stop()
        removeStrip()
        state.snapClosed()
        removePanel()
        scope.cancel()
    }

    // ---- 画面上端の帯 ----

    @SuppressLint("ClickableViewAccessibility")
    private fun showStrip() {
        val current = strip
        if (current != null) {
            runCatching { windowManager.updateViewLayout(current, stripLayoutParams()) }
            return
        }
        val view = View(service).apply { setOnTouchListener(StripTouchListener()) }
        try {
            windowManager.addView(view, stripLayoutParams())
            strip = view
        } catch (e: RuntimeException) {
            // 追加できない場合は何もしない（ユーザー補助が無効になった直後など）
        }
    }

    private fun removeStrip() {
        val view = strip ?: return
        strip = null
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun stripLayoutParams(): WindowManager.LayoutParams {
        val width = if (prefs.leftHalfOnly) screenSize().first / 2 else ViewGroup.LayoutParams.MATCH_PARENT
        return baseParams(width, systemInset(top = true), focusable = false).apply {
            title = "AndroidUI shade handle"
        }
    }

    /** 帯の上での下スワイプを検知して、指に合わせてパネルを開く */
    private inner class StripTouchListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
        private var downY = 0f
        private var dragging = false
        private var tracker: VelocityTracker? = null

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    dragging = false
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                }

                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(event)
                    if (!dragging && event.rawY - downY > touchSlop) {
                        dragging = true
                        downY += touchSlop
                        showPanel()
                        state.startDrag()
                    }
                    if (dragging) state.dragTo(event.rawY - downY)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(event)
                    if (dragging) {
                        val velocity = if (event.actionMasked == MotionEvent.ACTION_UP) {
                            tracker?.computeCurrentVelocity(1000)
                            tracker?.yVelocity ?: 0f
                        } else {
                            0f
                        }
                        state.settle(velocity)
                    }
                    dragging = false
                    tracker?.recycle()
                    tracker = null
                }
            }
            return true
        }
    }

    // ---- パネル本体 ----

    private fun showPanel() {
        if (panelRoot != null) return
        val screenHeight = screenSize().second
        state.openDistancePx = screenHeight * 0.4f
        state.qsDistancePx = screenHeight * 0.3f
        quickSettings.onPanelShown()
        media.onPanelShown()
        val topInset = (systemInset(top = true) / density).dp
        val bottomInset = (systemInset(top = false) / density).dp

        val owner = OverlayLifecycleOwner().apply { create() }
        val root = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) state.handleBack()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.addView(
            ComposeView(service).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    ShadeTheme {
                        ShadePanel(
                            state = state,
                            actions = panelActions,
                            quickSettings = quickSettings,
                            media = media,
                            topInset = topInset,
                            bottomInset = bottomInset,
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        val params = baseParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            focusable = true,
        ).apply {
            title = "AndroidUI shade panel"
            if (Build.VERSION.SDK_INT >= 31) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 0
            }
        }

        try {
            windowManager.addView(root, params)
        } catch (e: RuntimeException) {
            owner.destroy()
            return
        }
        owner.resume()
        panelRoot = root
        panelParams = params
        panelOwner = owner
        if (Build.VERSION.SDK_INT >= 31) startBlurSync()
    }

    /** 開き具合に合わせて背景のぼかしを強くする */
    @RequiresApi(31)
    private fun startBlurSync() {
        val maxBlurPx = 32f * density
        blurJob = scope.launch {
            snapshotFlow { ((state.expansion * maxBlurPx) / 4f).roundToInt() * 4 }
                .collect { radius ->
                    val root = panelRoot ?: return@collect
                    val params = panelParams ?: return@collect
                    if (!windowManager.isCrossWindowBlurEnabled) return@collect
                    params.blurBehindRadius = radius
                    runCatching { windowManager.updateViewLayout(root, params) }
                }
        }
    }

    private fun removePanel() {
        blurJob?.cancel()
        blurJob = null
        val root = panelRoot ?: return
        panelRoot = null
        panelParams = null
        runCatching { windowManager.removeViewImmediate(root) }
        panelOwner?.destroy()
        panelOwner = null
    }

    // ---- 共通 ----

    /** ボタンが画面を開くものか（Android 12未満は判定できないため、閉じる側に倒す） */
    private fun opensActivity(intent: PendingIntent): Boolean =
        if (Build.VERSION.SDK_INT >= 31) intent.isActivity else true

    private fun baseParams(width: Int, height: Int, focusable: Boolean) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= 30) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        } else {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    /** ステータスバー（top=true）またはナビゲーションバー（top=false）の高さ */
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun systemInset(top: Boolean): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            val type = if (top) {
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
            } else {
                WindowInsets.Type.navigationBars()
            }
            val insets = windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(type)
            val value = if (top) insets.top else insets.bottom
            if (value > 0) return value
            if (!top) return 0
        }
        val name = if (top) "status_bar_height" else "navigation_bar_height"
        val id = service.resources.getIdentifier(name, "dimen", "android")
        return when {
            id > 0 -> service.resources.getDimensionPixelSize(id)
            top -> (24 * density).toInt()
            else -> 0
        }
    }
}
