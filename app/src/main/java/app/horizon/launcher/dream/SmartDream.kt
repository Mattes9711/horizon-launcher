package app.horizon.launcher.dream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.view.View
import androidx.core.content.res.ResourcesCompat
import app.horizon.launcher.R
import app.horizon.launcher.ui.Art
import java.io.File
import java.util.Date
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Screensaver: slow Ken-Burns pans over your own photos (pushed with `tv.sh screensaver`),
 * or drifting colour "aurora" scenes when there are none. Any key wakes the TV.
 */
class SmartDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(DreamView(this))
    }
}

private class DreamView(context: Context) : View(context) {

    private val photos: List<File> = context.getExternalFilesDir("screensaver")
        ?.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
        ?.toList()?.shuffled().orEmpty()

    private val loader = Executors.newSingleThreadExecutor()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.RIGHT
        typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
        setShadowLayer(12f, 0f, 3f, 0x99000000.toInt())
    }
    private val timeFormat = DateFormat.getTimeFormat(context)

    private class Scene(val bitmap: Bitmap, val start: Long, val fromLeft: Boolean)

    private var current: Scene? = null
    private var previous: Scene? = null
    @Volatile private var next: Bitmap? = null
    private var index = 0

    init {
        setBackgroundColor(Color.BLACK)
        prepareNext()
    }

    private fun prepareNext() {
        val i = index++
        loader.execute {
            next = (if (photos.isNotEmpty()) decode(photos[i % photos.size]) else null) ?: aurora()
        }
    }

    private fun aurora(): Bitmap {
        val hue = Random.nextFloat() * 360f
        val accent = androidx.compose.ui.graphics.Color.hsv(hue, 0.6f, 0.8f)
        val p = Art.palette(accent)
        return Art.renderBitmap(
            p[0],
            listOf(
                Art.Blob(Random.nextFloat(), Random.nextFloat() * 0.6f, 0.7f + Random.nextFloat() * 0.3f, p[1]),
                Art.Blob(Random.nextFloat(), Random.nextFloat(), 0.5f + Random.nextFloat() * 0.3f, p[2]),
                Art.Blob(Random.nextFloat(), 0.6f + Random.nextFloat() * 0.4f, 0.6f, p[3]),
            ),
            w = 480, h = 270,
        )
    }

    private fun decode(f: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }.getOrNull()

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val cur = current
        if (cur == null || now - cur.start > SCENE_MS) {
            val n = next
            if (n != null) {
                previous = cur
                current = Scene(n, now, Random.nextBoolean())
                next = null
                prepareNext()
            }
        }
        val c = current
        if (c != null) {
            val fade = ((now - c.start) / FADE_MS.toFloat()).coerceIn(0f, 1f)
            previous?.let { if (fade < 1f) drawScene(canvas, it, now, 255) else previous = null }
            drawScene(canvas, c, now, (fade * 255).toInt())
        }
        clockPaint.textSize = height * 0.05f
        canvas.drawText(timeFormat.format(Date()), width - width * 0.04f, height - height * 0.06f, clockPaint)
        postInvalidateOnAnimation()
    }

    /** Ken Burns: slow zoom 1.0 -> 1.15 with a gentle sideways pan. */
    private fun drawScene(canvas: Canvas, s: Scene, now: Long, alpha: Int) {
        val t = ((now - s.start) / (SCENE_MS + FADE_MS).toFloat()).coerceIn(0f, 1f)
        val b = s.bitmap
        val base = maxOf(width / b.width.toFloat(), height / b.height.toFloat())
        val zoom = base * (1f + 0.15f * t)
        val extraX = b.width * zoom - width
        val extraY = b.height * zoom - height
        val pan = if (s.fromLeft) t else 1f - t
        matrix.reset()
        matrix.postScale(zoom, zoom)
        matrix.postTranslate(-extraX * pan, -extraY * 0.5f)
        paint.alpha = alpha
        canvas.drawBitmap(b, matrix, paint)
    }

    override fun onDetachedFromWindow() {
        loader.shutdownNow()
        super.onDetachedFromWindow()
    }

    companion object {
        const val SCENE_MS = 14_000L
        const val FADE_MS = 1_800L
    }
}
