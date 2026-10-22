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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.horizon.launcher.data.AppEntry
import app.horizon.launcher.data.INPUT_PREFIX
import app.horizon.launcher.data.SETTINGS_ID
import kotlinx.coroutines.delay
import java.util.Date

private val SidePadding = 40.dp
private val TileGap = 22.dp

// Fixed geometry, so the page can be positioned exactly (no automatic scrolling that can jump).
private val DockHeight = 18.dp + TileHeight + TileLabelHeight + 6.dp
private val RowHeight = 18.dp + TileHeight + TileLabelHeight
private val RowPeek = 50.dp      // how much of the 2nd row shows below the dock on the Top Shelf
private val GridTop = 64.dp      // dock position when browsing the grid (below the clock)

private val PageSpring = spring<Float>(dampingRatio = 1f, stiffness = 700f)

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val state by vm.state.collectAsStateWithLifecycle()
    val immersive by vm.immersive.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()
    val restoreTick by vm.restoreTick.collectAsStateWithLifecycle()
    val controlCenter by vm.controlCenter.collectAsStateWithLifecycle()
    val switcher by vm.switcher.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val drawerOpen by vm.drawer.collectAsStateWithLifecycle()

    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    // focus requests: every tile checks whether it is the target when the counter changes
    var focusTarget by remember { mutableStateOf<String?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    fun requestFocusOn(pkg: String?) {
        focusTarget = pkg ?: (state.favorites.firstOrNull() ?: state.others.firstOrNull())?.pkg
        focusTick++
    }

    val tilesEnabled = !immersive && menuFor == null && !controlCenter && switcher == null && !drawerOpen
    val heroSpace = LocalConfiguration.current.screenHeightDp.dp - DockHeight - RowPeek

    // Page position, animated from a coroutine: changing rows never recomposes the grid.
    val scroll = remember { Animatable(0f) }
    LaunchedEffect(heroSpace) {
        vm.focusRow.collect { row ->
            val target = with(density) {
                when {
                    row == DOCK_ROW -> 0f
                    row <= 1 -> (heroSpace - GridTop).toPx()
                    else -> (heroSpace - GridTop + RowHeight * (row - 1)).toPx()
                }
            }
            scroll.animateTo(target, PageSpring)
        }
    }

    LaunchedEffect(state.loaded, homeTick) { if (state.loaded) requestFocusOn(null) }
    LaunchedEffect(restoreTick) { if (state.loaded) requestFocusOn(vm.lastFocusedPkg) }
    // while moving a tile in edit mode, keep focus on it after every layout change
    LaunchedEffect(state, editing) { editing?.let { requestFocusOn(it) } }

    BackHandler(enabled = true) {
        if (editing != null) vm.endEdit() else vm.goHome()
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(vm)

        val pageAlpha by animateFloatAsState(if (immersive) 0f else 1f, tween(280), label = "pageAlpha")
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

            Dock(vm) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    items(state.favorites, key = { it.pkg }) { app ->
                        TileWithFocus(
                            app = app, target = focusTarget, tick = focusTick, enabled = tilesEnabled,
                            editing = editing == app.pkg, vm = vm, row = DOCK_ROW,
                            onMenu = { menuFor = app },
                            onUp = {
                                if (editing == null) {
                                    vm.setImmersive(true); true
                                } else false
                            },
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
                                editing = editing == app.pkg, vm = vm, row = rowIndex,
                                onMenu = { menuFor = app },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }

        TopShelfHint(vm, immersive)
        StatusPill(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 40.dp))
        if (editing != null) EditHint(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp))

        if (immersive) {
            ImmersiveLayer(vm, onExit = { vm.setImmersive(false) })
        }
        if (controlCenter) {
            ControlCenter(vm, hiddenCount = state.hiddenCount, onClose = { vm.closeControlCenter() })
        }
        switcher?.let { s -> AppSwitcher(vm, s) }
        if (drawerOpen) {
            AppDrawer(
                vm = vm, apps = state.drawer, target = focusTarget, tick = focusTick,
                enabled = menuFor == null, onMenu = { menuFor = it },
            )
        }

        menuFor?.let { app ->
            val isFav = state.favorites.any { it.pkg == app.pkg }
            val inDrawer = state.drawer.any { it.pkg == app.pkg }
            ContextMenu(
                app = app,
                actions = buildList<Pair<String, () -> Unit>> {
                    if (inDrawer) {
                        if (app.pkg != SETTINGS_ID) add("Auf Home anzeigen" to { vm.showOnHome(app.pkg) })
                        add("Öffnen" to { launch(context, app) })
                        if (app.pkg != SETTINGS_ID && !app.pkg.startsWith(INPUT_PREFIX)) {
                            add("Deinstallieren" to { uninstall(context, app.pkg) })
                            add("App-Info" to { openAppInfo(context, app.pkg) })
                        }
                    } else if (app.pkg != SETTINGS_ID) {
                        add("Bewegen" to { vm.startEdit(app.pkg) })
                        add((if (isFav) "Aus oberer Reihe entfernen" else "In obere Reihe") to { vm.toggleFavorite(app.pkg) })
                        add("In „Weitere Apps“" to { vm.hide(app.pkg) })
                        if (!app.pkg.startsWith(INPUT_PREFIX)) {
                            add("Deinstallieren" to { uninstall(context, app.pkg) })
                            add("App-Info" to { openAppInfo(context, app.pkg) })
                        }
                    } else {
                        add("Öffnen" to { launch(context, app) })
                    }
                },
                onClose = {
                    menuFor = null
                    requestFocusOn(app.pkg)
                },
            )
        }
    }
}

/** "Weitere Apps": everything kept off the home screen. Yellow key or Control Center; Back closes. */
@Composable
private fun AppDrawer(
    vm: HomeViewModel,
    apps: List<AppEntry>,
    target: String?,
    tick: Int,
    enabled: Boolean,
    onMenu: (AppEntry) -> Unit,
) {
    BackHandler { vm.closeDrawer() }
    val first = apps.firstOrNull()?.pkg
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF00D0F14))
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            contentPadding = PaddingValues(start = SidePadding, end = SidePadding, top = 96.dp, bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps, key = { it.pkg }) { app ->
                TileWithFocus(
                    app = app, target = target, tick = tick, enabled = enabled,
                    editing = false, vm = vm, row = DRAWER_ROW,
                    onMenu = { onMenu(app) },
                    autoFocus = app.pkg == first,
                )
            }
        }
        Column(Modifier.padding(start = 56.dp, top = 34.dp)) {
            Txt("Weitere Apps", size = 30.sp, weight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Txt("MENU: auf Home anzeigen  ·  Zurück: schließen", size = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
        if (apps.isEmpty()) {
            Txt("Keine weiteren Apps", size = 18.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center))
        }
    }
}

private const val DRAWER_ROW = -100

@Composable
private fun TileWithFocus(
    app: AppEntry,
    target: String?,
    tick: Int,
    enabled: Boolean,
    editing: Boolean,
    vm: HomeViewModel,
    row: Int,
    onMenu: () -> Unit,
    onUp: (() -> Boolean)? = null,
    autoFocus: Boolean = false,
) {
    val context = LocalContext.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(tick, enabled) {
        if (enabled && target == app.pkg) {
            delay(30)
            runCatching { requester.requestFocus() }
        }
    }
    LaunchedEffect(Unit) {
        if (autoFocus && enabled) {
            delay(60)
            runCatching { requester.requestFocus() }
        }
    }
    val inDrawer = row == DRAWER_ROW
    AppTile(
        app = app,
        focusRequester = requester,
        enabled = enabled,
        editing = editing,
        onFocused = { if (!inDrawer) vm.onFocus(app, row) },
        onOpen = { if (inDrawer) vm.closeDrawer(); launch(context, app) },
        onLongPress = { if (inDrawer) onMenu() else vm.startEdit(app.pkg) },
        onMenu = onMenu,
        onEditMove = { dir -> vm.editMove(app.pkg, dir) },
        onEditDone = { vm.endEdit() },
        onUp = onUp,
    )
}

/** Glass shelf behind the top row: dark glass over the Top Shelf art, frosted light glass over the wallpaper. */
@Composable
private fun Dock(vm: HomeViewModel, content: @Composable () -> Unit) {
    val row by vm.focusRow.collectAsStateWithLifecycle()
    val light = row != DOCK_ROW
    val shape = RoundedCornerShape(30.dp)
    val fill by animateColorAsState(
        if (light) Color.White.copy(alpha = 0.42f) else Color(0xFF16181D).copy(alpha = 0.40f), tween(300), label = "dockFill"
    )
    val edge by animateColorAsState(
        if (light) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f), tween(300), label = "dockEdge"
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

/**
 * Wallpaper + Top Shelf. Only one full-screen image is drawn when the shelf is fully shown or hidden,
 * which matters a lot on weak TV GPUs.
 */
@Composable
private fun Backdrop(vm: HomeViewModel) {
    val row by vm.focusRow.collectAsStateWithLifecycle()
    val immersive by vm.immersive.collectAsStateWithLifecycle()
    val heroAlpha by animateFloatAsState(
        if (row == DOCK_ROW || immersive) 1f else 0f, tween(260), label = "heroAlpha"
    )
    if (heroAlpha < 1f) WallpaperLayer(vm)
    if (heroAlpha > 0f) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = heroAlpha }) { HeroLayer(vm) }
    }
}

/** Behind the grid: the current photo, frosted (blurred + slightly darkened), or a calm pastel. */
@Composable
private fun WallpaperLayer(vm: HomeViewModel) {
    val photo by vm.photo.collectAsStateWithLifecycle()
    val frosted = photo?.frosted
    if (frosted != null) {
        Crossfade(targetState = frosted, animationSpec = tween(500), label = "frosted") { img ->
            Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    } else {
        val pastel = remember { Art.pastelWallpaper() }
        Image(
            bitmap = pastel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // slight darkening so white labels stay readable, without an extra layer
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.10f), BlendMode.SrcAtop),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun HeroLayer(vm: HomeViewModel) {
    val hero by vm.hero.collectAsStateWithLifecycle()
    val slide by vm.slide.collectAsStateWithLifecycle()
    val immersive by vm.immersive.collectAsStateWithLifecycle()
    val h = hero ?: return

    // auto-advance like the real Top Shelf
    LaunchedEffect(h.app.pkg, h.slides.size, slide) {
        if (h.slides.size > 1) {
            delay(8000)
            vm.nextSlide()
        }
    }

    val current = h.slides.getOrNull(slide)
    Crossfade(targetState = current?.image ?: h.art, animationSpec = tween(450), label = "hero") { img ->
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (current?.image != null || h.artIsPhoto) {
        // photos get the shade the generated art has baked in
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f), 0.35f to Color.Transparent,
                        0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f),
                    )
                )
        )
    }
    // title fades out in photo mode (full screen), like CleanFrame: only photo, time and caption
    val titleAlpha by animateFloatAsState(if (immersive) 0f else 1f, tween(250), label = "title")
    Column(Modifier.padding(start = 56.dp, top = 40.dp, end = 300.dp).graphicsLayer { alpha = titleAlpha }) {
        Txt(h.app.label, size = 54.sp, weight = FontWeight.ExtraBold, shadow = SoftShadow, letterSpacing = (-1).sp)
        current?.caption?.let { Txt(it, size = 18.sp, weight = FontWeight.Medium, shadow = SoftShadow, maxLines = 2) }
    }
    if (h.slides.size > 1) {
        PageDots(
            count = h.slides.size, index = slide,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentHeight(Alignment.Bottom)
                .padding(bottom = if (immersive) 34.dp else DockHeight + RowPeek + 14.dp),
        )
    }
}

@Composable
private fun PageDots(count: Int, index: Int, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .size(if (i == index) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (i == index) 0.95f else 0.45f))
                )
            }
        }
    }
}

/** Small chevron at the top while on the Top Shelf: press Up for full screen. */
@Composable
private fun TopShelfHint(vm: HomeViewModel, immersive: Boolean) {
    val row by vm.focusRow.collectAsStateWithLifecycle()
    val alpha by animateFloatAsState(if (row == DOCK_ROW && !immersive) 0.85f else 0f, tween(250), label = "hint")
    if (alpha == 0f) return
    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(26.dp, 12.dp).graphicsLayer { this.alpha = alpha }) {
            val w = size.width
            val hgt = size.height
            drawLine(Color.White, Offset(w * 0.1f, hgt * 0.85f), Offset(w * 0.5f, hgt * 0.15f), 3.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, Offset(w * 0.5f, hgt * 0.15f), Offset(w * 0.9f, hgt * 0.85f), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun EditHint(modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Txt("Pfeiltasten bewegen  ·  OK fertig  ·  MENU Optionen", size = 13.sp, weight = FontWeight.Medium)
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
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(start = 14.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(format.format(Date(now)), size = 14.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Avatar(26.dp)
    }
}

@Composable
fun Avatar(size: Dp) {
    Canvas(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF6B7AA6))
    ) {
        val w = this.size.width
        drawCircle(Color.White.copy(alpha = 0.92f), radius = w * 0.2f, center = Offset(w / 2, w * 0.38f))
        drawCircle(Color.White.copy(alpha = 0.92f), radius = w * 0.34f, center = Offset(w / 2, w * 1.0f))
    }
}

/** Full-screen Top Shelf: Left/Right flips pages, OK opens the content, Down/Back returns. */
@Composable
private fun ImmersiveLayer(vm: HomeViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    BackHandler { onExit() }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(requester)
            .onKeyEvent { e ->
                val passThrough = e.key == Key.Back || e.key == Key.Home
                if (e.type != KeyEventType.KeyUp) return@onKeyEvent !passThrough
                when {
                    e.key == Key.DirectionDown -> { onExit(); true }
                    e.key == Key.DirectionLeft -> { flip(vm, -1); true }
                    e.key == Key.DirectionRight -> { flip(vm, 1); true }
                    e.key.isOk() -> {
                        val h = vm.hero.value
                        val target = h?.slides?.getOrNull(vm.slide.value)?.intent
                        Sounds.tock()
                        if (target != null) {
                            runCatching { context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        } else if (h != null) {
                            launch(context, h.app)
                        }
                        true
                    }
                    else -> !passThrough
                }
            }
            .focusable()
    ) {
        PhotoModeOverlay(vm)
    }
}

/** Left/Right in full screen: the app's own pages if it has several, otherwise the next photo. */
private fun flip(vm: HomeViewModel, delta: Int) {
    if ((vm.hero.value?.slides?.size ?: 0) > 1) vm.nextSlide(delta) else vm.nextPhoto(delta)
}

/** Photo mode: big quiet clock and the photo caption. */
@Composable
private fun PhotoModeOverlay(vm: HomeViewModel) {
    val context = LocalContext.current
    val photo by vm.photo.collectAsStateWithLifecycle()
    val hero by vm.hero.collectAsStateWithLifecycle()
    val slide by vm.slide.collectAsStateWithLifecycle()
    val format = remember { DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis(); delay(10_000)
        }
    }
    val caption = hero?.slides?.getOrNull(slide)?.caption ?: photo?.caption
    Box(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, bottom = 40.dp)) {
        Column(Modifier.align(Alignment.BottomStart).padding(end = 340.dp)) {
            caption?.let { Txt(it, size = 15.sp, weight = FontWeight.Medium, shadow = SoftShadow, maxLines = 2) }
            Spacer(Modifier.height(6.dp))
            Txt("OK öffnen  ·  ◀ ▶ nächstes Bild  ·  ▼ zurück", size = 12.sp, color = Color.White.copy(alpha = 0.6f), shadow = SoftShadow)
        }
        Txt(
            format.format(Date(now)), size = 64.sp, weight = FontWeight.SemiBold, shadow = SoftShadow,
            letterSpacing = (-1.5).sp, modifier = Modifier.align(Alignment.BottomEnd),
        )
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
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF21C1E25))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                .padding(12.dp)
        ) {
            Txt(app.label, size = 18.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 10.dp))
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
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused && !focused) Sounds.tick()
                focused = it.isFocused
            }
            .onKeyEvent { e ->
                if (e.key.isOk()) {
                    // only a full press that started here (ignores the KeyUp of the press that opened the menu)
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
                        armed = true
                    } else if (e.type == KeyEventType.KeyUp && armed) {
                        armed = false; Sounds.tock(); onClick()
                    }
                    true
                } else false
            }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Txt(text, size = 15.sp, weight = FontWeight.Medium, color = if (focused) Color(0xFF111318) else Color.White)
    }
}

internal fun launch(context: Context, app: AppEntry) {
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

private fun uninstall(context: Context, pkg: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
