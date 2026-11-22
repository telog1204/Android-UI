package com.androidui.shade.util

import android.content.Context
import android.content.Intent

/** 画面を開く。開けなかった場合は fallback を試す。どちらも開けなければ false を返す */
fun Context.startActivitySafely(intent: Intent, fallback: Intent? = null): Boolean {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    } catch (e: Exception) {
        // 下の fallback を試す
    }
    if (fallback != null) {
        try {
            startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: Exception) {
            // 開ける画面がない
        }
    }
    return false
}
