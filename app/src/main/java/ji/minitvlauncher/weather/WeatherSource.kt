package ji.minitvlauncher.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import ji.minitvlauncher.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Keeps the weather line current: the last result is stored so it shows at once, and a new one is
 * fetched at most hourly, only when the home screen asks (it isn't polled in the background).
 */
class WeatherSource(private val context: Context, private val settings: Settings) {

    fun hasLocationPermission() =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun cached(): Weather? = settings.weather()?.let(Weather::fromJson)

    /** Returns fresh weather if the stored one is over an hour old and the network allows; else the stored one. */
    suspend fun refresh(now: Long, locale: Locale): Weather? = withContext(Dispatchers.IO) {
        val old = cached()
        if (old != null && now - old.fetchedAt < REFRESH_MS) return@withContext old
        val location = lastLocation() ?: freshLocation() ?: return@withContext old
        val place = placeName(location, locale) ?: old?.place
        val fresh = runCatching { fetchWeather(location.latitude, location.longitude, usesFahrenheit(locale), place, now) }.getOrNull()
            ?: return@withContext old
        settings.setWeather(fresh.toJson())
        fresh
    }

    /** The system's last coarse fix: Wi‑Fi based on TVs, no GPS needed. */
    @SuppressLint("MissingPermission")
    private fun lastLocation(): Location? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, "fused")
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /**
     * After a reboot the system remembers no location until some app asks for one, so ask once:
     * a single Wi‑Fi based fix, giving up after 15 s (or at once if location is switched off).
     */
    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(): Location? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, "fused")
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                runCatching {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }.onFailure { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
    }

    private fun placeName(location: Location, locale: Locale): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        @Suppress("DEPRECATION")
        Geocoder(context, locale).getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()

    private companion object {
        const val REFRESH_MS = 60 * 60 * 1000L
    }
}
