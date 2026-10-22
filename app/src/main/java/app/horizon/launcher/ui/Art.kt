package app.horizon.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Soft colour-blob artwork, rendered once on the CPU into a small bitmap. */
object Art {
    data class Blob(val cx: Float, val cy: Float, val r: Float, val color: Int)

    fun render(base: Int, blobs: List<Blob>, w: Int = 384, h: Int = 216): ImageBitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(base)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        for (b in blobs) {
            val x = w * b.cx
            val y = h * b.cy
            val r = w * b.r
            paint.shader = RadialGradient(x, y, r, b.color, b.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, paint)
        }
        return bmp.asImageBitmap()
    }
}
