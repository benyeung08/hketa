package hk.eta.app

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * 永久簽名一致性（App 端自我校驗）。
 *
 * 預期憑證 SHA-256 由 Gradle 從 keystore 自動抽出並寫入
 * `R.string.expected_signing_cert_sha256`（resValue）。
 * 留空代表未設定 → 不啟用校驗，方便本機開發 build。
 *
 * 除咗開 App 時核對自己，亦提供 `actualOfArchive()` 畀「設定 → 檢查更新」
 * 喺安裝前核對下載返嚟嘅候選 APK，確保升級都係同一把 key。
 */
object SignatureVerifier {

    /** 預期指紋（正規化為小寫、無冒號）；空字串 = 校驗關閉。 */
    fun expected(ctx: Context): String =
        normalize(ctx.getString(R.string.expected_signing_cert_sha256))

    /** 目前已安裝 APK 嘅簽署憑證 SHA-256；讀取失敗回傳 null。 */
    @Suppress("DEPRECATION")
    fun actual(ctx: Context): String? = try {
        val pm = ctx.packageManager
        val certs: Array<out Signature>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+：用 signingInfo，支援 APK Signature Scheme v2/v3
                val info = pm.getPackageInfo(
                    ctx.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                info.signingInfo?.apkContentsSigners
            } else {
                // API 26–27（Android 8.0/8.1）仍要用舊的 GET_SIGNATURES
                val info = pm.getPackageInfo(
                    ctx.packageName,
                    PackageManager.GET_SIGNATURES
                )
                info.signatures
            }
        certSha(certs)
    } catch (e: Exception) {
        null
    }

    /**
     * 一個「未安裝」嘅 APK 檔（例如下載返嚟嘅更新包）嘅簽署憑證 SHA-256。
     * 用 getPackageArchiveInfo，唔使安裝就可以讀到簽名。
     */
    @Suppress("DEPRECATION")
    fun actualOfArchive(ctx: Context, apk: File): String? = try {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES
        else
            PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return null
        val certs: Array<out Signature>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                info.signingInfo?.apkContentsSigners
            else
                info.signatures
        certSha(certs)
    } catch (e: Exception) {
        null
    }

    /** 一致（或未設定預期值）回傳 true。 */
    fun isConsistent(ctx: Context): Boolean {
        val want = expected(ctx)
        if (want.isEmpty()) return true
        val got = actual(ctx)
        return got != null && got.equals(want, ignoreCase = true)
    }

    /** 候選 APK 是否同「目前安裝版本」同一把 key（未設定預期值時仍要比對自身）。 */
    fun archiveMatches(ctx: Context, apk: File): Boolean {
        val installed = actual(ctx) ?: return false
        val candidate = actualOfArchive(ctx, apk) ?: return false
        if (!installed.equals(candidate, ignoreCase = true)) return false
        val want = expected(ctx)
        return want.isEmpty() || candidate.equals(want, ignoreCase = true)
    }

    private fun certSha(certs: Array<out Signature>?): String? {
        val der = certs?.firstOrNull()?.toByteArray() ?: return null
        return sha256Hex(der)
    }

    private fun normalize(s: String): String =
        s.trim().lowercase().replace(":", "").replace(" ", "")

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
