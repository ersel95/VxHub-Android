package com.vxhub.internal.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.vxhub.VxConnectionType
import com.vxhub.internal.VxLog

/**
 * Follows the default network — the Android counterpart of iOS's `VxReachabilityManager`, without
 * the start/stop bookkeeping: it runs for the life of the process.
 */
internal class ConnectivityMonitor(
    context: Context,
    private val onChange: (isConnected: Boolean, type: VxConnectionType) -> Unit,
) {
    private val manager: ConnectivityManager? = context.getSystemService()
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            report(capabilities)
        }

        override fun onLost(network: Network) {
            onChange(false, VxConnectionType.NONE)
        }

        override fun onUnavailable() {
            onChange(false, VxConnectionType.NONE)
        }
    }

    fun start() {
        val cm = manager ?: return
        if (registered) return
        try {
            cm.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (e: SecurityException) {
            VxLog.w("ACCESS_NETWORK_STATE missing; connectivity is not tracked")
        } catch (e: RuntimeException) {
            // Some OEM builds throw when an app holds too many callbacks.
            VxLog.w("Connectivity callback could not be registered: ${e.message}")
        }
        val current = cm.activeNetwork?.let { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
        if (current != null) report(current) else onChange(false, VxConnectionType.NONE)
    }

    private fun report(capabilities: NetworkCapabilities) {
        val connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val type = when {
            !connected -> VxConnectionType.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> VxConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> VxConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> VxConnectionType.ETHERNET
            else -> VxConnectionType.OTHER
        }
        onChange(connected, type)
    }
}
