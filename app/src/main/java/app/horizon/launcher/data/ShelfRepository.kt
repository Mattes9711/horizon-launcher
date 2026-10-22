package app.horizon.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** One page of the Top Shelf: an image, an optional caption and what OK opens. */
data class ShelfSlide(val image: ImageBitmap?, val caption: String?, val intent: Intent?)

/**
 * Finds artwork for the Top Shelf, best source first:
 * 1. your own images pushed with `tv.sh hero` (hero/<package>.jpg, hero/<package>-2.jpg, ...)
 * 2. the app's Android TV preview / "watch next" programs (only if the TV lets us read them)
 */
class ShelfRepository(private val context: Context) {

    fun load(pkg: String): List<ShelfSlide> {
        val own = userImages(pkg).mapNotNull { f -> decodeFile(f)?.let { ShelfSlide(it, null, null) } }
        if (own.isNotEmpty()) return own
        return programs(pkg)
    }

    private fun userImages(pkg: String): List<File> {
        val dir = context.getExternalFilesDir("hero") ?: return emptyList()
        return dir.listFiles { f ->
            f.isFile && (f.name.startsWith("$pkg.") || f.name.startsWith("$pkg-")) &&
                f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }?.sortedBy { it.name }.orEmpty().take(6)
    }

    private fun programs(pkg: String): List<ShelfSlide> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val out = ArrayList<ShelfSlide>()
        for (uri in listOf(TvContract.WatchNextPrograms.CONTENT_URI, TvContract.PreviewPrograms.CONTENT_URI)) {
            if (out.size >= 5) break
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf("title", "poster_art_uri", "thumbnail_uri", "intent_uri", "package_name"),
                    "package_name=?", arrayOf(pkg), null,
                )?.use { c ->
                    Log.d(TAG, "shelf $pkg ${uri.lastPathSegment}: ${c.count} rows")
                    while (c.moveToNext() && out.size < 5) {
                        val title = c.getString(0)
                        val art = c.getString(1) ?: c.getString(2) ?: continue
                        val intent = c.getString(3)?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
                        val img = decodeUri(art) ?: continue
                        out += ShelfSlide(img, title, intent)
                    }
                }
            } catch (e: Exception) {
                // no permission or no TV provider on this device - fall back to own art
                Log.d(TAG, "shelf $pkg ${uri.lastPathSegment}: ${e.javaClass.simpleName} ${e.message}")
            }
        }
        return out
    }

    private fun decodeFile(f: File): ImageBitmap? = runCatching { decode { f.inputStream() } }.getOrNull()

    private fun decodeUri(s: String): ImageBitmap? = runCatching {
        val uri = Uri.parse(s)
        when (uri.scheme) {
            "http", "https" -> decode {
                (URL(s).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000; readTimeout = 6000
                }.inputStream
            }
            else -> decode { context.contentResolver.openInputStream(uri)!! }
        }
    }.getOrNull()

    /** Decodes at most ~1280 px wide in RGB_565 to keep memory low on the TV. */
    private fun decode(open: () -> InputStream): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1280) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return open().use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
    }

    companion object {
        const val TAG = "SmartTV"

        fun decodeWallpaper(context: Context): ImageBitmap? {
            val dir = context.getExternalFilesDir(null) ?: return null
            val f = listOf("jpg", "jpeg", "png", "webp").map { File(dir, "wallpaper.$it") }.firstOrNull { it.isFile } ?: return null
            return ShelfRepository(context).decodeFile(f)
        }
    }
}
