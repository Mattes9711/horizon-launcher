package app.horizon.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.horizon.launcher.data.AppEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardW = 340.dp
private val CardH = 191.dp

/** App Switcher: recently used apps as big cards. OK opens, Up closes the app, Back leaves. */
@Composable
fun AppSwitcher(vm: HomeViewModel, s: SwitcherState) {
    val context = LocalContext.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 500f)) }
    BackHandler { vm.closeSwitcher() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .background(Color(0xF00B0C10)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            s.loading -> Unit
            !s.hasAccess -> Message("Für „Zuletzt benutzt“ einmal am PC ausführen:\n./tv.sh einrichten")
            s.apps.isEmpty() -> Message("Noch keine zuletzt benutzten Apps")
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 310.dp),
                horizontalArrangement = Arrangement.spacedBy(44.dp),
                modifier = Modifier.graphicsLayer { scaleX = 0.9f + 0.1f * enter.value; scaleY = 0.9f + 0.1f * enter.value },
            ) {
                items(s.apps, key = { it.first.pkg }) { (app, art) ->
                    SwitcherCard(
                        app = app, art = art, first = app.pkg == s.apps.first().first.pkg,
                        onOpen = {
                            vm.closeSwitcher()
                            launch(context, app)
                        },
                        onClose = { vm.closeApp(app.pkg) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Txt(text, size = 18.sp, weight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f), align = TextAlign.Center, maxLines = 3)
}

@Composable
private fun SwitcherCard(app: AppEntry, art: ImageBitmap?, first: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val lift = remember { Animatable(0f) }
    val scale by animateFloatAsState(if (focused) 1.08f else 0.94f, spring(dampingRatio = 0.75f, stiffness = 500f), label = "card")
    val dim by animateFloatAsState(if (focused) 0f else 0.35f, tween(200), label = "cardDim")
    LaunchedEffect(Unit) {
        if (first) {
            delay(60)
            runCatching { requester.requestFocus() }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            translationY = -lift.value * with(density) { 420.dp.toPx() }
            alpha = 1f - lift.value
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            app.icon?.let { Image(it, null, Modifier.size(22.dp)) }
            if (app.icon != null) Spacer(Modifier.width(8.dp))
            Txt(app.label, size = 15.sp, weight = FontWeight.SemiBold, color = Color.White.copy(alpha = if (focused) 1f else 0.6f))
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .size(CardW, CardH)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    shadowElevation = if (focused) 24.dp.toPx() else 0f
                    shape = RoundedCornerShape(22.dp)
                    clip = true
                }
                .background(app.accent)
                .focusRequester(requester)
                .onFocusChanged {
                    if (it.isFocused && !focused) Sounds.tick()
                    focused = it.isFocused
                }
                .onKeyEvent { e ->
                    when {
                        e.key.isOk() && e.type == KeyEventType.KeyDown -> {
                            if (e.nativeKeyEvent.repeatCount == 0) armed = true; true
                        }
                        e.key.isOk() && e.type == KeyEventType.KeyUp -> {
                            if (armed) { armed = false; Sounds.tock(); onOpen() }
                            true
                        }
                        e.key == Key.DirectionUp && e.type == KeyEventType.KeyDown -> {
                            scope.launch {
                                lift.animateTo(1f, tween(260))
                                onClose()
                            }
                            true
                        }
                        e.key == Key.DirectionUp || e.key == Key.DirectionDown -> true
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null && app.banner != null) {
                Image(art, app.label, contentScale = ContentScale.Crop, modifier = Modifier.size(CardW, CardH))
            } else {
                (app.icon ?: art)?.let { Image(it, app.label, Modifier.size(84.dp)) }
            }
            Box(Modifier.size(CardW, CardH).background(Color.Black.copy(alpha = dim)))
        }
    }
}
