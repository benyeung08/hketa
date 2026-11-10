package hk.eta.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * 極簡 content:// provider，只 serve `cacheDir/updates/` 入面嘅 APK。
 *
 * Android 8 (API 26) 起唔可以直接用 `file://` 開安裝 Intent
 * （FileUriExposedException），而專案刻意唔用 AndroidX，
 * 所以自己寫一個純 framework 嘅 FileProvider，唔引 androidx.core。
 *
 * exported=false + grantUriPermissions=true：只畀我哋自己發出、
 * 帶 FLAG_GRANT_READ_URI_PERMISSION 嘅安裝 Intent 臨時讀取。
 */
class ApkFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val dir = File(ctx.cacheDir, "updates").canonicalFile
        val name = uri.lastPathSegment ?: return null
        val file = File(dir, name).canonicalFile
        // 防止 ../ 路徑穿越
        if (!file.path.startsWith(dir.path + File.separator)) {
            throw SecurityException("Invalid APK path")
        }
        if (!file.isFile) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
