package com.androidui.shade.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony

/** ホーム画面の配置（下部のドックと、各ページのアプリ） */
data class HomeLayout(
    val dock: List<AppEntry>,
    val pages: List<List<AppEntry>>,
)

/**
 * 配置を変える機能ができるまでの、最初の配置。
 * ドックには電話・メッセージ・ブラウザ・カメラを、1ページ目にはよく使うアプリを置く。
 */
object DefaultLayout {
    const val COLUMNS = 4
    private const val FIRST_PAGE_ROWS = 3

    fun build(context: Context, apps: List<AppEntry>): HomeLayout {
        if (apps.isEmpty()) return HomeLayout(emptyList(), emptyList())
        val pm = context.packageManager
        val byPackage = apps.groupBy { it.packageName }

        fun pick(packageName: String?): AppEntry? = packageName?.let { byPackage[it]?.firstOrNull() }

        /** その操作を受け持つアプリ（既定のアプリ、なければ最初に見つかったもの） */
        fun resolve(intent: Intent): AppEntry? {
            val default = runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull()?.takeIf { it != "android" }
            val packageName = default ?: runCatching {
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).firstOrNull()?.activityInfo?.packageName
            }.getOrNull()
            return pick(packageName)
        }

        fun selector(category: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

        val dock = listOfNotNull(
            resolve(Intent(Intent.ACTION_DIAL)),
            pick(runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull())
                ?: resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))),
            resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).distinctBy { it.key }

        val dockKeys = dock.mapTo(HashSet()) { it.key }
        val firstPage = (
            listOf(
                selector(Intent.CATEGORY_APP_CALENDAR),
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                selector(Intent.CATEGORY_APP_GALLERY),
                selector(Intent.CATEGORY_APP_EMAIL),
                selector(Intent.CATEGORY_APP_MAPS),
                selector(Intent.CATEGORY_APP_MUSIC),
                selector(Intent.CATEGORY_APP_CONTACTS),
                selector(Intent.CATEGORY_APP_FILES),
                Intent(Settings.ACTION_SETTINGS),
            ).mapNotNull { resolve(it) } +
                listOfNotNull(
                    pick("com.android.vending"),
                    pick("com.google.android.youtube"),
                    // このアプリ（設定画面を開けるように）
                    pick(context.packageName),
                )
            )
            .filter { it.key !in dockKeys }
            .distinctBy { it.key }
            .take(COLUMNS * FIRST_PAGE_ROWS)

        return HomeLayout(dock = dock, pages = listOf(firstPage))
    }
}
