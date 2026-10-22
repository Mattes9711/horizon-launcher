package app.horizon.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val Ink = Color(0xFF111318)

private class NavItem(val screen: Screen, val glyph: Glyph, val label: String)

private val Items = listOf(
    NavItem(Screen.HOME, Glyph.HOME, "Start"),
    NavItem(Screen.APPS, Glyph.GRID, "Alle Apps"),
    NavItem(Screen.INPUTS, Glyph.LIVE, "Eingänge"),
    NavItem(Screen.AIRPLAY, Glyph.AIRPLAY, "AirPlay"),
    NavItem(Screen.PHOTO, Glyph.PHOTO, "Fotos"),
)
private val SettingsItem = NavItem(Screen.SETTINGS, Glyph.GEAR, "Einstellungen")

/**
 * Sidebar like the Apple TV app: a slim glass pill with icons; as soon as it has focus it opens
 * with labels and dims the page. Reached with Left from the page, the blue key or holding Home.
 */
@Composable
fun Sidebar(vm: HomeViewModel, overPhoto: Boolean) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val tick by vm.sidebarTick.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val requesters = remember { (Items + SettingsItem).associate { it.screen to FocusRequester() } }

    LaunchedEffect(tick) {
        if (tick > 0) {
            delay(30)
            runCatching { requesters.getValue(if (screen == Screen.PHOTO) Screen.HOME else screen).requestFocus() }
        }
    }

    val width by animateDpAsState(if (open) 195.dp else 48.dp, spring(dampingRatio = 0.9f, stiffness = 700f), label = "sideW")
    val scrim by animateFloatAsState(if (open) 0.42f else 0f, tween(220), label = "scrim")
    val fill by animateColorAsState(
        when {
            open -> Color(0xF01E2028)
            overPhoto -> Color(0x4D14161C)
            else -> Color.White.copy(alpha = 0.07f)
        }, tween(220), label = "sideFill",
    )

    Box(Modifier.fillMaxSize()) {
        if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
        Column(
            Modifier
                .padding(start = 18.dp, top = 18.dp, bottom = 18.dp)
                .width(width)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(fill)
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                .onFocusChanged { open = it.hasFocus }
                .padding(horizontal = 7.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, if (open) Alignment.Top else Alignment.CenterVertically),
        ) {
            if (open) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 12.dp)) {
                    Avatar(27.dp)
                    Spacer(Modifier.width(10.dp))
                    Txt("Smart-TV", size = 12.sp, weight = FontWeight.SemiBold)
                }
            }
            Items.forEach { item ->
                NavButton(vm, item, open, selected = screen == item.screen, requester = requesters.getValue(item.screen))
            }
            if (open) Spacer(Modifier.weight(1f))
            NavButton(vm, SettingsItem, open, selected = screen == Screen.SETTINGS, requester = requesters.getValue(Screen.SETTINGS))
        }
    }
}

@Composable
private fun NavButton(vm: HomeViewModel, item: NavItem, open: Boolean, selected: Boolean, requester: FocusRequester) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val ink = if (focused) Ink else Color.White
    Row(
        Modifier
            .then(if (open) Modifier.fillMaxWidth() else Modifier.size(34.dp))
            .height(if (open) 36.dp else 34.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused && !focused) Sounds.tick()
                focused = it.isFocused
            }
            .tvKeys(onClick = {
                vm.show(item.screen)
                vm.focus(null) // the new page takes focus; the sidebar closes
            })
            .focusable()
            .padding(horizontal = if (open) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (open) Arrangement.Start else Arrangement.Center,
    ) {
        GlyphIcon(item.glyph, ink, 15.dp)
        if (open) {
            Spacer(Modifier.width(11.dp))
            Txt(item.label, size = 12.sp, weight = if (focused) FontWeight.SemiBold else FontWeight.Medium, color = ink)
        }
    }
}

@Composable
fun Avatar(size: Dp) {
    Canvas(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF5B6B8F))
    ) {
        val w = this.size.width
        drawCircle(Color.White.copy(alpha = 0.92f), radius = w * 0.2f, center = Offset(w / 2, w * 0.38f))
        drawCircle(Color.White.copy(alpha = 0.92f), radius = w * 0.34f, center = Offset(w / 2, w * 1.0f))
    }
}
