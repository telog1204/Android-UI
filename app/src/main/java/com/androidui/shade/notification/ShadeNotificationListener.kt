package com.androidui.shade.notification

import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** 表示中の通知を読み取り、NotificationRepository に渡す */
class ShadeNotificationListener : NotificationListenerService() {

    private class CachedItem(val postTime: Long, val item: ShadeNotification)

    private val cache = HashMap<String, CachedItem>()
    private val appNames = HashMap<String, String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationRepository.onConnected(this)
        refresh()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationRepository.onDisconnected(this)
        cache.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    private fun refresh() {
        val active: Array<StatusBarNotification> =
            try { activeNotifications } catch (e: RuntimeException) { null } ?: return
        val order: Array<String> =
            try { currentRanking?.orderedKeys } catch (e: RuntimeException) { null } ?: emptyArray()
        val rank = HashMap<String, Int>().apply { order.forEachIndexed { index, key -> put(key, index) } }

        // 子の通知があるグループでは、まとめ用の通知（サマリー）を表示しない
        val groupsWithChildren = active.filterNot { it.isGroupSummary() }.mapTo(HashSet()) { it.groupKey }
        val visible = active.filterNot { it.isGroupSummary() && it.groupKey in groupsWithChildren }

        cache.keys.retainAll(visible.mapTo(HashSet()) { it.key })
        val items = visible
            .sortedBy { rank[it.key] ?: Int.MAX_VALUE }
            .map { toItem(it) }
        NotificationRepository.update(items)
    }

    private fun StatusBarNotification.isGroupSummary(): Boolean =
        notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    private fun toItem(sbn: StatusBarNotification): ShadeNotification {
        cache[sbn.key]?.let { if (it.postTime == sbn.postTime) return it.item }

        val n = sbn.notification
        val extras = n.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: lastMessage(extras))
            ?.toString().orEmpty()
        val bigText = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: textLines(extras))
            ?.toString()?.takeIf { it.isNotBlank() } ?: text
        val density = resources.displayMetrics.density

        val item = ShadeNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = extras.getString(EXTRA_SUBSTITUTE_APP_NAME) ?: appName(sbn.packageName),
            title = title,
            text = text,
            bigText = bigText,
            postTime = sbn.postTime,
            smallIcon = loadIcon(n.smallIcon, (24 * density).toInt()),
            largeIcon = loadIcon(n.getLargeIcon(), (48 * density).toInt()),
            accentColor = n.color,
            contentIntent = n.contentIntent,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            clearable = sbn.isClearable,
            actions = n.actions.orEmpty().mapNotNull { toAction(it) },
        )
        cache[sbn.key] = CachedItem(sbn.postTime, item)
        return item
    }

    private fun toAction(action: Notification.Action): ShadeAction? {
        val intent = action.actionIntent ?: return null
        val inputs = action.remoteInputs.orEmpty().filter { it.allowFreeFormInput }
        return ShadeAction(action.title?.toString().orEmpty(), intent, inputs)
    }

    private fun lastMessage(extras: Bundle): CharSequence? {
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = messages.lastOrNull() as? Bundle ?: return null
        return last.getCharSequence("text")
    }

    private fun textLines(extras: Bundle): CharSequence? =
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")

    private fun appName(packageName: String): String = appNames.getOrPut(packageName) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun loadIcon(icon: Icon?, sizePx: Int): ImageBitmap? {
        if (icon == null) return null
        return try {
            val drawable = icon.loadDrawable(this) ?: return null
            drawable.toBitmap(sizePx, sizePx).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        /** システムアプリが表示名を差し替えるときに使う追加情報 */
        const val EXTRA_SUBSTITUTE_APP_NAME = "android.substName"
    }
}
