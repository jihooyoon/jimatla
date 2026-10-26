package ji.minitvlauncher.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

// BluetoothProfile.HID_HOST, public only from API 34; the value has always been 4.
private const val HID_HOST = 4
private val PROFILES = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, HID_HOST)

/**
 * Names of connected Bluetooth accessories other than TV remotes, for the top bar tooltip.
 * Empty without Bluetooth or (Android 12+) without the nearby-devices permission.
 */
@SuppressLint("MissingPermission") // checked in canRead()
fun connectedAccessories(context: Context): Flow<List<String>> = callbackFlow {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    if (adapter == null || !canRead(context)) {
        trySend(emptyList())
        awaitClose()
        return@callbackFlow
    }
    val proxies = HashMap<Int, BluetoothProfile>()

    fun publish() {
        val devices = proxies.values.flatMap { runCatching { it.connectedDevices }.getOrDefault(emptyList()) }
        trySend(devices.distinctBy { it.address }.filterNot(::isRemote).mapNotNull { runCatching { it.name }.getOrNull() }.sorted())
    }

    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            proxies[profile] = proxy
            publish()
        }
        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
            publish()
        }
    }
    PROFILES.forEach { runCatching { adapter.getProfileProxy(context, listener, it) } }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = publish()
    }
    val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    publish()

    awaitClose {
        context.unregisterReceiver(receiver)
        proxies.forEach { (profile, proxy) -> runCatching { adapter.closeProfileProxy(profile, proxy) } }
    }
}.distinctUntilChanged()

private fun canRead(context: Context) =
    Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Peripherals whose minor class says "remote control" (Bluetooth assigned numbers, 0x0C). */
@SuppressLint("MissingPermission")
private fun isRemote(device: BluetoothDevice): Boolean {
    val cls = runCatching { device.bluetoothClass }.getOrNull() ?: return false
    return cls.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL && (cls.deviceClass and 0x3C) == 0x0C
}
