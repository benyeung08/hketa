package hk.eta.app.ui

import android.content.Context
import hk.eta.app.api.RouteDataStore

/** SharedPreferences for the "線路資料更新" section in Settings. */
object RouteDataPrefs {

    private const val PREF = "hketa_settings"
    private const val KEY_INTERVAL_DAYS = "route_update_interval_days"
    private const val KEY_WIFI_ONLY = "route_update_wifi_only"
    private const val KEY_LAST_CHECK = "route_update_last_check"

    /** 0 = manual only, 1 / 3 / 7 = days between automatic checks. */
    val INTERVAL_DAYS = intArrayOf(0, 1, 3, 7)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun intervalDays(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL_DAYS, 1)

    fun setIntervalDays(context: Context, days: Int) {
        prefs(context).edit().putInt(KEY_INTERVAL_DAYS, days).apply()
    }

    fun intervalIndex(context: Context): Int =
        INTERVAL_DAYS.indexOf(intervalDays(context)).coerceAtLeast(0)

    fun wifiOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun lastCheck(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CHECK, 0L)

    fun markChecked(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    fun isDue(context: Context): Boolean {
        val days = intervalDays(context)
        if (days <= 0) return false
        return System.currentTimeMillis() - lastCheck(context) >= days * 24L * 60L * 60L * 1000L
    }

    fun updateAllowed(context: Context): Boolean =
        !wifiOnly(context) || RouteDataStore.onWifi()
}
