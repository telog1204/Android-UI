package com.androidui.shade.ui.shade

import com.androidui.shade.notification.ShadeAction
import com.androidui.shade.notification.ShadeNotification

/** パネル上の操作を受け取る */
class ShadePanelActions(
    val onOpen: (ShadeNotification) -> Unit,
    val onAction: (ShadeNotification, ShadeAction) -> Unit,
    val onDismiss: (ShadeNotification) -> Unit,
    val onClearAll: () -> Unit,
    val onManage: () -> Unit,
)
