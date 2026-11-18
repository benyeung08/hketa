package hk.eta.app.ui

import android.content.Context

/**
 * "定位附近路線嘅距離" now lives in 應用設置, not on the Home page.
 * MainActivity reads the same value on every nearby lookup; when the user
 * changes it in Settings and returns, onResume() reloads nearby routes.
 */
object NearbyRadiusPrefs {

    private const val PREF = "hketa_settings"
    private const val KEY_RADIUS_METERS = "nearby_radius_meters"

    /** 50 m is the extra small radius shown in the reference screenshot. */
    val RADIUS_METERS = intArrayOf(50, 100, 200, 300, 500, 1000, 2000)

    private const val DEFAULT_METERS = 500

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun radiusMeters(context: Context): Int =
        prefs(context).getInt(KEY_RADIUS_METERS, DEFAULT_METERS)

    fun setRadiusMeters(context: Context, meters: Int) {
        prefs(context).edit().putInt(KEY_RADIUS_METERS, meters).apply()
    }

    fun radiusIndex(context: Context): Int =
        RADIUS_METERS.indexOf(radiusMeters(context)).coerceAtLeast(0)
}
