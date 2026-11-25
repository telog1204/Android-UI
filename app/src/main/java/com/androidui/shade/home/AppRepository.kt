package com.androidui.shade.home

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 端末に入っているアプリ（ホーム画面から起動できるもの）を読み込む。
 * アプリを入れたり消したりすると、自動で読み込み直す。
 */
class AppRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val iconSizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private var loadJob: Job? = null

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = changed(packageName)

        override fun onPackageAdded(packageName: String?, user: UserHandle?) = changed(packageName)

        override fun onPackageChanged(packageName: String?, user: UserHandle?) = changed(packageName)

        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            packageNames?.forEach { forgetIcons(it) }
            reload()
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            packageNames?.forEach { forgetIcons(it) }
            reload()
        }
    }

    fun start() {
        runCatching { launcherApps?.registerCallback(callback, Handler(Looper.getMainLooper())) }
        reload()
    }

    fun stop() {
        runCatching { launcherApps?.unregisterCallback(callback) }
        loadJob?.cancel()
    }

    private fun changed(packageName: String?) {
        packageName?.let { forgetIcons(it) }
        reload()
    }

    private fun forgetIcons(packageName: String) {
        iconCache.keys.removeAll { it.startsWith("$packageName/") }
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.Default) {
            _apps.value = load()
        }
    }

    private fun load(): List<AppEntry> {
        val manager = launcherApps ?: return emptyList()
        val densityDpi = context.resources.displayMetrics.densityDpi
        val collator = Collator.getInstance(Locale.getDefault())
        return runCatching { manager.getActivityList(null, Process.myUserHandle()) }
            .getOrDefault(emptyList())
            .mapNotNull { info ->
                runCatching {
                    val key = info.componentName.flattenToString()
                    val icon = iconCache.getOrPut(key) {
                        IconRenderer.render(info.getIcon(densityDpi), iconSizePx)
                    }
                    AppEntry(
                        key = key,
                        component = info.componentName,
                        packageName = info.componentName.packageName,
                        label = info.label?.toString()?.takeIf { it.isNotBlank() } ?: info.componentName.packageName,
                        icon = icon,
                    )
                }.getOrNull()
            }
            .sortedWith(compareBy(collator) { it.label })
    }

    private companion object {
        const val ICON_SIZE_DP = 56
    }
}
