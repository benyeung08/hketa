package hk.eta.app.ui

import android.content.Context

/** User override for the floating liquid-glass navigation bar. */
object NavGlassPrefs {

    private const val PREF = "hketa_settings"
    private const val KEY_FOLLOW_SYSTEM = "nav_glass_follow_system"

    /**
     * CN_ADVANCED_MATERIAL: 中國版 Android OS 17「進階材質」三態覆蓋。
     * 0 = 跟隨系統（預設）／1 = 強制完整液態／2 = 簡化靜態。
     * 只係本 App 自己嘅偏好，唔會寫入任何系統／廠商設定。
     */
    private const val KEY_MATERIAL_MODE = "nav_glass_material_mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Default ON: the bar reacts to system appearance / accessibility changes. */
    fun followSystem(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FOLLOW_SYSTEM, true)

    fun setFollowSystem(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FOLLOW_SYSTEM, enabled).apply()
    }

    fun materialMode(context: Context): Int =
        NavGlassMaterial.sanitizeMode(prefs(context).getInt(KEY_MATERIAL_MODE, 0))

    fun setMaterialMode(context: Context, mode: Int) {
        prefs(context).edit()
            .putInt(KEY_MATERIAL_MODE, NavGlassMaterial.sanitizeMode(mode))
            .apply()
    }
}
