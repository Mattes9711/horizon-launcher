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
    /** Small Android bitmap used to build the blurred hero background. */
    internal val source: Bitmap?,
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
            source = banner,
        )
    }

    private fun render(d: Drawable, w: Int, h: Int): Bitmap {
        val copy = d.constantState?.newDrawable()?.mutate() ?: d
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        copy.setBounds(0, 0, w, h)
        copy.draw(Canvas(b))
        return b
    }

    private fun averageColor(b: Bitmap): Color {
        val px = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
        val c = Color(px)
        // transparent icons average to almost nothing - push towards a usable dark tone
        return if (c.alpha < 0.3f) Color(0xFF2A2F45) else c.copy(alpha = 1f)
    }

    companion object {
        /**
         * Cheap blur that runs fine on weak TV GPUs (Android 11 has no RenderEffect):
         * shrink to a few pixels, then scale back up with bilinear filtering.
         */
        fun blurred(src: Bitmap): ImageBitmap {
            val tiny = Bitmap.createScaledBitmap(src, 32, 18, true)
            val mid = Bitmap.createScaledBitmap(tiny, 96, 54, true)
            val small = Bitmap.createScaledBitmap(mid, 24, 14, true)
            return Bitmap.createScaledBitmap(small, 320, 180, true).asImageBitmap()
        }
    }
}
