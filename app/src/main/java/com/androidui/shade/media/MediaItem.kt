package com.androidui.shade.media

import android.app.PendingIntent
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap

/** 再生カード1枚分の内容 */
data class MediaItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val appIcon: ImageBitmap?,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val isPlaying: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val positionUpdateTime: Long,
    val playbackSpeed: Float,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val canSeek: Boolean,
    val customActions: List<MediaCustomAction>,
    val sessionActivity: PendingIntent?,
) {
    /** 最後に位置が知らされた時刻からの経過を足して、今の再生位置を求める */
    fun currentPositionMs(now: Long = SystemClock.elapsedRealtime()): Long {
        val position = if (isPlaying && positionUpdateTime > 0) {
            positionMs + ((now - positionUpdateTime) * playbackSpeed).toLong()
        } else {
            positionMs
        }
        return if (durationMs > 0) position.coerceIn(0L, durationMs) else position.coerceAtLeast(0L)
    }
}

/** アプリ独自のボタン（「いいね」など） */
data class MediaCustomAction(
    val action: String,
    val name: String,
    val icon: ImageBitmap?,
)
