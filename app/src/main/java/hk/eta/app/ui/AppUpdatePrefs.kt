package hk.eta.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import hk.eta.app.R

/** SharedPreferences for the "更新應用程式" section in Settings. */
object AppUpdatePrefs {

    private const val PREF = "hketa_app_update"
    private const val KEY_REPO = "github_repo"
    private const val KEY_AUTO = "auto_check"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_LATEST_TAG = "latest_tag"
    private const val KEY_HAS_UPDATE = "has_update"

    const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Falls back to R.string.default_github_repo, which the repo owner edits once. */
    fun repo(context: Context): String =
        prefs(context).getString(KEY_REPO, null)
            ?: context.getString(R.string.default_github_repo)

    fun setRepo(context: Context, value: String) {
        prefs(context).edit().putString(KEY_REPO, value.trim()).apply()
    }

    fun autoCheck(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoCheck(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    fun lastCheck(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CHECK, 0L)

    fun markChecked(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    fun isDue(context: Context): Boolean =
        autoCheck(context) &&
                System.currentTimeMillis() - lastCheck(context) >= CHECK_INTERVAL_MS

    fun cacheResult(context: Context, latestTag: String, hasUpdate: Boolean) {
        prefs(context).edit()
            .putString(KEY_LATEST_TAG, latestTag)
            .putBoolean(KEY_HAS_UPDATE, hasUpdate)
            .apply()
    }

    fun latestTag(context: Context): String =
        prefs(context).getString(KEY_LATEST_TAG, "") ?: ""

    fun hasUpdate(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_UPDATE, false)

    private fun packageInfo(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

    fun installedVersion(context: Context): String = runCatching {
        packageInfo(context).versionName ?: "0"
    }.getOrDefault("0")

    fun installedVersionCode(context: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(packageInfo(context))
    }.getOrDefault(0L)
}
