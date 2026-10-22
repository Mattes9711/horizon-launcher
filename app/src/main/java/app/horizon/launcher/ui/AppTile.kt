package app.horizon.launcher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.horizon.launcher.data.AppEntry
import kotlinx.coroutines.launch

/** Last D-pad direction, recorded by the activity. Drives the parallax "momentum" tilt. */
object Motion {
    var dx = 0
        private set
    var dy = 0
        private set

    fun record(keyCode: Int) {
        when (keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { dx = -1; dy = 0 }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { dx = 1; dy = 0 }
            AndroidKeyEvent.KEYCODE_DPAD_UP -> { dx = 0; dy = -1 }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { dx = 0; dy = 1 }
        }
    }
}

val TileWidth = 128.dp
val TileHeight = 80.dp
private val TileShape = RoundedCornerShape(14.dp)

internal fun Key.isOk() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

@Composable
fun AppTile(
    app: AppEntry,
    focusRequester: FocusRequester,
    enabled: Boolean,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onUp: (() -> Boolean)? = null,
    width: Dp = TileWidth,
    height: Dp = TileHeight,
) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        if (focused) 1.1f else 1f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "scale"
    )
    val dim by animateFloatAsState(if (focused) 0f else 0.28f, tween(220), label = "dim")
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "label")
    val elevation by animateDpAsState(if (focused) 20.dp else 0.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "lift")
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val sheen = remember { Animatable(-1f) }

    LaunchedEffect(focused) {
        if (focused) {
            val tiltSpring = spring<Float>(dampingRatio = 0.42f, stiffness = 90f)
            launch { tiltY.snapTo(Motion.dx * 8f); tiltY.animateTo(0f, tiltSpring) }
            launch { tiltX.snapTo(-Motion.dy * 8f); tiltX.animateTo(0f, tiltSpring) }
            launch { sheen.snapTo(-0.6f); sheen.animateTo(1.6f, tween(950, easing = FastOutSlowInEasing)) }
        }
    }

    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .size(width, height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationX = tiltX.value
                    rotationY = tiltY.value
                    cameraDistance = 14f * density
                    shadowElevation = elevation.toPx()
                    shape = TileShape
                    clip = true
                }
                .focusRequester(focusRequester)
                .focusProperties { canFocus = enabled }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .onKeyEvent { e ->
                    when {
                        e.key.isOk() && e.type == KeyEventType.KeyDown -> {
                            val repeat = e.nativeKeyEvent.repeatCount
                            if (repeat == 0) longFired = false
                            if (repeat >= 1 && !longFired) {
                                longFired = true
                                onMenu()
                            }
                            true
                        }
                        e.key.isOk() && e.type == KeyEventType.KeyUp -> {
                            if (longFired) longFired = false else onOpen()
                            true
                        }
                        e.key == Key.Menu && e.type == KeyEventType.KeyUp -> {
                            onMenu(); true
                        }
                        e.key == Key.DirectionUp && e.type == KeyEventType.KeyDown && onUp != null -> onUp()
                        else -> false
                    }
                }
                .focusable()
                .background(
                    Brush.linearGradient(listOf(app.accent, app.accent.copy(red = app.accent.red * 0.45f, green = app.accent.green * 0.45f, blue = app.accent.blue * 0.45f)))
                )
                .drawWithContent {
                    drawContent()
                    if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
                    val s = sheen.value
                    if (focused && s > -0.6f && s < 1.6f) {
                        val x = size.width * s
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                                start = Offset(x - size.width * 0.35f, 0f),
                                end = Offset(x + size.width * 0.15f, size.height),
                            )
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                app.banner != null -> Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width, height),
                )
                app.icon != null -> Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(height * 0.55f),
                )
                else -> Text(app.label, color = Color.White, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
        Text(
            text = app.label,
            color = Color.White.copy(alpha = labelAlpha),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
