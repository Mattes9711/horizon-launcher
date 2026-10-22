package app.horizon.launcher.ui

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.horizon.launcher.data.AppEntry
import kotlinx.coroutines.launch

/** Last D-pad direction and time, recorded by the activity. Drives the parallax tilt and the focus sound. */
object Motion {
    var dx = 0
        private set
    var dy = 0
        private set
    var lastMoveAt = 0L
        private set

    fun record(keyCode: Int) {
        when (keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { dx = -1; dy = 0 }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { dx = 1; dy = 0 }
            AndroidKeyEvent.KEYCODE_DPAD_UP -> { dx = 0; dy = -1 }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { dx = 0; dy = 1 }
            else -> return
        }
        lastMoveAt = SystemClock.uptimeMillis()
    }
}

enum class Dir { LEFT, RIGHT, UP, DOWN }

val TileWidth = 128.dp
val TileHeight = 80.dp
val TileLabelHeight = 30.dp
private val TileShape = RoundedCornerShape(15.dp)

internal fun Key.isOk() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

private fun Key.toDir(): Dir? = when (this) {
    Key.DirectionLeft -> Dir.LEFT
    Key.DirectionRight -> Dir.RIGHT
    Key.DirectionUp -> Dir.UP
    Key.DirectionDown -> Dir.DOWN
    else -> null
}

@Composable
fun AppTile(
    app: AppEntry,
    focusRequester: FocusRequester,
    enabled: Boolean,
    editing: Boolean,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onMenu: () -> Unit,
    onEditMove: (Dir) -> Unit,
    onEditDone: () -> Unit,
    onUp: (() -> Boolean)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        if (focused || editing) 1.12f else 1f, spring(dampingRatio = 0.68f, stiffness = 520f), label = "scale"
    )
    val dim by animateFloatAsState(if (focused) 0f else 0.12f, tween(180), label = "dim")
    val labelAlpha by animateFloatAsState(if (focused && !editing) 1f else 0f, tween(160), label = "label")
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "lift")
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }

    // edit mode: the tile wiggles like on the big-screen home screens
    val wiggle = if (editing) {
        rememberInfiniteTransition(label = "wiggle").animateFloat(
            initialValue = -1.6f, targetValue = 1.6f,
            animationSpec = infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse),
            label = "wiggleAngle",
        )
    } else null

    LaunchedEffect(focused) {
        if (focused) {
            val tiltSpring = spring<Float>(dampingRatio = 0.45f, stiffness = 110f)
            launch { tiltY.snapTo(Motion.dx * 5f); tiltY.animateTo(0f, tiltSpring) }
            launch { tiltX.snapTo(-Motion.dy * 5f); tiltX.animateTo(0f, tiltSpring) }
        }
    }

    Column(
        modifier = Modifier.width(TileWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(TileWidth, TileHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationX = tiltX.value
                    rotationY = tiltY.value
                    rotationZ = wiggle?.value ?: 0f
                    cameraDistance = 14f * density
                    shadowElevation = lift * 8.dp.toPx()
                    shape = TileShape
                    clip = true
                }
                .focusRequester(focusRequester)
                .focusProperties { canFocus = enabled }
                .onFocusChanged {
                    if (it.isFocused != focused) {
                        focused = it.isFocused
                        if (it.isFocused) {
                            Sounds.tick()
                            onFocused()
                        }
                    }
                }
                .onKeyEvent { e ->
                    val down = e.type == KeyEventType.KeyDown
                    val up = e.type == KeyEventType.KeyUp
                    when {
                        e.key.isOk() && down -> {
                            val repeat = e.nativeKeyEvent.repeatCount
                            if (repeat == 0) longFired = false
                            if (repeat >= 1 && !longFired && !editing) {
                                longFired = true
                                onLongPress()
                            }
                            true
                        }
                        e.key.isOk() && up -> {
                            when {
                                longFired -> longFired = false
                                editing -> onEditDone()
                                else -> { Sounds.tock(); onOpen() }
                            }
                            true
                        }
                        (e.key == Key.Menu || e.key == Key.MediaPlayPause) && up -> {
                            onMenu(); true
                        }
                        editing && e.key.toDir() != null -> {
                            if (down) onEditMove(e.key.toDir()!!)
                            true
                        }
                        e.key == Key.DirectionUp && down && onUp != null -> onUp()
                        else -> false
                    }
                }
                .focusable()
                .background(app.accent)
                .drawWithContent {
                    drawContent()
                    if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                app.banner != null -> Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(TileWidth, TileHeight),
                )
                app.icon != null -> Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(TileHeight * 0.56f),
                )
                else -> Txt(app.label, size = 12.sp, maxLines = 2, align = TextAlign.Center)
            }
        }
        // label only under the focused tile; faded as a layer so its shadow fades too
        Txt(
            text = app.label,
            size = 12.sp,
            weight = FontWeight.Medium,
            shadow = SoftShadow,
            align = TextAlign.Center,
            modifier = Modifier
                .height(TileLabelHeight)
                .padding(top = 13.dp)
                .graphicsLayer { alpha = labelAlpha },
        )
    }
}
