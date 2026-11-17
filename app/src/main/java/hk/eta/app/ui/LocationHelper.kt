package hk.eta.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot device location using the Android platform LocationManager.
 * No Google Play Services dependency, so the APK builds on a clean CI runner.
 * If permission / provider / fresh fix is unavailable, falls back to Central, HK.
 */
object LocationHelper {

    const val DEFAULT_LAT = 22.2817
    const val DEFAULT_LNG = 114.1583

    data class Fix(val lat: Double, val lng: Double, val source: String)

    private const val MAX_LAST_FIX_AGE_MS = 2 * 60 * 1000L
    private const val FRESH_FIX_TIMEOUT_MS = 9_000L

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    suspend fun current(context: Context): Fix = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext Fix(DEFAULT_LAT, DEFAULT_LNG, "預設位置（中環）")

        if (!hasPermission(app)) {
            return@withContext Fix(DEFAULT_LAT, DEFAULT_LNG, "未授予定位權限 · 預設位置（中環）")
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        val last = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        if (last != null && System.currentTimeMillis() - last.time < MAX_LAST_FIX_AGE_MS) {
            return@withContext Fix(last.latitude, last.longitude, "裝置定位")
        }
        if (providers.isEmpty()) {
            return@withContext if (last != null) {
                Fix(last.latitude, last.longitude, "上次定位（較舊）")
            } else {
                Fix(DEFAULT_LAT, DEFAULT_LNG, "定位服務關閉 · 預設位置（中環）")
            }
        }

        val deferred = CompletableDeferred<Location>()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!deferred.isCompleted) {
                    deferred.complete(location)
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String?) = Unit
            override fun onProviderDisabled(provider: String?) = Unit
        }

        withContext(Dispatchers.Main) {
            runCatching {
                manager.requestSingleUpdate(providers[0], listener, Looper.getMainLooper())
            }
        }
        val fresh = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) { deferred.await() }
        withContext(Dispatchers.Main) {
            runCatching { manager.removeUpdates(listener) }
        }

        when {
            fresh != null -> Fix(fresh.latitude, fresh.longitude, "裝置定位")
            last != null -> Fix(last.latitude, last.longitude, "上次定位（較舊）")
            else -> Fix(DEFAULT_LAT, DEFAULT_LNG, "定位逾時 · 預設位置（中環）")
        }
    }

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}
