package app.horizon.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import app.horizon.launcher.data.AppEntry
import app.horizon.launcher.data.Kind
import app.horizon.launcher.data.LIVE_TV_ID
import app.horizon.launcher.data.SETTINGS_ID
import app.horizon.launcher.service.HomeKeyService
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Near-black page colour of the design. */
val InkBg = Color(0xFF0D0F14)
private val ContentStart = 96.dp

private typealias MenuAction = Pair<String, () -> Unit>

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val drawerOpen by vm.drawer.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf<Pair<AppEntry, List<MenuAction>>?>(null) }
    fun openMenu(app: AppEntry, actions: List<MenuAction>) {
        menu = app to actions
    }

    BackHandler {
        when {
            screen == Screen.HOME -> vm.goHome()
            else -> {
                vm.show(Screen.HOME); vm.focus(null)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(vm, screen)
        when (screen) {
            Screen.HOME -> HomePage(vm, state, ::openMenu)
            Screen.APPS -> AppsPage(vm, "Alle Apps", state.apps, keyPrefix = "apps", favorites = state.favorites, openMenu = ::openMenu)
            Screen.INPUTS -> InputsPage(vm, state, ::openMenu)
            Screen.AIRPLAY -> AirPlayPage(vm)
            Screen.SETTINGS -> SettingsPage(vm, state.drawer.size)
            Screen.PHOTO -> PhotoPage(vm)
        }
        if (screen != Screen.PHOTO) Sidebar(vm, overPhoto = screen == Screen.HOME && vm.photoBackground.collectAsStateWithLifecycle().value)

        if (drawerOpen) {
            Box(Modifier.fillMaxSize().background(InkBg)) {
                BackHandler { vm.closeDrawer() }
                AppsPage(
                    vm, "Weitere Apps", state.drawer, keyPrefix = "drawer", favorites = state.favorites,
                    openMenu = ::openMenu, drawer = true, start = 56.dp,
                )
            }
        }

        menu?.let { (app, actions) ->
            ContextMenu(app.label, actions, onClose = {
                menu = null
                vm.requestRestore()
            })
        }
    }
}

// ---------------------------------------------------------------------------------------------- background

/**
 * One full-screen image at most: the calm gradient (default) or a photo with the shade baked in.
 * Pages without artwork are plain near-black (cheapest possible).
 */
@Composable
private fun Backdrop(vm: HomeViewModel, screen: Screen) {
    val usePhoto by vm.photoBackground.collectAsStateWithLifecycle()
    val photo by vm.photo.collectAsStateWithLifecycle()
    val gradient = remember { Art.homeGradient().asImageBitmap() }
    val image: ImageBitmap? = when (screen) {
        Screen.HOME -> if (usePhoto) photo ?: gradient else gradient
        Screen.PHOTO -> photo ?: gradient
        Screen.AIRPLAY -> gradient
        else -> null
    }
    if (image == null) {
        Box(Modifier.fillMaxSize().background(InkBg))
    } else {
        Crossfade(targetState = image, animationSpec = tween(400), label = "backdrop") { img ->
            Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------------------------------------------------------------------------------------------- start page

@Composable
private fun HomePage(vm: HomeViewModel, state: HomeState, openMenu: (AppEntry, List<MenuAction>) -> Unit) {
    val context = LocalContext.current
    val recents by vm.recents.collectAsStateWithLifecycle()
    var lowerRow by remember { mutableStateOf(false) }
    // the page slides up when the home-apps row is focused (no recomposition: read in the layer)
    val lift by animateFloatAsState(if (lowerRow) 1f else 0f, spring(dampingRatio = 1f, stiffness = 500f), label = "lift")

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -lift * 190.dp.toPx() }
    ) {
        Column(Modifier.padding(start = ContentStart, top = 40.dp).graphicsLayer { alpha = 1f - lift }) {
            Txt(rememberDate(), size = 11.sp, weight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
            Txt(rememberClock(), size = 57.sp, weight = FontWeight.Light, letterSpacing = (-1.9).sp)
        }

        Txt("Zuletzt", size = 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = ContentStart, top = 264.dp))
        LazyRow(
            contentPadding = PaddingValues(start = ContentStart, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(21.dp),
            modifier = Modifier.padding(top = 292.dp).fillMaxWidth(),
        ) {
            items(recents, key = { "r:" + it.pkg }) { app ->
                AppCard(
                    vm, app, key = "home:recent:${app.pkg}", width = 249.dp, height = 140.dp, radius = 16.dp,
                    label = Label.FOCUSED, big = true, autofocus = app == recents.first(),
                    onFocus = { lowerRow = false },
                    onClick = { launch(context, app) },
                    onMenu = {
                        openMenu(app, buildList<MenuAction> {
                            if (state.favorites.none { it.pkg == app.pkg }) add("Zur Startseite hinzufügen" to { vm.addToHome(app.pkg) })
                            add("App-Info" to { openAppInfo(context, app.pkg) })
                        })
                    },
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(start = ContentStart, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            modifier = Modifier.padding(top = 484.dp).fillMaxWidth(),
        ) {
            items(state.favorites, key = { "f:" + it.pkg }) { app ->
                val idx = state.favorites.indexOf(app)
                val menuActions = {
                    openMenu(app, buildList<MenuAction> {
                        if (idx > 0) add("Nach links" to { vm.moveOnHome(app.pkg, -1) })
                        if (idx < state.favorites.size - 1) add("Nach rechts" to { vm.moveOnHome(app.pkg, 1) })
                        add("Von Startseite entfernen" to { vm.removeFromHome(app.pkg) })
                        if (app.kind == Kind.APP) {
                            add("In „Weitere Apps“" to { vm.hide(app.pkg) })
                            add("Deinstallieren" to { uninstall(context, app.pkg) })
                            add("App-Info" to { openAppInfo(context, app.pkg) })
                        }
                    })
                }
                if (app.kind == Kind.INPUT) {
                    InputCard(
                        vm, app, key = "home:fav:${app.pkg}", width = 132.dp, height = 74.dp, caption = null,
                        onFocus = { lowerRow = true }, onClick = { launch(context, app) }, onMenu = menuActions,
                    )
                } else {
                    AppCard(
                        vm, app, key = "home:fav:${app.pkg}", width = 132.dp, height = 74.dp, radius = 12.dp,
                        label = Label.FOCUSED, onFocus = { lowerRow = true },
                        onClick = { launch(context, app) }, onMenu = menuActions,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- app grids

@Composable
private fun PageTitle(title: String, start: androidx.compose.ui.unit.Dp = ContentStart) {
    Row(
        Modifier.fillMaxWidth().padding(start = start, end = 42.dp, top = 30.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Txt(title, size = 25.sp, weight = FontWeight.Bold, letterSpacing = (-0.4).sp, modifier = Modifier.weight(1f))
        Txt(rememberClock(), size = 13.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun AppsPage(
    vm: HomeViewModel,
    title: String,
    apps: List<AppEntry>,
    keyPrefix: String,
    favorites: List<AppEntry>,
    openMenu: (AppEntry, List<MenuAction>) -> Unit,
    drawer: Boolean = false,
    start: androidx.compose.ui.unit.Dp = ContentStart,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PageTitle(title, start)
        if (apps.isEmpty()) {
            Txt("Keine Apps", size = 15.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(start = start, top = 40.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = start, end = 42.dp, top = 30.dp, bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(21.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps.size, key = { apps[it].pkg }) { i ->
                val app = apps[i]
                val onHome = favorites.any { it.pkg == app.pkg }
                AppCard(
                    vm, app, key = "$keyPrefix:${app.pkg}", width = null, height = 104.dp, radius = 15.dp,
                    label = Label.ALWAYS, autofocus = i == 0,
                    onClick = {
                        if (drawer) vm.closeDrawer()
                        launch(context, app)
                    },
                    onMenu = {
                        openMenu(app, buildList<MenuAction> {
                            if (app.pkg != SETTINGS_ID) {
                                if (drawer) add("In „Alle Apps“ anzeigen" to { vm.showInApps(app.pkg) })
                                add((if (onHome) "Von Startseite entfernen" else "Zur Startseite hinzufügen") to {
                                    if (onHome) vm.removeFromHome(app.pkg) else vm.addToHome(app.pkg)
                                })
                                if (!drawer) add("In „Weitere Apps“" to { vm.hide(app.pkg) })
                                add("Deinstallieren" to { uninstall(context, app.pkg) })
                                add("App-Info" to { openAppInfo(context, app.pkg) })
                            } else add("Öffnen" to { launch(context, app) })
                        })
                    },
                )
            }
        }
    }
}

@Composable
private fun InputsPage(vm: HomeViewModel, state: HomeState, openMenu: (AppEntry, List<MenuAction>) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PageTitle("Eingänge")
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = ContentStart, end = 42.dp, top = 48.dp, bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.inputs.size, key = { state.inputs[it].pkg }) { i ->
                val input = state.inputs[i]
                val onHome = state.favorites.any { it.pkg == input.pkg }
                InputCard(
                    vm, input, key = "inputs:${input.pkg}", width = null, height = 150.dp,
                    caption = if (input.pkg == LIVE_TV_ID) "Antenne · Kabel · Satellit" else null,
                    autofocus = i == 0,
                    onClick = { launch(context, input) },
                    onMenu = {
                        openMenu(input, listOf((if (onHome) "Von Startseite entfernen" else "Zur Startseite hinzufügen") to {
                            if (onHome) vm.removeFromHome(input.pkg) else vm.addToHome(input.pkg)
                        }))
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- AirPlay

@Composable
private fun AirPlayPage(vm: HomeViewModel) {
    val context = LocalContext.current
    val receiver = remember {
        context.packageManager.getLeanbackLaunchIntentForPackage(AIRPLAY_PKG)
            ?: context.packageManager.getLaunchIntentForPackage(AIRPLAY_PKG)
    }
    Box(Modifier.fillMaxSize().background(InkBg.copy(alpha = 0.45f))) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(start = 66.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier
                    .width(99.dp).height(99.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                    .remembered(vm, "airplay:main", autofocus = true)
                    .tvKeys(onClick = { receiver?.let { runCatching { context.startActivity(it) } } })
                    .focusable(),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.AIRPLAY, Color.White, 46.dp) }
            Spacer(Modifier.height(4.dp))
            Txt("Smart-TV", size = 39.sp, weight = FontWeight.Light, letterSpacing = (-1.1).sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(7.dp).height(7.dp).clip(RoundedCornerShape(50)).background(if (receiver != null) Color(0xFF4ADE80) else Color(0xFFF59E0B)))
                Spacer(Modifier.width(8.dp))
                Txt(if (receiver != null) "Bereit für AirPlay" else "Noch nicht installiert", size = 13.sp, color = Color.White.copy(alpha = 0.82f))
            }
            Spacer(Modifier.height(16.dp))
            Txt(
                if (receiver != null) "iPhone: Kontrollzentrum → Bildschirmsynchronisierung → Smart-TV"
                else "Einmal am PC ausführen: ./tv.sh airplay",
                size = 11.sp, color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

const val AIRPLAY_PKG = "io.github.jqssun.airplay"

// ---------------------------------------------------------------------------------------------- settings

@Composable
private fun SettingsPage(vm: HomeViewModel, drawerCount: Int) {
    val context = LocalContext.current
    val photoBg by vm.photoBackground.collectAsStateWithLifecycle()
    val bing by vm.bing.collectAsStateWithLifecycle()
    val sounds by vm.sounds.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(start = ContentStart).width(345.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.width(165.dp).height(165.dp).clip(RoundedCornerShape(39.dp)).background(Color(0xFF1F2229)),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.GEAR, Color(0xFFD6D8DD), 78.dp) }
            Txt("Einstellungen", size = 22.sp, weight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
        }
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = PaddingValues(top = 60.dp, bottom = 60.dp, end = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 480.dp).fillMaxSize(),
        ) {
            item { SectionHeader("Darstellung") }
            item {
                SettingRow(vm, "settings:bg", "Hintergrund", if (photoBg) "Fotos" else "Verlauf", autofocus = true) {
                    vm.setPhotoBackground(!photoBg)
                }
            }
            item {
                SettingRow(vm, "settings:bing", "Foto des Tages", if (bing) "An" else "Aus") { vm.setBing(!bing) }
            }
            item { SettingRow(vm, "settings:sounds", "Klicktöne", if (sounds) "An" else "Aus") { vm.setSounds(!sounds) } }
            item { SectionHeader("Apps") }
            item { SettingRow(vm, "settings:drawer", "Weitere Apps", "$drawerCount") { vm.toggleDrawer() } }
            item { SectionHeader("Fernseher") }
            item { SettingRow(vm, "settings:saver", "Bildschirmschoner", "") { startScreensaver(context) } }
            item {
                SettingRow(vm, "settings:sleep", "Ruhezustand", "") {
                    if (!HomeKeyService.sleep()) startScreensaver(context)
                }
            }
            item {
                SettingRow(vm, "settings:system", "System-Einstellungen", "WLAN, Bild, Ton") {
                    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Txt(text, size = 10.sp, weight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(start = 15.dp, top = 12.dp, bottom = 2.dp))
}

@Composable
private fun SettingRow(vm: HomeViewModel, key: String, title: String, value: String, autofocus: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, spring(stiffness = 600f), label = "rowScale")
    val ink = if (focused) Color(0xFF111318) else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .remembered(vm, key, autofocus)
            .onFocusChanged { focused = it.isFocused }
            .tvKeys(onClick = onClick)
            .focusable()
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(title, size = 13.sp, weight = if (focused) FontWeight.SemiBold else FontWeight.Medium, color = ink, modifier = Modifier.weight(1f))
        Txt(value, size = 13.sp, color = if (focused) Color(0xFF4B4F58) else Color.White.copy(alpha = 0.6f))
    }
}

// ---------------------------------------------------------------------------------------------- photo mode

/** Only the photo and a large, light clock. ◀ ▶ next photo, OK / Back return. */
@Composable
private fun PhotoPage(vm: HomeViewModel) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(30)
        runCatching { requester.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(requester)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyUp) return@onKeyEvent e.key != Key.Back && e.key != Key.Home
                when {
                    e.key == Key.DirectionLeft -> { vm.nextPhoto(-1); true }
                    e.key == Key.DirectionRight -> { vm.nextPhoto(1); true }
                    e.key.isOk() || e.key == Key.DirectionDown || e.key == Key.DirectionUp -> {
                        vm.show(Screen.HOME); vm.focus(null); true
                    }
                    else -> e.key != Key.Back && e.key != Key.Home
                }
            }
            .focusable()
    ) {
        Txt(
            rememberClock(), size = 78.sp, weight = FontWeight.Light, shadow = SoftShadow, letterSpacing = (-2.2).sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 54.dp, bottom = 34.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------- menu

@Composable
private fun ContextMenu(title: String, actions: List<MenuAction>, onClose: () -> Unit) {
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF21C1E25))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                .padding(10.dp)
        ) {
            Txt(title, size = 16.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 8.dp))
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
    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(30)
            runCatching { requester.requestFocus() }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused && !focused) Sounds.tick()
                focused = it.isFocused
            }
            .tvKeys(onClick = onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Txt(text, size = 14.sp, weight = FontWeight.Medium, color = if (focused) Color(0xFF111318) else Color.White)
    }
}

// ---------------------------------------------------------------------------------------------- helpers

@Composable
private fun rememberDate(): String {
    val fmt = remember { SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY) }
    rememberClock() // re-evaluated with the clock
    return fmt.format(Date())
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
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun uninstall(context: Context, pkg: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun startScreensaver(context: Context) {
    val ok = runCatching {
        context.startActivity(
            Intent().setComponent(android.content.ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!ok) runCatching { context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
