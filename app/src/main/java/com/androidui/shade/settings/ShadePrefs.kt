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

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val NAME = "shade_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_LEFT_HALF_ONLY = "left_half_only"
    }
}
