package hk.eta.app.ui

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Signature consistency check for in-app updates.
 *
 * Before the system installer is opened, the downloaded APK's signing
 * certificate(s) are compared with the certificate of the running app. A
 * mismatch means Android would reject the overwrite with INSTALL_FAILED_
 * UPDATE_INCOMPATIBLE, so we refuse earlier and explain why.
 *
 * Certificate rotation is respected: on API 28+ the app's signingCertificate
 * History is accepted as well (Android allows lineage-signed updates).
 */
object ApkSignature {

    data class Check(
        val ok: Boolean,
        val reason: String,
        val installed: List<String>,
        val downloaded: List<String>,
        val apkPackage: String
    )

    private fun flags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    /** SHA-256 fingerprints of the currently installed app (current + history). */
    @Suppress("DEPRECATION")
    fun installedFingerprints(context: Context): List<String> = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags().toLong())
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, flags())
        }
        certificates(info, includeHistory = true)
    }.getOrDefault(emptyList())

    /** SHA-256 fingerprints of a not-yet-installed APK file. */
    @Suppress("DEPRECATION")
    fun archiveFingerprints(context: Context, apk: File): List<String> = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(flags().toLong())
            )
        } else {
            pm.getPackageArchiveInfo(apk.absolutePath, flags())
        }
        // Some platform builds need sourceDir set before certificate info is filled.
        info?.applicationInfo?.let {
            it.sourceDir = apk.absolutePath
            it.publicSourceDir = apk.absolutePath
        }
        certificates(info, includeHistory = false)
    }.getOrDefault(emptyList())

    fun archivePackageName(context: Context, apk: File): String = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        info?.packageName.orEmpty()
    }.getOrDefault("")

    @Suppress("DEPRECATION")
    private fun certificates(info: PackageInfo?, includeHistory: Boolean): List<String> {
        if (info == null) return emptyList()
        val raw = ArrayList<Signature>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing: SigningInfo? = info.signingInfo
            if (signing != null) {
                raw.addAll(signing.apkContentsSigners.toList())
                if (includeHistory && signing.hasPastSigningCertificates()) {
                    raw.addAll(signing.signingCertificateHistory.toList())
                }
            } else {
                info.signatures?.let { raw.addAll(it.toList()) }
            }
        } else {
            info.signatures?.let { raw.addAll(it.toList()) }
        }
        return raw.map { fingerprint(it.toByteArray()) }.distinct().sorted()
    }

    /**
     * @param expectedSha256 optional pinned fingerprint from resources.
     *                       Empty = compare against the running app only.
     */
    fun verify(context: Context, apk: File, expectedSha256: String = ""): Check {
        val installed = installedFingerprints(context)
        val downloaded = archiveFingerprints(context, apk)
        val apkPackage = archivePackageName(context, apk)

        if (!apk.exists() || apk.length() < 1024L) {
            return Check(false, "APK 檔案無效或不存在", installed, downloaded, apkPackage)
        }
        if (downloaded.isEmpty()) {
            return Check(false, "讀取不到下載 APK 的簽章憑證", installed, downloaded, apkPackage)
        }
        if (apkPackage.isNotEmpty() && apkPackage != context.packageName) {
            return Check(false, "APK 套件名稱不符（$apkPackage）", installed, downloaded, apkPackage)
        }
        if (installed.isEmpty()) {
            return Check(false, "讀取不到本機 App 的簽章憑證", installed, downloaded, apkPackage)
        }

        val pin = normalize(expectedSha256)
        if (pin.length == 64) {
            val downloadedFlat = downloaded.map { normalize(it) }
            val installedFlat = installed.map { normalize(it) }
            if (pin !in downloadedFlat || pin !in installedFlat) {
                return Check(false, "與預設簽章指紋不符", installed, downloaded, apkPackage)
            }
            return Check(true, "簽章指紋與預設值及本機一致", installed, downloaded, apkPackage)
        }

        val allowed = installed.toSet()
        val downloadSet = downloaded.toSet()
        return if (allowed.containsAll(downloadSet)) {
            Check(true, "APK 簽章與本機一致", installed, downloaded, apkPackage)
        } else {
            Check(false, "APK 與本機使用不同簽章金鑰", installed, downloaded, apkPackage)
        }
    }

    fun normalize(raw: String): String =
        raw.replace(":", "").replace(" ", "").replace("\n", "")
            .trim().uppercase(Locale.US)

    private fun fingerprint(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { byte ->
            java.lang.String.format(Locale.US, "%02X", byte.toInt() and 0xFF)
        }
    }
}
