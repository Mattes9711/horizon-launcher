package app.horizon.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Calm "ready for AirPlay" screen. The receiver itself runs in the background (separate open-source app);
 * when the iPhone starts mirroring, its picture appears full screen on its own. OK opens the receiver's settings.
 */
@Composable
fun AirPlayScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val installed = remember {
        context.packageManager.getLeanbackLaunchIntentForPackage(AIRPLAY_PKG)
            ?: context.packageManager.getLaunchIntentForPackage(AIRPLAY_PKG)
    }
    val fade = remember { Animatable(0f) }
    val requester = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { requester.requestFocus() }
        fade.animateTo(1f, tween(300))
    }
    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Color.Black.copy(alpha = 0.55f))
            .focusRequester(requester)
            .onKeyEvent { e ->
                if (e.key.isOk()) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) armed = true
                    if (e.type == KeyEventType.KeyUp && armed && installed != null) {
                        armed = false
                        onClose()
                        runCatching { context.startActivity(installed) }
                    }
                    true
                } else false
            }
            .focusable(),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlyphIcon(Glyph.AIRPLAY, Color.White, 54.dp)
            Txt("Smart-TV", size = 39.sp, weight = FontWeight.Light, letterSpacing = (-1.1).sp)
            Txt(
                if (installed != null) "Bereit für AirPlay" else "AirPlay ist noch nicht installiert",
                size = 13.sp, color = Color.White.copy(alpha = 0.78f),
            )
        }
        Txt(
            if (installed != null) "iPhone: Kontrollzentrum → Bildschirmsynchronisierung → Smart-TV"
            else "Einmal am PC ausführen: ./tv.sh airplay",
            size = 11.sp, color = Color.White.copy(alpha = 0.72f), align = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
        )
    }
}
