package com.androidui.shade.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** アプリのアイコンを、Pixelと同じ丸い形にそろえて描く */
object IconRenderer {

    fun render(drawable: Drawable, sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val half = sizePx / 2f
        canvas.clipPath(Path().apply { addCircle(half, half, half, Path.Direction.CW) })

        if (drawable is AdaptiveIconDrawable) {
            // アダプティブアイコンは、見える範囲（中央の72/108）の外側にも絵があるため、はみ出す分だけ広げて描く
            val extra = (sizePx * 0.25f).toInt()
            drawable.background?.let {
                it.setBounds(-extra, -extra, sizePx + extra, sizePx + extra)
                it.draw(canvas)
            }
            drawable.foreground?.let {
                it.setBounds(-extra, -extra, sizePx + extra, sizePx + extra)
                it.draw(canvas)
            }
        } else {
            // 従来の形式のアイコンは、白い丸の中に縮小して描く（Pixelと同じ扱い）
            canvas.drawCircle(half, half, half, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            val inset = (sizePx * 0.18f).toInt()
            drawable.setBounds(inset, inset, sizePx - inset, sizePx - inset)
            drawable.draw(canvas)
        }
        return bitmap.asImageBitmap()
    }
}
