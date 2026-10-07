package com.hketa.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object LocationProvider {

    data class Fix(val lat: Double, val lon: Double, val source: String)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 優先使用 Google 融合定位；沒有 GMS 時退回系統 LocationManager */
    suspend fun current(context: Context): Fix? =
        withContext(Dispatchers.IO) {
            fused(context) ?: system(context)
        }

    private suspend fun fused(context: Context): Fix? = runCatching {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        suspendCancellableCoroutine<Fix?> { cont ->
            try {
                client.lastLocation
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) {
                            cont.resume(loc?.let { Fix(it.latitude, it.longitude, "Fused") })
                        }
                    }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
            } catch (t: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }.getOrNull()

    private fun system(context: Context): Fix? = runCatching {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (p in providers) {
            val loc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            } else {
                @Suppress("DEPRECATION")
                runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            }
            if (loc != null) return Fix(loc.latitude, loc.longitude, "System")
        }
        null
    }.getOrNull()
}
