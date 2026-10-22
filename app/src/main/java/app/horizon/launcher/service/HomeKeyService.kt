package app.horizon.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import app.horizon.launcher.MainActivity

/**
 * Takes over the Home key (enabled once with `tv.sh setup`, removed with `tv.sh undo`):
 *  - press        -> Smart-TV home screen (instead of the stock launcher)
 *  - double press -> App Switcher
 *  - hold         -> Control Center
 * If this service is off or crashes, Home simply goes to the stock launcher again.
 */
class HomeKeyService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var downAt = 0L
    private var longFired = false
    private var pendingSingle: Runnable? = null

    private val longPress = Runnable {
        longFired = true
        open(MainActivity.ACTION_CONTROL_CENTER)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "home key service connected")
        // right after boot: show Smart-TV instead of the stock launcher
        if (SystemClock.elapsedRealtime() < 5 * 60 * 1000L) open(MainActivity.ACTION_HOME)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                downAt = SystemClock.uptimeMillis()
                longFired = false
                handler.postDelayed(longPress, LONG_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (!longFired) {
                    val pending = pendingSingle
                    if (pending != null) {
                        // second press within the window -> App Switcher
                        handler.removeCallbacks(pending)
                        pendingSingle = null
                        open(MainActivity.ACTION_SWITCHER)
                    } else {
                        val single = Runnable {
                            pendingSingle = null
                            open(MainActivity.ACTION_HOME)
                        }
                        pendingSingle = single
                        handler.postDelayed(single, DOUBLE_MS)
                    }
                }
            }
        }
        return true // the system never sees Home while this service runs
    }

    private fun open(action: String) {
        val i = Intent(this, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(i) }.onFailure { Log.w(TAG, "open failed", it) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SmartTV"
        private const val LONG_MS = 550L
        private const val DOUBLE_MS = 260L

        @Volatile
        private var instance: HomeKeyService? = null

        val running: Boolean get() = instance != null

        /** Puts the TV to sleep (screen off / standby). Returns false if not possible. */
        fun sleep(): Boolean {
            val s = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }
}
