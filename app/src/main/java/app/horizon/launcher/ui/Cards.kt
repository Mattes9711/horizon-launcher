package app.horizon.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.horizon.launcher.data.AppEntry

enum class Label { NONE, FOCUSED, ALWAYS }

private val CardSpring = spring<Float>(dampingRatio = 0.75f, stiffness = 600f)

/**
 * An app card: banner (or icon on the app's colour), lifts and grows a little when focused.
 * Width null = fill the available width (grids).
 */
@Composable
fun AppCard(
    vm: HomeViewModel,
    app: AppEntry,
    key: String,
    width: Dp?,
    height: Dp,
    radius: Dp,
    label: Label,
    big: Boolean = false,
    autofocus: Boolean = false,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, CardSpring, label = "cardScale")
    val labelAlpha by animateFloatAsState(
        when (label) {
            Label.NONE -> 0f
            Label.ALWAYS -> if (focused) 1f else 0.72f
            Label.FOCUSED -> if (focused) 1f else 0f
        }, tween(160), label = "cardLabel",
    )
    // the big "Zuletzt" cards load sharper artwork on demand (small LRU cache in the view model)
    val art by if (big) {
        produceState(app.banner, app.pkg) { vm.bigArtFor(app)?.let { value = it } }
    } else {
        remember(app) { mutableStateOf(app.banner) }
    }
    val shape = RoundedCornerShape(radius)
    Column(Modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())) {
        Box(
            Modifier
                .then(if (width != null) Modifier.size(width, height) else Modifier.fillMaxWidth().height(height))
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = (scale - 1f) / 0.07f * 10.dp.toPx()
                    this.shape = shape
                    clip = true
                }
                .remembered(vm, key, autofocus, onFocus)
                .onFocusChanged { focused = it.isFocused }
                .tvKeys(onClick = onClick, onMenu = onMenu)
                .focusable()
                .background(app.accent)
                .drawWithContent {
                    drawContent()
                    // unfocused cards a touch darker, so the focused one stands out
                    if (!focused) drawRect(Color.Black.copy(alpha = 0.10f))
                },
            contentAlignment = Alignment.Center,
        ) {
            val img = art
            when {
                img != null -> Image(img, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                app.icon != null -> Image(app.icon, app.label, modifier = Modifier.size(height * 0.5f))
                else -> Txt(app.label, size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, maxLines = 2)
            }
        }
        if (label != Label.NONE) {
            Txt(
                app.label,
                size = if (big) 12.sp else 11.sp,
                weight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 3.dp, top = if (focused) 13.dp else 9.dp)
                    .graphicsLayer { alpha = labelAlpha },
            )
        }
    }
}

/** Live TV / HDMI card, drawn in Compose (no bitmap): white when focused, like the design. */
@Composable
fun InputCard(
    vm: HomeViewModel,
    input: AppEntry,
    key: String,
    width: Dp?,
    height: Dp,
    caption: String?,
    autofocus: Boolean = false,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, CardSpring, label = "inputScale")
    val ink = if (focused) Color(0xFF18181B) else Color.White
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())) {
        Column(
            Modifier
                .then(if (width != null) Modifier.size(width, height) else Modifier.fillMaxWidth().height(height))
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = (scale - 1f) / 0.05f * 10.dp.toPx()
                    this.shape = shape
                    clip = true
                }
                .remembered(vm, key, autofocus, onFocus)
                .onFocusChanged { focused = it.isFocused }
                .tvKeys(onClick = onClick, onMenu = onMenu)
                .focusable()
                .background(if (focused) Color(0xFFF2F2F0) else Color(0xFF23262D)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            GlyphIcon(if (input.pkg == app.horizon.launcher.data.LIVE_TV_ID) Glyph.INPUT else Glyph.HDMI, ink, if (height > 100.dp) 40.dp else 26.dp)
            Txt(input.label, size = if (height > 100.dp) 18.sp else 13.sp, weight = FontWeight.Bold, color = ink)
        }
        if (caption != null) {
            Txt(
                caption, size = 11.sp, weight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                color = Color.White.copy(alpha = if (focused) 1f else 0.7f),
                modifier = Modifier.padding(start = 3.dp, top = if (focused) 13.dp else 9.dp),
            )
        }
    }
}
