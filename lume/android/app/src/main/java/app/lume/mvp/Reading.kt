package app.lume.mvp

import android.graphics.Bitmap

object Reading {
    var image: Bitmap? = null
    var text: String = ""
    var isDemoExample = false
    var version = 0
    fun clear() { version++; image = null; text = ""; isDemoExample = false }
    fun fit(bitmap: Bitmap): Bitmap {
        val scale = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
        return if (scale == 1f) bitmap else Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }
}
