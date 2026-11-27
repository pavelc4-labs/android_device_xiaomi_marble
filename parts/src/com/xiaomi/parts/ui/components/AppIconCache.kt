package com.xiaomi.parts.ui.components

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap

object AppIconCache {
    private const val MAX_PX = 96
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    fun get(packageName: String): ImageBitmap? = cache[packageName]
    fun isMiss(packageName: String): Boolean = misses.contains(packageName)

    fun load(
        pm: PackageManager,
        packageName: String,
    ): ImageBitmap? {
        cache[packageName]?.let { return it }
        if (misses.contains(packageName)) return null
        return try {
            val drawable = pm.getApplicationIcon(packageName)
            val srcW = drawable.intrinsicWidth.coerceAtLeast(1)
            val srcH = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = MAX_PX.toFloat() / maxOf(srcW, srcH).toFloat()
            val w = if (scale < 1f) (srcW * scale).toInt().coerceAtLeast(1) else srcW
            val h = if (scale < 1f) (srcH * scale).toInt().coerceAtLeast(1) else srcH
            val bmp =
                if (drawable is BitmapDrawable && drawable.bitmap != null && srcW <= MAX_PX && srcH <= MAX_PX) {
                    drawable.bitmap
                } else {
                    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(out)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(canvas)
                    out
                }
            val ib = bmp.asImageBitmap()
            cache[packageName] = ib
            ib
        } catch (_: Throwable) {
            misses.add(packageName)
            null
        }
    }
}
