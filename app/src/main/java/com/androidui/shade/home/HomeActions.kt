package com.androidui.shade.home

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.speech.RecognizerIntent
import android.widget.Toast
import com.androidui.shade.overlay.ShadeAccessibilityService
import com.androidui.shade.util.startActivitySafely

/** ホーム画面からの操作 */
object HomeActions {

    fun launch(context: Context, app: AppEntry) {
        val launched = runCatching {
            context.getSystemService(LauncherApps::class.java)
                .startMainActivity(app.component, Process.myUserHandle(), null, null)
        }.isSuccess
        if (!launched) {
            context.startActivitySafely(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component),
            )
        }
    }

    /** 検索バーのタップ：Google検索を開く */
    fun search(context: Context) {
        context.startActivitySafely(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH),
            fallback = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, ""),
        )
    }

    /** マイクのタップ：音声検索を開く */
    fun voiceSearch(context: Context) {
        context.startActivitySafely(
            Intent(RecognizerIntent.ACTION_WEB_SEARCH),
            fallback = Intent(Intent.ACTION_VOICE_COMMAND),
        )
    }

    /** アプリ一覧で見つからなかった言葉をWebで検索する */
    fun webSearch(context: Context, query: String) {
        context.startActivitySafely(
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query),
            fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))),
        )
    }

    /** ホーム画面で下へスワイプ：このアプリの通知パネルを開く */
    fun openShade(context: Context) {
        val service = ShadeAccessibilityService.instance
        if (service != null) {
            service.openPanel()
        } else {
            Toast.makeText(context, "通知パネルを使うには、ユーザー補助をオンにしてください", Toast.LENGTH_SHORT).show()
        }
    }
}
