package app.horizon.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Artwork rendered once on the CPU into small bitmaps (cheap to draw full screen, little RAM). */
object Art {
    data class Blob(val cx: Float, val cy: Float, val r: Float, val color: Int)

    const val INK = 0xFF0D0F14.toInt()

    fun renderBitmap(base: Int, blobs: List<Blob>, w: Int = 384, h: Int = 216): Bitmap {
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
        return bmp
    }

    /**
     * The calm "not really a picture" home background: dusk colours at the top,
     * fading into near-black where the cards sit.
     */
    fun homeGradient(): Bitmap {
        // 1280x720 in RGB_565 with dithering: crisp shapes on the TV, ~1.8 MB, still a single draw
        val w = 1280
        val h = 720
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        c.drawColor(INK)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.65f, intArrayOf(0xFF2E3A4F.toInt(), 0xFF6C5A4C.toInt(), 0xFF8A6F55.toInt()),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h * 0.65f, p)
        p.shader = null
        p.color = 0xFF3A3530.toInt()
        c.drawOval(w * 0.30f, h * 0.29f, w * 1.16f, h * 1.02f, p)
        p.color = 0xFF2B2A2A.toInt()
        c.drawOval(w * -0.16f, h * 0.36f, w * 0.55f, h * 1.08f, p)
        shade(c, w, h, p, strength = 1f)
        return bmp
    }

    /** Darkens the lower part (for the cards) and the left edge (for the clock), baked in. */
    fun shade(c: Canvas, w: Int, h: Int, p: Paint, strength: Float) {
        fun a(alpha: Float) = (((alpha * strength).coerceIn(0f, 1f) * 255).toInt() shl 24) or (INK and 0xFFFFFF)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(a(0f), a(0f), a(0.85f), a(1f)), floatArrayOf(0f, 0.38f, 0.66f, 0.80f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = LinearGradient(0f, 0f, w * 0.42f, 0f, a(0.5f), a(0f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w * 0.42f, h.toFloat(), p)
        p.shader = null
    }

    fun palette(accent: Color): List<Int> {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgb(), out)
        fun hsv(h: Float, s: Float, v: Float) =
            android.graphics.Color.HSVToColor(floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        val h = out[0]
        val s = out[1].coerceAtLeast(0.35f)
        return listOf(hsv(h, s * 0.9f, 0.22f), hsv(h, s, 0.85f), hsv(h + 35f, s * 0.7f, 0.95f), hsv(h - 40f, s * 0.8f, 0.60f))
    }
}
