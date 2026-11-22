package com.androidui.shade.notification

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/** 通知のタップやボタン操作を実行する */
object NotificationActions {

    fun send(context: Context, pendingIntent: PendingIntent, fillIn: Intent? = null): Boolean = try {
        pendingIntent.send(context, 0, fillIn, null, null, null, backgroundStartOptions())
        true
    } catch (e: Exception) {
        false
    }

    fun reply(context: Context, pendingIntent: PendingIntent, inputs: List<RemoteInput>, text: String): Boolean {
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val fillIn = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(inputs.toTypedArray(), fillIn, results)
        RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
        return send(context, pendingIntent, fillIn)
    }

    /** Android 14以降、バックグラウンドからの画面起動を許可するために必要 */
    @Suppress("DEPRECATION")
    private fun backgroundStartOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < 34) return null
        return ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
    }
}
