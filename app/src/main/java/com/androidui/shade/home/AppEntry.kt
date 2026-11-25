package com.androidui.shade.home

import android.content.ComponentName
import androidx.compose.ui.graphics.ImageBitmap

/** ホーム画面とアプリ一覧に並べるアプリ1つ分 */
data class AppEntry(
    val key: String,
    val component: ComponentName,
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)
