package app.horizon.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SETTINGS_ID = "__settings"
const val INPUT_PREFIX = "input:"
const val LIVE_TV_ID = "input:__live"

enum class Kind { APP, INPUT }

/**
 * One launchable app or TV input.
 *
 * RAM: only small, tile-sized bitmaps are kept (opaque banners as RGB_565 = half the memory).
 * The original Drawables are NOT kept - they hold the app's full-resolution artwork. Bigger art
 * for the "Zuletzt" cards is rendered on demand and cached in a small LRU (see [bigArt]).
 */
class AppEntry(
    val pkg: String,
    val label: String,
    val launch: Intent,
    val kind: Kind,
    /** TV banner (16:9) at tile size, if the app has one. */
    val banner: ImageBitmap?,
    /** Icon, only for apps without a banner. */
    val icon: ImageBitmap?,
    /** Average colour of the artwork: card background behind icons. */
    val accent: Color,
    /** Activity to load bigger artwork from, on demand. */
    val component: ComponentName?,
)

class AppRepository(private val context: Context) {

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppEntry>()
        val infos =
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        for (ri in infos) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName
            if (pkg == context.packageName || result.containsKey(pkg)) continue
            val launch = (pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: pm.getLaunchIntentForPackage(pkg)
                ?: Intent(Intent.ACTION_MAIN).setClassName(pkg, ai.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // the Drawables are local: rendered to small bitmaps and then dropped
            val bannerD = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull()
            val banner = bannerD?.let { compact(render(it, TILE_W, TILE_H)) }
            val icon = if (banner == null) runCatching { ri.loadIcon(pm) }.getOrNull()?.let { render(it, ICON, ICON) } else null
            val accent = (banner ?: icon)?.let { averageColor(it) } ?: Color(0xFF2A2F45)
            result[pkg] = AppEntry(
                pkg, ri.loadLabel(pm).toString(), launch, Kind.APP,
                banner?.asImageBitmap(), icon?.asImageBitmap(), accent, ComponentName(pkg, ai.name),
            )
        }
        result[SETTINGS_ID] = AppEntry(
            SETTINGS_ID, "System-Einstellungen",
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), Kind.APP,
            null, null, Color(0xFF2A2F45), null,
        )
        result.values.toList()
    }

    /** Live TV plus the HDMI ports. Drawn in Compose - no bitmaps at all. */
    fun inputs(): List<AppEntry> {
        val out = ArrayList<AppEntry>()
        val pm = context.packageManager
        val live = pm.getLeanbackLaunchIntentForPackage("com.mediatek.wwtv.tvcenter")
            ?: pm.getLaunchIntentForPackage("com.mediatek.wwtv.tvcenter")
            ?: Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)
        out += AppEntry(LIVE_TV_ID, "Live-TV", live.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), Kind.INPUT, null, null, Color(0xFF23262D), null)
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return out
        runCatching { tim.tvInputList }.getOrDefault(emptyList())
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !runCatching { it.isHidden(context) }.getOrDefault(false) }
            .forEachIndexed { i, info ->
                val label = (runCatching { info.loadCustomLabel(context) }.getOrNull() ?: runCatching { info.loadLabel(context) }.getOrNull())
                    ?.toString()?.takeIf { it.isNotBlank() } ?: "HDMI ${i + 1}"
                out += AppEntry(
                    INPUT_PREFIX + info.id, label,
                    Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    Kind.INPUT, null, null, Color(0xFF23262D), null,
                )
            }
        return out
    }

    /** Big card artwork (480x270), loaded from the app on demand. */
    fun bigArt(app: AppEntry): ImageBitmap? {
        val c = app.component ?: return null
        val pm = context.packageManager
        val d = runCatching { pm.getActivityBanner(c) ?: pm.getApplicationBanner(c.packageName) }.getOrNull() ?: return null
        return compact(render(d, BIG_W, BIG_H)).asImageBitmap()
    }

    companion object {
        // tile size on a 1080p TV (132 x 74 dp at density 2)
        const val TILE_W = 264
        const val TILE_H = 148
        const val BIG_W = 480
        const val BIG_H = 270
        const val ICON = 96

        fun render(d: Drawable, w: Int, h: Int): Bitmap {
            val copy = d.constantState?.newDrawable()?.mutate() ?: d
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            copy.setBounds(0, 0, w, h)
            copy.draw(Canvas(b))
            return b
        }

        /** Opaque artwork -> RGB_565 (2 bytes per pixel instead of 4). */
        fun compact(b: Bitmap): Bitmap {
            val w = b.width - 1
            val h = b.height - 1
            val opaque = listOf(b.getPixel(0, 0), b.getPixel(w, 0), b.getPixel(0, h), b.getPixel(w, h), b.getPixel(w / 2, h / 2))
                .all { (it ushr 24) == 0xFF }
            if (!opaque) return b
            val small = b.copy(Bitmap.Config.RGB_565, false) ?: return b
            b.recycle()
            return small
        }

        private fun averageColor(b: Bitmap): Color {
            val px = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
            val c = Color(px)
            return if (c.alpha < 0.3f) Color(0xFF2A2F45) else c.copy(alpha = 1f)
        }
    }
}
