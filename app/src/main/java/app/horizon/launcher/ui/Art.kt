package app.horizon.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb

/** Soft colour-blob artwork, rendered once on the CPU into a small bitmap (cheap to draw full screen). */
object Art {
    data class Blob(val cx: Float, val cy: Float, val r: Float, val color: Int)

    fun render(base: Int, blobs: List<Blob>, w: Int = 384, h: Int = 216, heroShade: Boolean = false): ImageBitmap =
        renderBitmap(base, blobs, w, h, heroShade).asImageBitmap()

    fun renderBitmap(base: Int, blobs: List<Blob>, w: Int = 384, h: Int = 216, heroShade: Boolean = false): Bitmap {
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
        if (heroShade) {
            // baked in instead of an extra full-screen layer: dark top for the title, dark bottom for the dock
            paint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(0x59000000, 0x00000000, 0x00000000, 0x8C000000.toInt()),
                floatArrayOf(0f, 0.35f, 0.6f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        return bmp
    }

    /**
     * Frosted-glass version of a photo for behind the app grid: small, strongly blurred, slightly darkened.
     * Done once per photo on the CPU (Android 11 has no live blur), then simply stretched full screen.
     */
    fun frosted(src: Bitmap): ImageBitmap {
        val w = 192
        val h = 108
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        small.getPixels(a, 0, w, 0, 0, w, h)
        repeat(3) {
            boxBlur(a, b, w, h, 4, horizontal = true)
            boxBlur(b, a, w, h, 4, horizontal = false)
        }
        val out = Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0x26000000) // keeps white labels readable on bright photos
        return out.asImageBitmap()
    }

    private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val outer = if (horizontal) h else w
        val inner = if (horizontal) w else h
        for (o in 0 until outer) {
            for (i in 0 until inner) {
                var sr = 0; var sg = 0; var sb = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, inner - 1)
                    val p = if (horizontal) src[o * w + j] else src[j * w + o]
                    sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
                    n++
                }
                val idx = if (horizontal) o * w + i else i * w + o
                dst[idx] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            }
        }
    }

    /** Original "aurora" artwork in an app's own colours. */
    fun aurora(accent: Color, seed: Int = 0): ImageBitmap {
        val p = palette(accent)
        val s = (seed % 3) * 0.08f
        return render(
            p[0],
            listOf(
                Blob(0.22f + s, 0.30f, 0.80f, p[1]),
                Blob(0.85f - s, 0.15f + s, 0.60f, p[2]),
                Blob(0.70f, 0.85f - s, 0.70f, p[3]),
            ),
            heroShade = true,
        )
    }

    /** Calm light wallpaper behind the app grid, the same for every app. */
    fun pastelWallpaper(): ImageBitmap = render(
        0xFFDCE3EB.toInt(),
        listOf(
            Blob(0.18f, 0.22f, 0.75f, 0xFFADCBE8.toInt()),
            Blob(0.88f, 0.18f, 0.55f, 0xFFEBD9C1.toInt()),
            Blob(0.62f, 0.95f, 0.65f, 0xFFC6D5E4.toInt()),
            Blob(0.05f, 0.95f, 0.45f, 0xFFE4D9CB.toInt()),
        ),
    )

    fun palette(accent: Color): List<Int> {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgb(), out)
        fun hsv(h: Float, s: Float, v: Float) =
            android.graphics.Color.HSVToColor(floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        val h = out[0]
        val s = out[1].coerceAtLeast(0.35f)
        return listOf(
            hsv(h, s * 0.9f, 0.22f),          // deep base
            hsv(h, s, 0.85f),                 // main colour
            hsv(h + 35f, s * 0.7f, 0.95f),    // warm neighbour
            hsv(h - 40f, s * 0.8f, 0.60f),    // cool neighbour
        )
    }
}
