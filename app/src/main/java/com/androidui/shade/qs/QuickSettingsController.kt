package com.androidui.shade.qs

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.androidui.shade.notification.NotificationRepository
import com.androidui.shade.settings.Permissions
import com.androidui.shade.settings.ShadePrefs
import com.androidui.shade.util.startActivitySafely
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * クイック設定のタイルと明るさの状態を読み取り、操作を実行する。
 */
class QuickSettingsController(
    private val service: AccessibilityService,
    private val prefs: ShadePrefs,
    /** パネルをアニメーションで閉じる */
    private val collapse: () -> Unit,
    /** パネルをすぐに閉じる（スクリーンショットなどの前に使う） */
    private val closeImmediately: () -> Unit,
) {
    private val context: Context = service
    private val handler = Handler(Looper.getMainLooper())
    private val resolver = context.contentResolver

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val bluetooth: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val camera = context.getSystemService(CameraManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val location = context.getSystemService(LocationManager::class.java)
    private val nfc: NfcAdapter? = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()

    private val torchCameraId: String? = findTorchCamera()
    private var torchOn = false

    private val brightnessMin: Float
    private val brightnessMax: Float
    private var lastWrittenBrightness = -1
    private var writeSettingsRequested = false

    /** 表示中のタイル（保存した並び順） */
    private val _tiles = MutableStateFlow<List<TileState>>(emptyList())
    val tiles: StateFlow<List<TileState>> = _tiles.asStateFlow()

    /** 非表示にしたタイル（編集画面の「追加できるタイル」） */
    private val _hiddenTiles = MutableStateFlow<List<TileState>>(emptyList())
    val hiddenTiles: StateFlow<List<TileState>> = _hiddenTiles.asStateFlow()

    /** 明るさスライダーの位置（0〜1） */
    private val _brightness = MutableStateFlow(0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { refresh() }
        }

        override fun onLost(network: Network) {
            handler.post { refresh() }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            handler.post { refresh() }
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                torchOn = enabled
                refresh()
            }
        }
    }

    init {
        val (min, max) = readBrightnessRange()
        brightnessMin = min.toFloat()
        brightnessMax = max.toFloat()
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        }
        // いずれもシステムだけが送る通知（Bluetoothなどは別のシステムアプリから届くため EXPORTED にする）
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        listOf(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
        ).forEach { resolver.registerContentObserver(it, false, observer) }
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback, handler) }
        runCatching { camera?.registerTorchCallback(torchCallback, handler) }
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { resolver.unregisterContentObserver(observer) }
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        runCatching { camera?.unregisterTorchCallback(torchCallback) }
    }

    /** パネルを開くときに呼ぶ */
    fun onPanelShown() {
        writeSettingsRequested = false
        refresh()
    }

    fun refresh() {
        val available = buildList {
            add(internetTile())
            add(onOffTile(TileId.BLUETOOTH, "Bluetooth", isBluetoothOn()))
            if (torchCameraId != null) add(onOffTile(TileId.FLASHLIGHT, "ライト", torchOn))
            add(onOffTile(TileId.DND, "サイレント", isDndOn()))
            add(onOffTile(TileId.ROTATION, "自動回転", isAutoRotateOn()))
            add(onOffTile(TileId.AIRPLANE, "機内モード", isAirplaneOn()))
            add(onOffTile(TileId.LOCATION, "位置情報", isLocationOn()))
            add(onOffTile(TileId.DARK_MODE, "ダークモード", isDarkMode()))
            add(onOffTile(TileId.VIBRATE, "マナーモード", audio?.ringerMode == AudioManager.RINGER_MODE_VIBRATE))
            if (nfc != null) add(onOffTile(TileId.NFC, "NFC", runCatching { nfc.isEnabled }.getOrDefault(false)))
            add(TileState(TileId.SCREENSHOT, "スクリーンショット", null, false))
            add(TileState(TileId.LOCK, "画面ロック", null, false))
        }.associateBy { it.id }
        val order = savedOrder() ?: available.keys.toList()
        val shown = order.mapNotNull { available[it] }
        _tiles.value = shown
        _hiddenTiles.value = available.values.filter { it.id !in order }
        _brightness.value = readBrightnessSlider()
    }

    // ---- 並び順の編集 ----

    /** 表示するタイルと並び順を保存する */
    fun saveOrder(ids: List<TileId>) {
        prefs.tileOrder = ids.distinct().joinToString(",") { it.name }
        refresh()
    }

    /** 初期の並びに戻す */
    fun resetOrder() {
        prefs.tileOrder = null
        refresh()
    }

    private fun savedOrder(): List<TileId>? {
        val saved = prefs.tileOrder ?: return null
        return saved.split(',').filter { it.isNotBlank() }.mapNotNull { name ->
            TileId.entries.firstOrNull { it.name == name }
        }
    }

    // ---- タイルの操作 ----

    fun onTileClick(id: TileId) {
        when (id) {
            TileId.INTERNET -> openPanel(
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
                Intent(Settings.ACTION_WIFI_SETTINGS),
            )
            TileId.BLUETOOTH -> openPanel(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            TileId.FLASHLIGHT -> toggleTorch()
            TileId.DND -> toggleDnd()
            TileId.ROTATION -> toggleAutoRotate()
            TileId.AIRPLANE -> openPanel(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            TileId.LOCATION -> openPanel(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            TileId.DARK_MODE -> openPanel(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            TileId.VIBRATE -> toggleVibrate()
            TileId.NFC -> openPanel(Intent(Settings.Panel.ACTION_NFC), Intent(Settings.ACTION_NFC_SETTINGS))
            TileId.SCREENSHOT -> runGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, delayMs = 400)
            TileId.LOCK -> runGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, delayMs = 150)
        }
    }

    /** 長押しで、対応する設定画面を開く */
    fun onTileLongClick(id: TileId) {
        val intent = when (id) {
            TileId.INTERNET -> Intent(Settings.ACTION_WIFI_SETTINGS)
            TileId.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            TileId.DND -> Intent(ZEN_MODE_SETTINGS)
            TileId.ROTATION, TileId.DARK_MODE -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            TileId.AIRPLANE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            TileId.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            TileId.VIBRATE -> Intent(Settings.ACTION_SOUND_SETTINGS)
            TileId.NFC -> Intent(Settings.ACTION_NFC_SETTINGS)
            TileId.FLASHLIGHT, TileId.SCREENSHOT, TileId.LOCK -> null
        } ?: return
        openPanel(intent, Intent(Settings.ACTION_SETTINGS))
    }

    fun openSettings() = openPanel(Intent(Settings.ACTION_SETTINGS))

    fun openPowerMenu() = runGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, delayMs = 150)

    /** 明るさスライダーの操作 */
    fun onBrightnessChange(slider: Float) {
        if (!Settings.System.canWrite(context)) {
            if (!writeSettingsRequested) {
                writeSettingsRequested = true
                closeImmediately()
                Permissions.openWriteSettings(context)
            }
            return
        }
        _brightness.value = slider.coerceIn(0f, 1f)
        val value = BrightnessConverter.toValue(slider, brightnessMin, brightnessMax).roundToInt()
        if (value != lastWrittenBrightness) {
            lastWrittenBrightness = value
            runCatching { Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value) }
        }
    }

    private fun openPanel(intent: Intent, fallback: Intent? = null) {
        if (context.startActivitySafely(intent, fallback)) collapse()
    }

    private fun runGlobalAction(action: Int, delayMs: Long) {
        closeImmediately()
        handler.postDelayed({ service.performGlobalAction(action) }, delayMs)
    }

    private fun toggleTorch() {
        val id = torchCameraId ?: return
        runCatching { camera?.setTorchMode(id, !torchOn) }
    }

    private fun toggleDnd() {
        val target = if (isDndOn()) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }
        // 「通知へのアクセス」の権限で切り替える。使えない場合は設定画面を開く
        if (NotificationRepository.requestInterruptionFilter(target)) return
        val manager = notificationManager
        if (manager != null && manager.isNotificationPolicyAccessGranted) {
            runCatching { manager.setInterruptionFilter(target) }
        } else {
            openPanel(Intent(ZEN_MODE_SETTINGS), Intent(Settings.ACTION_SOUND_SETTINGS))
        }
    }

    private fun toggleAutoRotate() {
        if (!Settings.System.canWrite(context)) {
            closeImmediately()
            Permissions.openWriteSettings(context)
            return
        }
        val value = if (isAutoRotateOn()) 0 else 1
        runCatching { Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, value) }
    }

    private fun toggleVibrate() {
        val manager = audio ?: return
        val target = if (manager.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            AudioManager.RINGER_MODE_NORMAL
        } else {
            AudioManager.RINGER_MODE_VIBRATE
        }
        try {
            manager.ringerMode = target
        } catch (e: SecurityException) {
            // サイレント状態から切り替える場合など、権限が足りないときは設定画面を開く
            openPanel(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
    }

    // ---- 状態の読み取り ----

    private fun onOffTile(id: TileId, label: String, on: Boolean) =
        TileState(id, label, if (on) "オン" else "オフ", on)

    private fun internetTile(): TileState {
        val wifiOn = runCatching { wifi?.isWifiEnabled == true }.getOrDefault(false)
        val capabilities = runCatching {
            connectivity?.let { it.getNetworkCapabilities(it.activeNetwork) }
        }.getOrNull()
        val secondary = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "モバイルデータ"
            wifiOn -> "未接続"
            else -> "オフ"
        }
        return TileState(TileId.INTERNET, "インターネット", secondary, wifiOn || capabilities != null)
    }

    @SuppressLint("MissingPermission")
    private fun isBluetoothOn(): Boolean = runCatching { bluetooth?.isEnabled == true }.getOrDefault(false)

    private fun isDndOn(): Boolean {
        val filter = notificationManager?.currentInterruptionFilter ?: return false
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun isAutoRotateOn(): Boolean =
        Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun isAirplaneOn(): Boolean =
        Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    private fun isLocationOn(): Boolean = runCatching { location?.isLocationEnabled == true }.getOrDefault(false)

    private fun isDarkMode(): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun readBrightnessSlider(): Float {
        val value = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightnessMax.toInt())
        return BrightnessConverter.toSlider(value.toFloat(), brightnessMin, brightnessMax)
    }

    /** 端末の明るさの範囲（Android標準の設定値。読めなければ 1〜255） */
    @SuppressLint("DiscouragedApi")
    private fun readBrightnessRange(): Pair<Int, Int> {
        val res = Resources.getSystem()
        fun intRes(name: String, default: Int): Int {
            val id = res.getIdentifier(name, "integer", "android")
            return if (id != 0) runCatching { res.getInteger(id) }.getOrDefault(default) else default
        }
        val min = intRes("config_screenBrightnessSettingMinimum", 1)
        val max = intRes("config_screenBrightnessSettingMaximum", 255)
        return if (min in 0 until max) min to max else 1 to 255
    }

    private fun findTorchCamera(): String? = runCatching {
        val manager = camera ?: return@runCatching null
        manager.cameraIdList.firstOrNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    private companion object {
        const val ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
    }
}
