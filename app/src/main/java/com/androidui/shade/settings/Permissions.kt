package com.androidui.shade.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.androidui.shade.overlay.ShadeAccessibilityService
import com.androidui.shade.util.startActivitySafely

/** 必要な許可の確認と、設定画面への移動 */
object Permissions {

    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = ComponentName(context, ShadeAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    /** 明るさや自動回転を変更できるか（「システム設定の変更」の許可） */
    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun openNotificationListenerSettings(context: Context) {
        context.startActivitySafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivitySafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openWriteSettings(context: Context) {
        context.startActivitySafely(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)),
            fallback = appDetailsIntent(context),
        )
    }

    fun openAppDetails(context: Context) {
        context.startActivitySafely(appDetailsIntent(context))
    }

    /** HyperOS（MIUI）の自動起動設定。開けない場合はアプリ情報を開く */
    fun openAutoStart(context: Context) {
        val intent = Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        )
        context.startActivitySafely(intent, fallback = appDetailsIntent(context))
    }

    private fun appDetailsIntent(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
}
