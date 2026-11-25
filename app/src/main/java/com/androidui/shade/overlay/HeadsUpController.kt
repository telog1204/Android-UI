package com.androidui.shade.overlay

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.androidui.shade.notification.NotificationActions
import com.androidui.shade.notification.NotificationRepository
import com.androidui.shade.notification.ShadeNotification
import com.androidui.shade.reply.ReplyActivity
import com.androidui.shade.settings.ShadePrefs
import com.androidui.shade.ui.shade.HeadsUpPopup
import com.androidui.shade.ui.shade.ShadePanelActions
import com.androidui.shade.ui.theme.ShadeTheme
import com.androidui.shade.util.startActivitySafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** ポップアップ通知に表示する内容 */
class HeadsUpUiState {
    var item by mutableStateOf<ShadeNotification?>(null)

    /** 表示中か（false にすると消えるアニメーションのあとでウィンドウを外す） */
    var shown by mutableStateOf(false)
}

/**
 * 新しい通知を、画面上部にAndroid 15風のポップアップで表示する。
 */
class HeadsUpController(
    private val service: AccessibilityService,
    private val prefs: ShadePrefs,
    /** ポップアップを置く高さ（ステータスバーの高さ） */
    private val topOffsetPx: () -> Int,
    /** 通知パネルが開いているか（開いている間は出さない） */
    private val isPanelOpen: () -> Boolean,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val power = service.getSystemService(PowerManager::class.java)
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val density = service.resources.displayMetrics.density
    private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)

    val ui = HeadsUpUiState()

    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private val hideRunnable = Runnable { ui.shown = false }

    private val actions = ShadePanelActions(
        onOpen = { n ->
            n.contentIntent?.let { NotificationActions.send(service, it) }
            if (n.autoCancel && n.clearable) NotificationRepository.dismiss(n.key)
            hide()
        },
        onAction = { n, action ->
            if (action.remoteInputs.isNotEmpty()) {
                service.startActivitySafely(ReplyActivity.createIntent(service, action, n.title.ifEmpty { n.appName }))
                hide()
            } else {
                NotificationActions.send(service, action.intent)
                if (NotificationActions.opensActivity(action.intent)) hide()
            }
        },
        onDismiss = { n ->
            NotificationRepository.dismiss(n.key)
            hide()
        },
        onClearAll = {},
        onManage = {},
    )

    fun start() {
        NotificationRepository.headsUpListener = ::onHeadsUp
        scope.launch {
            // 表示中の通知が消えたら閉じ、内容が更新されたら差し替える
            NotificationRepository.items.collect { list ->
                val current = ui.item ?: return@collect
                val latest = list.firstOrNull { it.key == current.key }
                if (latest == null) hide() else ui.item = latest
            }
        }
    }

    fun stop() {
        NotificationRepository.headsUpListener = null
        scope.cancel()
        hideNow()
    }

    /** アニメーションで消す */
    fun hide() {
        handler.removeCallbacks(hideRunnable)
        ui.shown = false
    }

    /** すぐに消す */
    fun hideNow() {
        handler.removeCallbacks(hideRunnable)
        ui.shown = false
        removeWindow()
        ui.item = null
    }

    private fun onHeadsUp(item: ShadeNotification) {
        if (!prefs.headsUpEnabled) return
        if (power?.isInteractive != true) return
        if (keyguard?.isKeyguardLocked == true) return
        if (isPanelOpen()) return
        ui.item = item
        if (root == null) addWindow()
        ui.shown = true
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun onHidden() {
        removeWindow()
        ui.item = null
    }

    private fun addWindow() {
        val lifecycleOwner = OverlayLifecycleOwner().apply { create() }
        val view = object : FrameLayout(service) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                // 触れている間は消さない
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                    handler.removeCallbacks(hideRunnable)
                } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    if (ui.shown) scheduleHide()
                }
                return super.dispatchTouchEvent(event)
            }
        }
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        view.addView(
            ComposeView(service).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    ShadeTheme {
                        HeadsUpPopup(
                            state = ui,
                            actions = actions,
                            onSwipeAway = ::hide,
                            onHidden = ::onHidden,
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            y = topOffsetPx() + (4 * density).toInt()
            title = "AndroidUI heads-up"
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            }
        }

        try {
            windowManager.addView(view, params)
        } catch (e: RuntimeException) {
            lifecycleOwner.destroy()
            return
        }
        lifecycleOwner.resume()
        root = view
        owner = lifecycleOwner
    }

    private fun removeWindow() {
        val view = root ?: return
        root = null
        runCatching { windowManager.removeViewImmediate(view) }
        owner?.destroy()
        owner = null
    }

    private companion object {
        /** Android 15と同じく、5秒で自動的に消す */
        const val HIDE_DELAY_MS = 5000L
    }
}
