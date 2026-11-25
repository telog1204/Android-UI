package com.androidui.shade.volume

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.androidui.shade.overlay.OverlayLifecycleOwner
import com.androidui.shade.settings.ShadePrefs
import com.androidui.shade.ui.theme.ShadeTheme
import com.androidui.shade.ui.volume.VolumePanel
import com.androidui.shade.util.startActivitySafely
import kotlin.math.roundToInt

/**
 * 音量ボタンの操作を受け取り、HyperOSの音量パネルの代わりにAndroid 15風の音量パネルを表示する。
 */
class VolumePanelController(
    private val service: AccessibilityService,
    private val prefs: ShadePrefs,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val audio = service.getSystemService(AudioManager::class.java)
    private val power = service.getSystemService(PowerManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val density = service.resources.displayMetrics.density

    val ui = VolumeUiState()

    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private val hideRunnable = Runnable { ui.shown = false }

    /**
     * 音量ボタンの操作。処理した場合は true を返し、OS（HyperOSの音量パネル）には渡さない。
     * 通話中・着信中・画面オフ中は OS に任せる。
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val up = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (!up && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (!prefs.volumePanelEnabled) return false
        if (power?.isInteractive != true) return false
        val manager = audio ?: return false
        if (manager.mode != AudioManager.MODE_NORMAL) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            runCatching { manager.adjustStreamVolume(STREAM, direction, 0) }
            show()
        }
        return true
    }

    fun show() {
        refresh()
        if (root == null) addWindow()
        ui.shown = true
        scheduleHide()
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        ui.shown = false
    }

    fun stop() {
        handler.removeCallbacks(hideRunnable)
        ui.shown = false
        removeWindow()
    }

    // ---- パネルからの操作 ----

    /** スライダーの位置（0〜1）に合わせて音量を変える */
    fun setFraction(fraction: Float) {
        val manager = audio ?: return
        val index = (ui.min + fraction.coerceIn(0f, 1f) * (ui.max - ui.min)).roundToInt()
        if (index != ui.level) runCatching { manager.setStreamVolume(STREAM, index, 0) }
        refresh()
        scheduleHide()
    }

    /** 着信モードを「通常 ⇔ マナーモード」で切り替える */
    fun toggleRinger() {
        val manager = audio ?: return
        val target = if (manager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            AudioManager.RINGER_MODE_VIBRATE
        } else {
            AudioManager.RINGER_MODE_NORMAL
        }
        try {
            manager.ringerMode = target
        } catch (e: SecurityException) {
            // サイレント状態から戻す場合など、権限が足りないときは音の設定を開く
            hide()
            service.startActivitySafely(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
        refresh()
        scheduleHide()
    }

    /** Android標準の音量設定パネル（メディア・通話・着信音・アラーム）を開く */
    fun openSettings() {
        hide()
        service.startActivitySafely(Intent(Settings.Panel.ACTION_VOLUME), Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    /** 消えるアニメーションが終わったら呼ばれる */
    fun onHidden() = removeWindow()

    // ---- 内部処理 ----

    private fun refresh() {
        val manager = audio ?: return
        ui.min = runCatching { manager.getStreamMinVolume(STREAM) }.getOrDefault(0)
        ui.max = manager.getStreamMaxVolume(STREAM)
        ui.level = manager.getStreamVolume(STREAM)
        ui.ringerMode = manager.ringerMode
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun addWindow() {
        val lifecycleOwner = OverlayLifecycleOwner().apply { create() }
        val view = object : FrameLayout(service) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    // パネルの外側をタッチしたら消す
                    hide()
                    return true
                }
                // 操作中は消さない
                scheduleHide()
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
                        VolumePanel(
                            state = ui,
                            onChange = ::setFraction,
                            onToggleRinger = ::toggleRinger,
                            onOpenSettings = ::openSettings,
                            onHidden = ::onHidden,
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            x = (12 * density).toInt()
            title = "AndroidUI volume panel"
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
        /** Android 15と同じく、メディアの音量を変える */
        const val STREAM = AudioManager.STREAM_MUSIC
        const val HIDE_DELAY_MS = 3000L
    }
}
