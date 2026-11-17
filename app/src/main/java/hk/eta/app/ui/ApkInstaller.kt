package hk.eta.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens the system package installer for an APK already stored in the app's
 * private filesDir. Never installs silently: the user always confirms in the
 * system UI. On Android 8+ the per-app "install unknown apps" switch is required.
 */
object ApkInstaller {

    const val AUTHORITY_SUFFIX = ".fileprovider"

    fun canRequestInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()

    /** Returns an intent that lets the user enable "Install unknown apps". */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    /**
     * @return true when the installer UI was launched, false when the user must
     *         first grant the unknown-sources permission.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() < 1024L) return false
        if (!canRequestInstalls(context)) return false

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}
