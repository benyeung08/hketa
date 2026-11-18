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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
// NOTE: Deferred.await() is a MEMBER of Deferred / CompletableDeferred.
// There is NO top-level `kotlinx.coroutines.await` extension in coroutines-core
// (that name only exists for Google Play Services Task / Java Future, which
// this project deliberately does NOT depend on). Importing it broke compilation.

/**
 * One-shot device location using the Android platform LocationManager.
 * No Google Play Services dependency, so the APK builds on a clean CI runner.
 *
 * IMPORTANT: callers MUST gate on [hasPermission] first. MainActivity refuses
 * to auto-locate and EtaViewModel refuses to query nearby routes until the
 * user has granted ACCESS_FINE/COARSE_LOCATION. The Central fallback below is
 * therefore only reachable for "permission granted but provider unavailable /
 * timed out" — never for "permission not yet granted".
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

        // Defensive only: both callers gate on hasPermission() BEFORE calling
        // current(), so auto-locate never reaches this branch without consent.
        if (!hasPermission(app)) {
            return@withContext Fix(DEFAULT_LAT, DEFAULT_LNG, "未授予定位權限 · 不顯示附近路線")
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

            // Android 34 marks these two params @NonNull, so Kotlin exposes the
            // overridable signature as `String` (NOT the platform type `String!`).
            // Declaring `String?` therefore matches nothing -> "overrides nothing".
            // onStatusChanged has no @NonNull and stays nullable (platform type).
            // Keeping all three overrides also preserves minSdk 24 compatibility,
            // where these callbacks are still abstract interface methods.
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
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

    /** Shared permission gate for both the Activity and the ViewModel. */
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}
