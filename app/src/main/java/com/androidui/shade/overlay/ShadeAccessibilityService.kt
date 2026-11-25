package com.androidui.shade.overlay

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.androidui.shade.settings.ShadePrefs
import com.androidui.shade.volume.VolumePanelController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ユーザー補助サービス。画面の内容は読み取らず、
 * ステータスバーより上に重ねて表示できる「ユーザー補助の重ね表示」と、
 * 音量ボタンの操作の受け取りのためだけに利用する。
 */
class ShadeAccessibilityService : AccessibilityService() {

    private var controller: ShadeOverlayController? = null
    private var volume: VolumePanelController? = null
    private var prefs: ShadePrefs? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        controller?.applySettings()
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            controller?.closePanel(animate = false)
            volume?.hide()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val shadePrefs = ShadePrefs(this)
        prefs = shadePrefs
        controller = ShadeOverlayController(this, shadePrefs).also { it.start() }
        volume = VolumePanelController(this, shadePrefs)
        shadePrefs.registerListener(prefListener)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        instance = this
        _running.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** 音量ボタンの操作（音量パネルで処理したら true を返し、OSには渡さない） */
    override fun onKeyEvent(event: KeyEvent): Boolean = volume?.onKeyEvent(event) ?: false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    /** 設定画面の「パネルを開いてみる」から呼ばれる */
    fun openPanel() {
        controller?.openPanel()
    }

    private fun shutdown() {
        val current = controller ?: return
        controller = null
        volume?.stop()
        volume = null
        prefs?.unregisterListener(prefListener)
        runCatching { unregisterReceiver(screenOffReceiver) }
        current.stop()
        if (instance === this) instance = null
        _running.value = false
    }

    companion object {
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        @Volatile
        var instance: ShadeAccessibilityService? = null
            private set
    }
}
