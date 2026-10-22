package app.horizon.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import app.horizon.launcher.data.AppEntry
import app.horizon.launcher.data.SETTINGS_ID
import kotlinx.coroutines.delay
import java.util.Date

private const val COLUMNS = 6
private val SidePadding = 40.dp
private val TileGap = 22.dp

// Fixed geometry, so the page can be positioned exactly (no automatic scrolling that can jump).
private val DockHeight = 18.dp + TileHeight + TileLabelHeight + 6.dp
private val RowHeight = 18.dp + TileHeight + TileLabelHeight
private val RowPeek = 50.dp      // how much of the 2nd row shows below the dock in hero mode
private val GridTop = 64.dp      // dock position when browsing the grid (below the clock)

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val state by vm.state.collectAsStateWithLifecycle()
    val immersive by vm.immersive.collectAsStateWithLifecycle()
    val focusRow by vm.focusRow.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()
    val restoreTick by vm.restoreTick.collectAsStateWithLifecycle()
    val controlCenter by vm.controlCenter.collectAsStateWithLifecycle()

    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    // focus requests: (package, counter) - every tile checks if it is the target
    var focusTarget by remember { mutableStateOf<String?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    fun requestFocusOn(pkg: String?) {
        focusTarget = pkg ?: (state.favorites.firstOrNull() ?: state.others.firstOrNull())?.pkg
        focusTick++
    }

    val tilesEnabled = !immersive && menuFor == null && !controlCenter
    val atTop = focusRow == DOCK_ROW
    val heroSpace = LocalConfiguration.current.screenHeightDp.dp - DockHeight - RowPeek

    // Page position: hero mode (dock low, big artwork) or grid mode (dock at the top, page slides up).
    val scroll = remember { Animatable(0f) }
    LaunchedEffect(focusRow, heroSpace) {
        val target = with(density) {
            when {
                focusRow == DOCK_ROW -> 0f
                focusRow <= 1 -> (heroSpace - GridTop).toPx()
                else -> (heroSpace - GridTop + RowHeight * (focusRow - 1)).toPx()
            }
        }
        scroll.animateTo(target, spring(dampingRatio = 1f, stiffness = 650f))
    }

    // first load and every Home press: back to the first favourite
    LaunchedEffect(state.loaded, homeTick) {
        if (state.loaded) requestFocusOn(null)
    }
    // window regained focus or the app list was reloaded: put focus back where it was
    LaunchedEffect(restoreTick) {
        if (state.loaded) requestFocusOn(vm.lastFocusedPkg)
    }

    BackHandler(enabled = true) { vm.goHome() }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0D12))) {
        WallpaperLayer(vm)
        HeroLayer(vm, visible = atTop || immersive)

        val pageAlpha by animateFloatAsState(if (immersive) 0f else 1f, tween(350), label = "pageAlpha")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    translationY = -scroll.value
                    alpha = pageAlpha
                },
        ) {
            Spacer(Modifier.height(heroSpace))

            Dock(glassy = !atTop) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    items(state.favorites, key = { it.pkg }) { app ->
                        TileWithFocus(
                            app = app, target = focusTarget, tick = focusTick, enabled = tilesEnabled,
                            onFocused = { vm.onFocus(app, DOCK_ROW) },
                            onOpen = { launch(context, app) },
                            onMenu = { menuFor = app },
                            onUp = { vm.setImmersive(true); true },
                        )
                    }
                }
            }

            state.others.chunked(COLUMNS).forEachIndexed { rowIndex, rowApps ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                    modifier = Modifier.padding(start = SidePadding, end = SidePadding, top = 18.dp),
                ) {
                    rowApps.forEach { app ->
                        key(app.pkg) {
                            TileWithFocus(
                                app = app, target = focusTarget, tick = focusTick, enabled = tilesEnabled,
                                onFocused = { vm.onFocus(app, rowIndex) },
                                onOpen = { launch(context, app) },
                                onMenu = { menuFor = app },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }

        StatusPill(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 40.dp))

        if (immersive) {
            ImmersiveLayer(
                title = "",
                onExit = {
                    vm.setImmersive(false)
                    requestFocusOn(vm.lastFocusedPkg)
                },
            )
        }

        if (controlCenter) {
            ControlCenter(onClose = { vm.closeControlCenter() })
        }

        menuFor?.let { app ->
            val isFav = state.favorites.any { it.pkg == app.pkg }
            val list = if (isFav) state.favorites else state.others
            val idx = list.indexOfFirst { it.pkg == app.pkg }
            ContextMenu(
                app = app,
                actions = buildList<Pair<String, () -> Unit>> {
                    if (app.pkg != SETTINGS_ID) {
                        add((if (isFav) "Aus oberer Reihe entfernen" else "In obere Reihe") to { vm.toggleFavorite(app.pkg) })
                    }
                    if (idx > 0) add("Nach links" to { vm.move(app.pkg, -1) })
                    if (idx >= 0 && idx < list.size - 1 && list[idx + 1].pkg != SETTINGS_ID) {
                        add("Nach rechts" to { vm.move(app.pkg, 1) })
                    }
                    if (app.pkg != SETTINGS_ID) add("App-Info" to { openAppInfo(context, app.pkg) })
                },
                onClose = {
                    menuFor = null
                    requestFocusOn(app.pkg)
                },
            )
        }
    }
}

@Composable
private fun TileWithFocus(
    app: AppEntry,
    target: String?,
    tick: Int,
    enabled: Boolean,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onUp: (() -> Boolean)? = null,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(tick, enabled) {
        if (enabled && target == app.pkg) {
            delay(30)
            runCatching { requester.requestFocus() }
        }
    }
    AppTile(app, requester, enabled, onFocused, onOpen, onMenu, onUp)
}

/** Glass shelf behind the top row: dark glass over artwork, light frosted glass over the wallpaper. */
@Composable
private fun Dock(glassy: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val fill by animateColorAsState(
        if (glassy) Color.White.copy(alpha = 0.38f) else Color(0xFF15171C).copy(alpha = 0.42f), tween(400), label = "dockFill"
    )
    val edge by animateColorAsState(
        if (glassy) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f), tween(400), label = "dockEdge"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(DockHeight)
            .background(fill, shape)
            .border(1.dp, edge, shape)
            .padding(top = 18.dp)
    ) { content() }
}

/** Calm wallpaper behind the grid - the same for every app. A pushed image (tv.sh wallpaper) replaces it. */
@Composable
private fun WallpaperLayer(vm: HomeViewModel) {
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val image = wallpaper
    if (image != null) {
        Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        val pastel = remember {
            Art.render(
                0xFFE3E9F0.toInt(),
                listOf(
                    Art.Blob(0.18f, 0.22f, 0.75f, 0xFFB5D2EE.toInt()),
                    Art.Blob(0.88f, 0.18f, 0.55f, 0xFFF2E1C9.toInt()),
                    Art.Blob(0.62f, 0.95f, 0.65f, 0xFFCFDDEB.toInt()),
                    Art.Blob(0.05f, 0.95f, 0.45f, 0xFFEDE3D6.toInt()),
                ),
            )
        }
        Image(pastel, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
    }
    // keep white labels readable on the light wallpaper
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
}

/** Full-screen artwork for the focused top-row app, with its name like a title card. */
@Composable
private fun HeroLayer(vm: HomeViewModel, visible: Boolean) {
    val hero by vm.hero.collectAsStateWithLifecycle()
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(260), label = "heroAlpha")
    if (alpha == 0f) return
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        Crossfade(targetState = hero, animationSpec = tween(320), label = "hero") { h ->
            Box(Modifier.fillMaxSize()) {
                val image = h?.image
                if (image != null) {
                    Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    GeneratedArt(h?.accent ?: Color(0xFF3A4C78))
                }
                // darker bottom so the dock reads well, light top for the title
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.35f),
                                0.35f to Color.Transparent,
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f),
                            )
                        )
                )
                Text(
                    text = h?.title.orEmpty(),
                    color = Color.White,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 4f), 18f)),
                    modifier = Modifier.padding(start = 56.dp, top = 40.dp),
                )
            }
        }
    }
}

/** Original soft "aurora" artwork in the app's own colours (used until real content art exists). */
@Composable
private fun GeneratedArt(accent: Color) {
    // rendered once per colour into a small bitmap; smooth gradients upscale cleanly and cost the GPU almost nothing
    val art = remember(accent) {
        val p = auroraPalette(accent).map { it.toArgb() }
        Art.render(
            p[0],
            listOf(
                Art.Blob(0.22f, 0.30f, 0.80f, p[1]),
                Art.Blob(0.85f, 0.15f, 0.60f, p[2]),
                Art.Blob(0.70f, 0.85f, 0.70f, p[3]),
            ),
        )
    }
    Image(art, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
}

private fun auroraPalette(accent: Color): List<Color> {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), out)
    fun hsv(h: Float, s: Float, v: Float) =
        Color(android.graphics.Color.HSVToColor(floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
    val h = out[0]
    val s = out[1].coerceAtLeast(0.35f)
    return listOf(
        hsv(h, s * 0.9f, 0.22f),           // deep base
        hsv(h, s, 0.85f),                  // main colour
        hsv(h + 35f, s * 0.7f, 0.95f),     // warm neighbour
        hsv(h - 40f, s * 0.8f, 0.60f),     // cool neighbour
    )
}

private fun DrawScope.blob(cx: Float, cy: Float, r: Float, color: Color) {
    val center = Offset(size.width * cx, size.height * cy)
    val radius = size.width * r
    drawCircle(
        Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

@Composable
private fun StatusPill(modifier: Modifier) {
    val context = LocalContext.current
    val format = remember { DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(start = 14.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(format.format(Date(now)), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        // generic profile avatar (profiles come in phase 7)
        Canvas(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF6B7AA6))
        ) {
            val w = size.width
            drawCircle(Color.White.copy(alpha = 0.92f), radius = w * 0.2f, center = Offset(w / 2, w * 0.38f))
            drawCircle(Color.White.copy(alpha = 0.92f), radius = w * 0.34f, center = Offset(w / 2, w * 1.0f))
        }
    }
}

/** "Full screen" hero: Up from the top row hides the grid. Down, OK or Back bring it back. */
@Composable
private fun ImmersiveLayer(title: String, onExit: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    BackHandler { onExit() }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(requester)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp &&
                    (e.key == Key.DirectionDown || e.key.isOk())
                ) {
                    onExit(); true
                } else e.key != Key.Back && e.key != Key.Home
            }
            .focusable()
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 48.dp)) {
            Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text("▼  zurück zu den Apps", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ContextMenu(app: AppEntry, actions: List<Pair<String, () -> Unit>>, onClose: () -> Unit) {
    BackHandler { onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(340.dp)
                .background(Color(0xF01C1E27), RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                .padding(12.dp)
        ) {
            Text(
                app.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 10.dp),
            )
            actions.forEachIndexed { i, (label, action) ->
                MenuItem(label, autoFocus = i == 0) {
                    action()
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, autoFocus: Boolean, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(30)
            runCatching { requester.requestFocus() }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.key.isOk()) {
                    // only react to a full press that started here (ignores the KeyUp of the long-press that opened the menu)
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) armed = true
                    else if (e.type == KeyEventType.KeyUp && armed) {
                        armed = false; onClick()
                    }
                    true
                } else false
            }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, color = if (focused) Color.Black else Color.White, fontSize = 15.sp)
    }
}

private fun launch(context: Context, app: AppEntry) {
    try {
        context.startActivity(app.launch)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "${app.label} lässt sich nicht öffnen", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "${app.label} lässt sich nicht öffnen", Toast.LENGTH_SHORT).show()
    }
}

private fun openAppInfo(context: Context, pkg: String) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
