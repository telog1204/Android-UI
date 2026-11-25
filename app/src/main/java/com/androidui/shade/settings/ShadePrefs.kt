package com.androidui.shade.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** アプリの設定値 */
class ShadePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** パネルを有効にするか */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** 画面上端の左半分だけで反応するか（右半分はHyperOSのコントロールセンターを残す） */
    var leftHalfOnly: Boolean
        get() = prefs.getBoolean(KEY_LEFT_HALF_ONLY, true)
        set(value) = prefs.edit { putBoolean(KEY_LEFT_HALF_ONLY, value) }

    /** 音量ボタンで、HyperOSの代わりにAndroid 15風の音量パネルを表示するか */
    var volumePanelEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_PANEL, true)
        set(value) = prefs.edit { putBoolean(KEY_VOLUME_PANEL, value) }

    /** クイック設定に表示するタイルの並び順（カンマ区切り。null なら初期の並び） */
    var tileOrder: String?
        get() = prefs.getString(KEY_TILE_ORDER, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_TILE_ORDER) else putString(KEY_TILE_ORDER, value)
        }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val NAME = "shade_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_LEFT_HALF_ONLY = "left_half_only"
        const val KEY_VOLUME_PANEL = "volume_panel"
        const val KEY_TILE_ORDER = "tile_order"
    }
}
