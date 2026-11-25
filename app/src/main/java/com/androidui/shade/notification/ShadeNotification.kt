package com.androidui.shade.notification

import android.app.PendingIntent
import android.app.RemoteInput
import androidx.compose.ui.graphics.ImageBitmap

/** パネルに表示する1件分の通知 */
data class ShadeNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val postTime: Long,
    val smallIcon: ImageBitmap?,
    val largeIcon: ImageBitmap?,
    val accentColor: Int,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
    val clearable: Boolean,
    val actions: List<ShadeAction>,
    /** 音楽アプリなどの再生用の通知か */
    val isMedia: Boolean,
)

/** 通知に付いているボタン（返信など） */
data class ShadeAction(
    val title: String,
    val intent: PendingIntent,
    val remoteInputs: List<RemoteInput>,
)
