package hk.eta.app.ui

import android.content.Context

/** User override for the floating liquid-glass navigation bar. */
object NavGlassPrefs {

    private const val PREF = "hketa_settings"
    private const val KEY_FOLLOW_SYSTEM = "nav_glass_follow_system"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Default ON: the bar reacts to system appearance / accessibility changes. */
    fun followSystem(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FOLLOW_SYSTEM, true)

    fun setFollowSystem(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FOLLOW_SYSTEM, enabled).apply()
    }
}
