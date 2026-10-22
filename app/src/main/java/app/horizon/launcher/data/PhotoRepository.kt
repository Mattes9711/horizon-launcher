package app.horizon.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A background photograph with an optional caption (place / photographer). */
data class Photo(val file: File, val caption: String?)

/**
 * Background photographs, CleanFrame style:
 *  - your own photos, pushed with `tv.sh photos <folder>` (or the single `tv.sh wallpaper` image)
 *  - optionally Bing's photo of the day: fetched at most every 12 h at 1920x1080, last 8 kept
 */
class PhotoRepository(private val context: Context) {

    private val bingDir = File(context.filesDir, "bing").apply { mkdirs() }
    private val stampFile = File(bingDir, ".last")

    fun list(includeBing: Boolean): List<Photo> {
        val own = buildList<File> {
            context.getExternalFilesDir("photos")?.listFiles()?.filter { it.isImage() }?.sortedBy { it.name }?.let { addAll(it) }
            context.getExternalFilesDir(null)?.listFiles { f -> f.name.startsWith("wallpaper.") && f.isImage() }?.let { addAll(it) }
        }.map { Photo(it, captionFor(it)) }
        if (own.isNotEmpty()) return own
        if (!includeBing) return emptyList()
        return bingDir.listFiles()?.filter { it.isImage() }?.sortedByDescending { it.name }?.map { Photo(it, captionFor(it)) }.orEmpty()
    }

    /** Downloads new Bing photos if the last check is older than 12 hours. Returns true if something new arrived. */
    fun refreshBing(): Boolean {
        val age = System.currentTimeMillis() - (stampFile.takeIf { it.exists() }?.lastModified() ?: 0L)
        if (age < 12 * 60 * 60 * 1000L) return false
        return try {
            val json = get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=de-DE").toString(Charsets.UTF_8)
            val images = JSONObject(json).getJSONArray("images")
            var added = false
            for (i in 0 until images.length()) {
                val img = images.getJSONObject(i)
                val date = img.optString("startdate")
                if (date.isEmpty()) continue
                val target = File(bingDir, "$date.jpg")
                if (target.exists()) continue
                val base = img.optString("urlbase")
                val url = if (base.isNotEmpty()) "https://www.bing.com${base}_1920x1080.jpg" else "https://www.bing.com" + img.getString("url")
                val bytes = get(url)
                File(bingDir, "$date.tmp").apply { writeBytes(bytes); renameTo(target) }
                File(bingDir, "$date.txt").writeText(img.optString("copyright").ifEmpty { img.optString("title") })
                added = true
            }
            // keep the newest 8
            bingDir.listFiles()?.filter { it.isImage() }?.sortedByDescending { it.name }?.drop(8)?.forEach {
                it.delete(); File(bingDir, it.nameWithoutExtension + ".txt").delete()
            }
            stampFile.writeText("ok"); stampFile.setLastModified(System.currentTimeMillis())
            added
        } catch (e: Exception) {
            Log.d("SmartTV", "bing: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    private fun captionFor(f: File): String? =
        File(f.parentFile, f.nameWithoutExtension + ".txt").takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "SmartTV-Launcher")
        return c.inputStream.use { it.readBytes() }
    }

    private fun File.isImage() = isFile && extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")

    companion object {
        /** Decodes a photo to at most [maxWidth] px wide in RGB_565 (half the memory of ARGB). */
        fun decode(f: File, maxWidth: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        }.getOrNull()
    }
}
