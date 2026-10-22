package app.horizon.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import androidx.core.content.res.ResourcesCompat
import app.horizon.launcher.R
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SETTINGS_ID = "__settings"
const val INPUT_PREFIX = "input:"

/** One launchable app, with bitmaps pre-rendered off the main thread. */
class AppEntry(
    val pkg: String,
    val label: String,
    val launch: Intent,
    /** Real Android TV banner (16:9), if the app has one. */
    val banner: ImageBitmap?,
    /** Icon, only used when there is no banner. */
    val icon: ImageBitmap?,
    /** Average colour of the artwork, used for fallback tiles and backgrounds. */
    val accent: Color,
    /** Original drawable (banner or icon), rendered bigger on demand, e.g. for the App Switcher. */
    val source: Drawable?,
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
        for (input in hdmiInputs()) result[input.pkg] = input
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
        // 320x180 px: sharp at the focused (1.1x) tile size on a 1080p TV
        val banner = bannerD?.let { renderDrawable(it, 320, 180) }
        val icon = if (banner == null) iconD?.let { renderDrawable(it, 144, 144) } else null
        val src = banner ?: icon
        val accent = src?.let { averageColor(it) } ?: Color(0xFF2A2F45)
        return AppEntry(
            pkg = pkg,
            label = label,
            launch = launch,
            banner = banner?.asImageBitmap(),
            icon = icon?.asImageBitmap(),
            accent = accent,
            source = bannerD ?: iconD,
        )
    }

    /** HDMI ports as tiles (CleanFrame idea): OK switches the TV straight to that input. */
    private fun hdmiInputs(): List<AppEntry> {
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        val inputs = runCatching { tim.tvInputList }.getOrDefault(emptyList())
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !runCatching { it.isHidden(context) }.getOrDefault(false) }
        return inputs.mapIndexed { i, info ->
            val label = (runCatching { info.loadCustomLabel(context) }.getOrNull() ?: runCatching { info.loadLabel(context) }.getOrNull())
                ?.toString()?.takeIf { it.isNotBlank() } ?: "HDMI ${i + 1}"
            AppEntry(
                pkg = INPUT_PREFIX + info.id,
                label = label,
                launch = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                banner = inputBanner(label).asImageBitmap(),
                icon = null,
                accent = Color(0xFF2B3140),
                source = null,
            )
        }
    }

    private fun inputBanner(label: String): Bitmap {
        val w = 320
        val h = 180
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF3B4254.toInt(), 0xFF1B1F28.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = 0xFFFFFFFF.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.strokeJoin = Paint.Join.ROUND
        // HDMI plug outline
        val port = Path().apply {
            moveTo(118f, 46f); lineTo(202f, 46f); lineTo(202f, 66f); lineTo(190f, 80f)
            lineTo(130f, 80f); lineTo(118f, 66f); close()
        }
        c.drawPath(port, p)
        p.style = Paint.Style.FILL
        p.typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
        p.textAlign = Paint.Align.CENTER
        p.textSize = 30f
        while (p.measureText(label) > w - 30 && p.textSize > 14f) p.textSize -= 2f
        c.drawText(label, w / 2f, 134f, p)
        return b
    }

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
    }
}
