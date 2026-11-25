package com.androidui.shade.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 通知リスナーとパネル画面の橋渡しをする */
object NotificationRepository {

    private val _items = MutableStateFlow<List<ShadeNotification>>(emptyList())
    val items: StateFlow<List<ShadeNotification>> = _items.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var listener: ShadeNotificationListener? = null

    internal fun onConnected(service: ShadeNotificationListener) {
        listener = service
        _connected.value = true
    }

    internal fun onDisconnected(service: ShadeNotificationListener) {
        if (listener !== service) return
        listener = null
        _connected.value = false
        _items.value = emptyList()
    }

    /** ポップアップ表示すべき通知が届いたときに呼ばれる（ポップアップ通知の表示役が設定する） */
    var headsUpListener: ((ShadeNotification) -> Unit)? = null

    internal fun emitHeadsUp(item: ShadeNotification) {
        headsUpListener?.invoke(item)
    }

    internal fun update(list: List<ShadeNotification>) {
        _items.value = list
    }

    /** 1件消去する（画面からはすぐに消し、実際の削除はシステムに依頼する） */
    fun dismiss(key: String) {
        _items.update { list -> list.filterNot { it.key == key } }
        runCatching { listener?.cancelNotification(key) }
    }

    /** サイレント（通知の制限）を切り替える。通知リスナーが使えない場合は false */
    fun requestInterruptionFilter(filter: Int): Boolean {
        val service = listener ?: return false
        return runCatching { service.requestInterruptionFilter(filter) }.isSuccess
    }

    /** 消去できる通知をすべて消去する */
    fun dismissAll() {
        _items.update { list -> list.filterNot { it.clearable } }
        runCatching { listener?.cancelAllNotifications() }
    }
}
