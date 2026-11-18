package hk.eta.app.ui

import android.content.Context
import hk.eta.app.R
import hk.eta.app.api.TimeUtils

/**
 * User-facing "ETA 自動刷新" settings.
 *
 * Before this change the app polled ONLY the currently selected stop on a
 * hard-coded 30-second timer. Nearby-route cards, favourites and the route
 * detail screen never refreshed on their own. This object is the single
 * source of truth for both the on/off switch and the interval; every screen
 * reads it on resume so a change in 應用程式設置 takes effect immediately.
 *
 * Honest scope: refresh runs while the app is in the FOREGROUND only. There
 * is no background service, no WorkManager job and no silent polling after
 * the user leaves the app.
 */
object EtaRefreshPrefs {

    private const val PREF = "hketa_settings"
    private const val KEY_ENABLED = "eta_refresh_enabled"
    private const val KEY_INTERVAL_MS = "eta_refresh_interval_ms"
    private const val KEY_LAST_AUTO_AT = "eta_refresh_last_auto_at"

    /** 0 = off. Four user-selectable foreground intervals. */
    val INTERVALS_MS = longArrayOf(15_000L, 30_000L, 45_000L, 60_000L)
    const val DEFAULT_INTERVAL_MS = 30_000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    /** The user's last choice, kept even while the switch is OFF. */
    fun selectedIntervalMs(context: Context): Long {
        val raw = prefs(context).getLong(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS)
        return if (INTERVALS_MS.contains(raw)) raw else DEFAULT_INTERVAL_MS
    }

    /** Returns 0 when the switch is OFF — the polling loop then never starts. */
    fun intervalMs(context: Context): Long =
        if (enabled(context)) selectedIntervalMs(context) else 0L

    fun setIntervalMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_INTERVAL_MS, ms).apply()
    }

    fun intervalIndex(context: Context): Int =
        INTERVALS_MS.indexOf(selectedIntervalMs(context)).coerceAtLeast(0)

    fun markAutoRefreshed(context: Context) {
        prefs(context).edit()
            .putLong(KEY_LAST_AUTO_AT, System.currentTimeMillis()).apply()
    }

    fun lastAutoAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_AUTO_AT, 0L)

    /** True when the app returns to the foreground after a full interval. */
    fun dueForForegroundRefresh(context: Context): Boolean {
        val interval = intervalMs(context)
        if (interval <= 0L) return false
        val last = lastAutoAt(context)
        return last <= 0L || System.currentTimeMillis() - last >= interval
    }

    /** Small status line shared by Home, route detail and Settings. */
    fun statusLabel(context: Context): String {
        if (!enabled(context)) {
            return context.getString(R.string.eta_refresh_status_off)
        }
        val seconds = (intervalMs(context) / 1000L).toString()
        val labels = context.resources.getStringArray(
            R.array.eta_refresh_interval_labels
        )
        val idx = intervalIndex(context)
        val label = labels.getOrNull(idx) ?: "${seconds}s"
        val last = lastAutoAt(context)
        return if (last <= 0L) {
            context.getString(R.string.eta_refresh_status_never, label)
        } else {
            context.getString(
                R.string.eta_refresh_status_on, label, TimeUtils.hhmm(last)
            )
        }
    }

    /** Used by the Settings screen's own summary line. */
    fun settingsLabel(context: Context): String = if (!enabled(context)) {
        context.getString(R.string.eta_refresh_settings_off)
    } else {
        val labels = context.resources.getStringArray(
            R.array.eta_refresh_interval_labels
        )
        context.getString(
            R.string.eta_refresh_settings_on,
            labels.getOrNull(intervalIndex(context)) ?: "30 秒"
        )
    }
}
