package app.horizon.launcher.ui

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.horizon.launcher.service.HomeKeyService
import kotlinx.coroutines.delay
import java.util.Date

private val PanelWidth = 420.dp
private val Ink = Color(0xFF111318)

/** Control Center: panel in the top-right corner (long-press Home or blue key). Back closes it. */
@Composable
fun ControlCenter(vm: HomeViewModel, hiddenCount: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val slide = remember { Animatable(1f) } // 1 = hidden, 0 = shown
    LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 600f)) }
    BackHandler { onClose() }

    val sounds by vm.sounds.collectAsStateWithLifecycle()
    val nm = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    var dnd by remember { mutableStateOf(nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) }
    val online = remember { isOnline(context) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - slide.value }
            .background(Color.Black.copy(alpha = 0.30f))
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 26.dp)
                .width(PanelWidth)
                .graphicsLayer {
                    translationY = -slide.value * with(density) { 60.dp.toPx() }
                    translationX = slide.value * with(density) { 40.dp.toPx() }
                }
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xEB1B1D23))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(30.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header()
            VolumeRow()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcButton(Glyph.POWER, "Ruhezustand", Modifier.weight(1f)) {
                    onClose()
                    if (!HomeKeyService.sleep()) startScreensaver(context)
                }
                CcButton(Glyph.MOON, "Nicht stören", Modifier.weight(1f), active = dnd) {
                    if (nm.isNotificationPolicyAccessGranted) {
                        nm.setInterruptionFilter(
                            if (dnd) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        )
                        dnd = !dnd
                    } else {
                        Toast.makeText(context, "Einmal am PC ./tv.sh einrichten ausführen", Toast.LENGTH_LONG).show()
                    }
                }
                CcButton(Glyph.NOTE, "Klicktöne", Modifier.weight(1f), active = sounds) { vm.setSounds(!sounds) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcButton(Glyph.SPARKLE, "Bildschirm-\nschoner", Modifier.weight(1f)) {
                    onClose(); startScreensaver(context)
                }
                CcButton(Glyph.INPUT, "Quelle", Modifier.weight(1f)) {
                    onClose(); openLiveTv(context)
                }
                CcButton(Glyph.WIFI, if (online) "WLAN\nverbunden" else "WLAN\noffline", Modifier.weight(1f)) {
                    onClose(); open(context, Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcButton(Glyph.CARDS, "Zuletzt\nbenutzt", Modifier.weight(1f)) { vm.toggleSwitcher() }
                CcButton(Glyph.EYE, if (hiddenCount > 0) "$hiddenCount Apps\neinblenden" else "Keine aus-\ngeblendet", Modifier.weight(1f)) {
                    vm.unhideAll()
                }
                CcButton(Glyph.GEAR, "Einstellungen", Modifier.weight(1f)) {
                    onClose(); open(context, Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
    }
}

@Composable
private fun Header() {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val dateFormat = remember { DateFormat.getLongDateFormat(context) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date(); delay(10_000)
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(timeFormat.format(now), size = 34.sp, weight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
            Txt(dateFormat.format(now), size = 13.sp, color = Color.White.copy(alpha = 0.65f))
        }
        Avatar(40.dp)
    }
}

@Composable
private fun VolumeRow() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var muted by remember { mutableStateOf(audio.isStreamMute(AudioManager.STREAM_MUSIC)) }
    val fill by animateFloatAsState(if (muted) 0f else volume / max.toFloat(), tween(120), label = "vol")
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        runCatching { requester.requestFocus() }
    }
    fun change(dir: Int) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        Sounds.tick()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f))
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                when {
                    e.key == Key.DirectionLeft -> { if (down) change(AudioManager.ADJUST_LOWER); true }
                    e.key == Key.DirectionRight -> { if (down) change(AudioManager.ADJUST_RAISE); true }
                    e.key.isOk() && e.type == KeyEventType.KeyUp -> {
                        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC); true
                    }
                    e.key.isOk() -> true
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (focused) Ink else Color.White
        GlyphIcon(Glyph.SPEAKER, ink, 22.dp)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ink.copy(alpha = 0.18f))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fill.coerceIn(0f, 1f)).background(ink))
        }
        Spacer(Modifier.width(12.dp))
        Txt(if (muted) "stumm" else "${volume * 100 / max}%", size = 13.sp, weight = FontWeight.SemiBold, color = ink, modifier = Modifier.width(44.dp), align = TextAlign.End)
    }
}

@Composable
private fun CcButton(glyph: Glyph, label: String, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "ccScale")
    val bg = when {
        focused -> Color.White
        active -> Color(0xFF3B82F6)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val ink = if (focused) Ink else Color.White
    Column(
        modifier
            .height(92.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .onFocusChanged {
                if (it.isFocused && !focused) Sounds.tick()
                focused = it.isFocused
            }
            .onKeyEvent { e ->
                if (!e.key.isOk()) return@onKeyEvent false
                if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) armed = true
                if (e.type == KeyEventType.KeyUp && armed) {
                    armed = false; Sounds.tock(); onClick()
                }
                true
            }
            .focusable()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        GlyphIcon(glyph, if (active && !focused) Color.White else ink, 24.dp)
        Txt(label, size = 11.sp, weight = FontWeight.SemiBold, color = ink, maxLines = 2)
    }
}

private fun open(context: Context, intent: Intent): Boolean =
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

fun startScreensaver(context: Context) {
    if (!open(context, Intent().setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator")))) {
        open(context, Intent(Settings.ACTION_DREAM_SETTINGS))
    }
}

private fun openLiveTv(context: Context) {
    val pm = context.packageManager
    val candidates = listOfNotNull(
        pm.getLeanbackLaunchIntentForPackage("com.mediatek.wwtv.tvcenter"),
        pm.getLaunchIntentForPackage("com.mediatek.wwtv.tvcenter"),
        Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.Channels.CONTENT_URI),
    )
    for (i in candidates) if (open(context, i)) return
}

private fun isOnline(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}.getOrDefault(false)
