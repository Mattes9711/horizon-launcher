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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val immersive by vm.immersive.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()

    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    // focus requests: (package, counter) - every tile checks if it is the target
    var focusTarget by remember { mutableStateOf<String?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    fun requestFocusOn(pkg: String?) {
        focusTarget = pkg; focusTick++
    }

    val listState = rememberLazyListState()
    val tilesEnabled = !immersive && menuFor == null

    // first load and every Home press: jump to the first favourite
    LaunchedEffect(state.loaded, homeTick) {
        if (state.loaded) {
            listState.scrollToItem(0)
            requestFocusOn((state.favorites.firstOrNull() ?: state.others.firstOrNull())?.pkg)
        }
    }

    BackHandler(enabled = true) { vm.goHome() }

    Box(Modifier.fillMaxSize().background(Color(0xFF07080C))) {
        // Background and shade read their own state, so a focus change does not recompose the grid.
        HeroBackground(vm)
        Shade(vm, immersive)

        val gridAlpha by animateFloatAsState(if (immersive) 0f else 1f, spring(stiffness = Spring.StiffnessLow), label = "gridAlpha")
        val gridShift by animateDpAsState(if (immersive) 140.dp else 0.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "gridShift")

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = gridAlpha
                    translationY = gridShift.toPx()
                },
        ) {
            item(key = "hero-space") { Spacer(Modifier.height(292.dp)) }

            item(key = "dock") {
                Dock {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(TileGap),
                    ) {
                        items(state.favorites, key = { it.pkg }) { app ->
                            TileWithFocus(
                                app = app, target = focusTarget, tick = focusTick, enabled = tilesEnabled,
                                onFocused = { vm.onFocus(app, inTopRow = true) },
                                onOpen = { launch(context, app) },
                                onMenu = { menuFor = app },
                                onUp = { vm.setImmersive(true); true },
                            )
                        }
                    }
                }
            }

            val rows = state.others.chunked(COLUMNS)
            items(rows.size, key = { "row$it" }) { index ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                    modifier = Modifier.padding(start = SidePadding, end = SidePadding, top = 26.dp),
                ) {
                    rows[index].forEach { app ->
                        TileWithFocus(
                            app = app, target = focusTarget, tick = focusTick, enabled = tilesEnabled,
                            onFocused = { vm.onFocus(app, inTopRow = false) },
                            onOpen = { launch(context, app) },
                            onMenu = { menuFor = app },
                        )
                    }
                }
            }
        }

        StatusPill(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 40.dp))

        if (immersive) {
            ImmersiveLayer(
                title = state.favorites.firstOrNull { it.pkg == vm.focusedPkg }?.label.orEmpty(),
                onExit = {
                    vm.setImmersive(false)
                    requestFocusOn(vm.focusedPkg)
                },
            )
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

/** The translucent "glass" shelf behind the top row (real blur needs Android 12+, so it is tinted glass). */
@Composable
private fun Dock(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(Color.White.copy(alpha = 0.10f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(top = 18.dp, bottom = 4.dp)
    ) { content() }
}

@Composable
private fun Shade(vm: HomeViewModel, immersive: Boolean) {
    val inTopRow by vm.focusInTopRow.collectAsStateWithLifecycle()
    // darker while browsing the lower rows
    val shade by animateFloatAsState(if (inTopRow || immersive) 0f else 0.45f, tween(400), label = "shade")
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.30f),
                    0.38f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.92f),
                )
            )
            .background(Color.Black.copy(alpha = shade))
    )
}

@Composable
private fun HeroBackground(vm: HomeViewModel) {
    val backdrop by vm.backdrop.collectAsStateWithLifecycle()
    Crossfade(targetState = backdrop, animationSpec = tween(550), label = "hero") { b ->
        when {
            b == null -> Box(Modifier.fillMaxSize())
            b.image != null -> Image(
                bitmap = b.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(b.accent.copy(alpha = 0.9f), Color(0xFF07080C)),
                            center = Offset(500f, 200f),
                            radius = 1400f,
                        )
                    )
            )
        }
    }
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
