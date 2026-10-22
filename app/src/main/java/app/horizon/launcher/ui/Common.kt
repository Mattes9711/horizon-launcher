package app.horizon.launcher.ui

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/** Time of the last D-pad press: the focus click sound only plays for real moves. */
object Motion {
    var lastMoveAt = 0L
        private set

    fun record(keyCode: Int) {
        when (keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> lastMoveAt = SystemClock.uptimeMillis()
        }
    }
}

internal fun Key.isOk() = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

/**
 * Remote keys for a focusable element: OK = [onClick]; holding OK, MENU or Play/Pause = [onMenu].
 * Only a press that started on this element counts (no stray clicks from the press that opened it).
 */
fun Modifier.tvKeys(
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
    onOther: ((KeyEvent) -> Boolean)? = null,
): Modifier = composed {
    var armed by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    onKeyEvent { e ->
        if (onOther?.invoke(e) == true) return@onKeyEvent true
        val down = e.type == KeyEventType.KeyDown
        when {
            e.key.isOk() && down -> {
                val repeat = e.nativeKeyEvent.repeatCount
                if (repeat == 0) {
                    armed = true; longFired = false
                } else if (armed && !longFired && onMenu != null) {
                    longFired = true; onMenu()
                }
                true
            }
            e.key.isOk() -> {
                if (armed && !longFired) {
                    Sounds.tock(); onClick()
                }
                armed = false; longFired = false
                true
            }
            (e.key == Key.Menu || e.key == Key.MediaPlayPause) && onMenu != null -> {
                if (!down) onMenu()
                true
            }
            else -> false
        }
    }
}

/**
 * Remembers focus by [key]: the element takes focus again whenever the view model asks for that key
 * (coming back from an app, closing a menu, ...). Put it before `focusable()`.
 */
@Composable
fun Modifier.remembered(vm: HomeViewModel, key: String, autofocus: Boolean = false, onFocus: (() -> Unit)? = null): Modifier {
    val requester = remember { FocusRequester() }
    val tick by vm.restoreTick.collectAsStateWithLifecycle()
    LaunchedEffect(tick) {
        val want = vm.focusKey
        if (want == key || (want == null && autofocus)) {
            delay(30)
            runCatching { requester.requestFocus() }
        }
    }
    return this
        .focusRequester(requester)
        .onFocusChanged {
            if (it.isFocused) {
                vm.focusKey = key
                Sounds.tick()
                onFocus?.invoke()
            }
        }
}
