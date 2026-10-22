package app.horizon.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import app.horizon.launcher.ui.HomeScreen
import app.horizon.launcher.ui.HomeViewModel
import app.horizon.launcher.ui.Motion
import app.horizon.launcher.ui.Sounds

class MainActivity : ComponentActivity() {

    private lateinit var vm: HomeViewModel

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vm.reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[HomeViewModel::class.java]
        Sounds.init(applicationContext)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        setContent { HomeScreen(vm) }
        // the home screen always paints every pixel itself: skip the window background (one less full-screen fill per frame)
        window.setBackgroundDrawable(null)
        if (savedInstanceState == null) handle(intent, fresh = true)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    /** Coming back from an app, a dialog or the screensaver: put focus back on the last tile. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vm.requestRestore()
    }

    /** Home pressed (system or Home key service) while Smart-TV is already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent, fresh = false)
    }

    private fun handle(intent: Intent?, fresh: Boolean) {
        when (intent?.action) {
            ACTION_CONTROL_CENTER -> vm.openSidebar()
            ACTION_SWITCHER -> vm.goHome()
            else -> if (!fresh) vm.goHome()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            Motion.record(event.keyCode)
            // which keys does the remote send? visible with: adb logcat -s SmartTV
            Log.d(HomeViewModel.TAG, "key ${KeyEvent.keyCodeToString(event.keyCode)} repeat=${event.repeatCount}")
        }
        when (event.keyCode) {
            // blue: sidebar, yellow: "Weitere Apps" (everything kept out of sight)
            KeyEvent.KEYCODE_PROG_BLUE -> {
                if (event.action == KeyEvent.ACTION_UP) vm.openSidebar()
                return true
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                if (event.action == KeyEvent.ACTION_UP) vm.toggleDrawer()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    companion object {
        const val ACTION_HOME = "app.horizon.launcher.HOME"
        const val ACTION_CONTROL_CENTER = "app.horizon.launcher.CONTROL_CENTER"
        const val ACTION_SWITCHER = "app.horizon.launcher.SWITCHER"
    }
}
