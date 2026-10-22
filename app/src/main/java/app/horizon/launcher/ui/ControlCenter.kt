package app.horizon.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Date

private val PanelWidth = 340.dp

/** Side panel sliding in from the right (blue key). Back or blue closes it. */
@Composable
fun ControlCenter(onClose: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val slide = remember { Animatable(1f) } // 1 = hidden to the right, 0 = shown
    LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 500f)) }
    BackHandler { onClose() }

    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var muted by remember { mutableStateOf(audio.isStreamMute(AudioManager.STREAM_MUSIC)) }
    val online = remember { isOnline(context) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - slide.value }
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(PanelWidth)
                .graphicsLayer { translationX = slide.value * with(density) { PanelWidth.toPx() } }
                .padding(top = 24.dp, bottom = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xE6181A21))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header()
            Spacer(Modifier.height(6.dp))

            PanelRow(
                title = if (muted) "Ton aus" else "Lautstärke",
                value = if (muted) "stumm" else "${volume * 100 / maxVolume} %",
                hint = "◀ ▶ ändern · OK stumm",
                autoFocus = true,
                onLeft = {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                },
                onRight = {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                },
                onOk = {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                    muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
                },
            )
            PanelRow("Fernsehen / Quelle", "", "Live-TV und Eingänge", onOk = {
                openFirst(context, listOf(
                    context.packageManager.getLeanbackLaunchIntentForPackage("com.mediatek.wwtv.tvcenter"),
                    context.packageManager.getLaunchIntentForPackage("com.mediatek.wwtv.tvcenter"),
                ))
                onClose()
            })
            PanelRow("WLAN", if (online) "verbunden" else "offline", "Netzwerkeinstellungen", onOk = {
                openFirst(context, listOf(Intent(Settings.ACTION_WIFI_SETTINGS)))
                onClose()
            })
            PanelRow("Bildschirmschoner", "", "jetzt starten", onOk = {
                openFirst(context, listOf(
                    Intent().setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator")),
                    Intent(Settings.ACTION_DREAM_SETTINGS),
                ))
                onClose()
            })
            PanelRow("Einstellungen", "", "Systemeinstellungen", onOk = {
                openFirst(context, listOf(Intent(Settings.ACTION_SETTINGS)))
                onClose()
            })
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
    Column(Modifier.padding(start = 6.dp, top = 4.dp)) {
        Text(timeFormat.format(now), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
        Text(dateFormat.format(now), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
    }
}

@Composable
private fun PanelRow(
    title: String,
    value: String,
    hint: String,
    autoFocus: Boolean = false,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onOk: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(40)
            runCatching { requester.requestFocus() }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                when {
                    e.key.isOk() && e.type == KeyEventType.KeyDown -> {
                        if (e.nativeKeyEvent.repeatCount == 0) armed = true; true
                    }
                    e.key.isOk() && e.type == KeyEventType.KeyUp -> {
                        if (armed) {
                            armed = false; onOk()
                        }
                        true
                    }
                    e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown && onLeft != null -> {
                        onLeft(); true
                    }
                    e.key == Key.DirectionRight && e.type == KeyEventType.KeyDown && onRight != null -> {
                        onRight(); true
                    }
                    // keep focus inside the panel
                    (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) -> true
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val main = if (focused) Color(0xFF111318) else Color.White
        Column(Modifier.weight(1f)) {
            Text(title, color = main, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = main.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        if (value.isNotEmpty()) Text(value, color = main, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

private fun openFirst(context: Context, intents: List<Intent?>) {
    for (i in intents) {
        if (i == null) continue
        val ok = runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (ok) return
    }
}

private fun isOnline(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}.getOrDefault(false)
