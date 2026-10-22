package app.horizon.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SETTINGS_ID = "__settings"

/** One launchable app, with bitmaps pre-rendered off the main thread. */
data class AppEntry(
    val pkg: String,
    val label: String,
    val launch: Intent,
    /** Real Android TV banner (16:9), if the app has one. */
    val banner: ImageBitmap?,
    /** Icon, only used when there is no banner. */
    val icon: ImageBitmap?,
    /** Average colour of the artwork, used for fallback tiles and backgrounds. */
    val accent: Color,
    /** Original banner drawable, rendered at higher resolution for the hero background. */
    internal val source: Drawable?,
)

class AppRepository(private val context: Context) {

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppEntry>()
        val infos =
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
            ) + pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            )
        for (ri in infos) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName
            if (pkg == context.packageName || result.containsKey(pkg)) continue
            val launch = (pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: pm.getLaunchIntentForPackage(pkg)
                ?: Intent(Intent.ACTION_MAIN).setClassName(pkg, ai.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val banner = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull()
            val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
            result[pkg] = build(pkg, ri.loadLabel(pm).toString(), launch, banner, icon)
        }
        result[SETTINGS_ID] = build(
            SETTINGS_ID,
            "Einstellungen",
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            null,
            context.getDrawable(android.R.drawable.ic_menu_preferences),
        )
        result.values.toList()
    }

    private fun build(pkg: String, label: String, launch: Intent, bannerD: Drawable?, iconD: Drawable?): AppEntry {
        val banner = bannerD?.let { render(it, 256, 144) }
        val icon = if (banner == null) iconD?.let { render(it, 128, 128) } else null
        val src = banner ?: icon
        val accent = src?.let { averageColor(it) } ?: Color(0xFF2A2F45)
        return AppEntry(
            pkg = pkg,
            label = label,
            launch = launch,
            banner = banner?.asImageBitmap(),
            icon = icon?.asImageBitmap(),
            accent = accent,
            source = if (banner != null) bannerD else null,
        )
    }

    private fun render(d: Drawable, w: Int, h: Int): Bitmap = renderDrawable(d, w, h)

    private fun averageColor(b: Bitmap): Color {
        val px = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
        val c = Color(px)
        // transparent icons average to almost nothing - push towards a usable dark tone
        return if (c.alpha < 0.3f) Color(0xFF2A2F45) else c.copy(alpha = 1f)
    }

    companion object {
        fun renderDrawable(d: Drawable, w: Int, h: Int): Bitmap {
            val copy = d.constantState?.newDrawable()?.mutate() ?: d
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            copy.setBounds(0, 0, w, h)
            copy.draw(Canvas(b))
            return b
        }

        /** Hero background: banner at 480x270, lightly blurred. */
        fun backdropFrom(d: Drawable): ImageBitmap = blurred(renderDrawable(d, 480, 270), radius = 2, passes = 2)

        /**
         * Light blur for the hero background: the art stays recognisable, just soft.
         * Two passes of a small box blur on the 256x144 banner (≈ gaussian), done on the CPU
         * because Android 11 has no RenderEffect. The upscale to full screen adds extra softness.
         */
        fun blurred(src: Bitmap, radius: Int = 1, passes: Int = 2): ImageBitmap {
            val w = src.width
            val h = src.height
            val a = IntArray(w * h)
            val b = IntArray(w * h)
            src.getPixels(a, 0, w, 0, 0, w, h)
            repeat(passes) {
                boxBlur(a, b, w, h, radius, horizontal = true)
                boxBlur(b, a, w, h, radius, horizontal = false)
            }
            return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
        }

        private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val outer = if (horizontal) h else w
            val inner = if (horizontal) w else h
            for (o in 0 until outer) {
                for (i in 0 until inner) {
                    var sa = 0; var sr = 0; var sg = 0; var sb = 0; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, inner - 1)
                        val p = if (horizontal) src[o * w + j] else src[j * w + o]
                        sa += p ushr 24; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
                        n++
                    }
                    val idx = if (horizontal) o * w + i else i * w + o
                    dst[idx] = ((sa / n) shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                }
            }
        }
    }
}
