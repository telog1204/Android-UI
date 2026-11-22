package com.androidui.shade.qs

/** クイック設定のタイルの種類（並び順＝表示順） */
enum class TileId {
    INTERNET,
    BLUETOOTH,
    FLASHLIGHT,
    DND,
    ROTATION,
    AIRPLANE,
    LOCATION,
    DARK_MODE,
    VIBRATE,
    NFC,
    SCREENSHOT,
    LOCK,
}

/** タイル1つ分の表示内容 */
data class TileState(
    val id: TileId,
    val label: String,
    val secondary: String?,
    val active: Boolean,
)
