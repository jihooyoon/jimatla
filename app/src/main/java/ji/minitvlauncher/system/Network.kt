package ji.minitvlauncher.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetKind { Wifi, WifiWeak, Ethernet, Offline, NoInternet }

/** [name] is the Wi‑Fi SSID when the system reveals it (needs location access), else null. */
data class NetStatus(val kind: NetKind, val name: String? = null)

/** The default network as the top bar shows it, updated as connectivity changes. */
fun networkStatus(context: Context): Flow<NetStatus> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    fun describe(caps: NetworkCapabilities?): NetStatus {
        if (caps == null) return NetStatus(NetKind.Offline)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                NetStatus(if (validated) NetKind.Ethernet else NetKind.NoInternet)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                @Suppress("DEPRECATION") val info = wifi?.connectionInfo
                @Suppress("DEPRECATION") val ssid = info?.ssid?.removeSurrounding("\"")?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.isBlank() }
                @Suppress("DEPRECATION") val level = info?.let { WifiManager.calculateSignalLevel(it.rssi, 4) } ?: 3
                val kind = when {
                    !validated -> NetKind.NoInternet
                    level <= 1 -> NetKind.WifiWeak
                    else -> NetKind.Wifi
                }
                NetStatus(kind, ssid)
            }
            else -> NetStatus(if (validated) NetKind.Wifi else NetKind.NoInternet)
        }
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            trySend(describe(caps))
        }
        override fun onLost(network: Network) {
            trySend(NetStatus(NetKind.Offline))
        }
    }
    trySend(describe(cm.getNetworkCapabilities(cm.activeNetwork)))
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
