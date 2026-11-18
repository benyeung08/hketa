package hk.eta.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
import hk.eta.app.R

/**
 * Floating "liquid glass" bottom bar whose appearance FOLLOWS SYSTEM SETTINGS.
 *
 * Re-applied from MainActivity.onCreate / onResume / onConfigurationChanged,
 * so changing Dark mode, High-contrast text or "Remove animations" in the
 * Android system Settings is picked up as soon as the user returns to the app.
 *
 * Honest scope: there is no public per-app "liquid glass" toggle whose exact
 * Android 17 key can be relied on from here, so we use only documented APIs:
 *   - Configuration.uiMode (night mode)
 *   - Settings.Secure "high_text_contrast_enabled"
 *   - Settings.Global ANIMATOR_DURATION_SCALE / TRANSITION_ANIMATION_SCALE
 *   - Window.setBackgroundBlurRadius (API 31+) for the real backdrop blur
 * Everything is wrapped in runCatching and falls back to a plain translucent
 * drawable, so older / restricted devices still render a valid bar.
 */
object NavGlass {

    private const val BLUR_RADIUS_DAY = 18
    private const val BLUR_RADIUS_NIGHT = 26
    private const val ELEVATION_DP = 10f

    private fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private fun highContrast(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(
            context.contentResolver, "high_text_contrast_enabled", 0
        ) == 1
    }.getOrDefault(false)

    private fun reducedMotion(context: Context): Boolean = runCatching {
        val cr = context.contentResolver
        val animator = Settings.Global.getFloat(
            cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )
        val transition = Settings.Global.getFloat(
            cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f
        )
        animator == 0f || transition == 0f
    }.getOrDefault(false)

    private fun backdropBlur(activity: Activity, enabled: Boolean, night: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            activity.window.setBackgroundBlurRadius(
                if (enabled) {
                    if (night) BLUR_RADIUS_NIGHT else BLUR_RADIUS_DAY
                } else 0
            )
        }
    }

    /** Applies the glass drawable, elevation and window blur to the bar. */
    fun apply(activity: Activity, bar: View) {
        val follow = NavGlassPrefs.followSystem(activity)
        val night = follow && isNight(activity)
        val contrast = follow && highContrast(activity)
        val reduced = follow && reducedMotion(activity)

        val bg = when {
            contrast -> R.drawable.bg_nav_glass_contrast
            night -> R.drawable.bg_nav_glass_dark
            else -> R.drawable.bg_nav_glass
        }
        bar.setBackgroundResource(bg)
        bar.elevation = if (reduced) 0f else {
            ELEVATION_DP * activity.resources.displayMetrics.density
        }
        // Real backdrop blur only when allowed by the system accessibility state.
        backdropBlur(activity, !reduced && !contrast, night)
    }

    /** Selected tab keeps a frosted pill instead of painting an opaque colour. */
    fun selectTab(context: Context, tab: View, selected: Boolean) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_glass_selected)
        } else {
            tab.background = null
        }
        val color = ContextCompat.getColor(
            context,
            if (selected) R.color.glass_tab_text else R.color.glass_tab_muted
        )
        (tab as? android.widget.TextView)?.setTextColor(color)
    }

    /** Human-readable state shown in 應用程式設置. */
    fun status(context: Context): String {
        if (!NavGlassPrefs.followSystem(context)) {
            return context.getString(R.string.nav_glass_state_fixed)
        }
        val inner = when {
            highContrast(context) -> context.getString(R.string.nav_glass_state_contrast)
            reducedMotion(context) -> context.getString(R.string.nav_glass_state_reduced)
            isNight(context) -> context.getString(R.string.nav_glass_state_night)
            else -> context.getString(R.string.nav_glass_state_day)
        }
        return context.getString(R.string.nav_glass_state_follow, inner)
    }
}
