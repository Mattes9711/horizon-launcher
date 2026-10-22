package app.horizon.launcher.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Keeps the AirPlay receiver (separate open-source app) reachable after the TV is switched on.
 * It normally keeps running in the background; if the iPhone could not see "Smart-TV" on the network,
 * the receiver is started once and Smart-TV comes straight back to the front.
 */
class AirPlayKeeper(private val context: Context, private val backHome: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    fun check() {
        if (running) return
        val launch = context.packageManager.getLeanbackLaunchIntentForPackage(PKG)
            ?: context.packageManager.getLaunchIntentForPackage(PKG) ?: return
        if (!online()) return
        running = true
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        var found = false
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName.contains(NAME, ignoreCase = true)) found = true
            }
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        runCatching { nsd.discoverServices("_airplay._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }
        handler.postDelayed({
            runCatching { nsd.stopServiceDiscovery(listener) }
            Log.d(TAG, "airplay receiver visible: $found")
            if (!found) {
                runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                handler.postDelayed({ backHome(); running = false }, 2500)
            } else {
                running = false
            }
        }, 4500)
    }

    private fun online(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)

    fun stop() = handler.removeCallbacksAndMessages(null)

    companion object {
        const val PKG = "io.github.jqssun.airplay"
        const val NAME = "Smart-TV"
        private const val TAG = "SmartTV"
    }
}
