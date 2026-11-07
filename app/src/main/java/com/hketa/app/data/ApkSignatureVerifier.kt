package com.hketa.app.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * 安裝前嘅最後一道防線：比較「待裝 APK」同「目前已安裝 App」嘅簽名證書。
 *
 * 點解唔淨係靠 SHA-256：`.sha256` 同 APK 放同一個 Release／同一個 repo 分支，
 * 改到其中一個就改到兩個。簽名證書比對可以擋低任何唔係用同一條 key 簽嘅包
 * （前提係 S1 嗰條洩漏咗嘅 key 已經輪換）。
 *
 * 任何一步攞唔到資料都當失敗（fail-closed），唔會放行。
 */
object ApkSignatureVerifier {

    fun matchesInstalled(context: Context, apk: File): Boolean = runCatching {
        if (!apk.isFile || apk.length() <= 0L) return false

        val pm = context.packageManager
        val flags = signingFlags()

        @Suppress("DEPRECATION")
        val archiveInfo: PackageInfo =
            pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false

        @Suppress("DEPRECATION")
        val installedInfo: PackageInfo =
            pm.getPackageInfo(context.packageName, flags) ?: return false

        val a = certs(archiveInfo)
        val b = certs(installedInfo)
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size != b.size) {
            return false
        }

        val expected = b.map { sha256(it.toByteArray()) }.sorted()
        val actual = a.map { sha256(it.toByteArray()) }.sorted()
        actual == expected
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun certs(info: PackageInfo): Array<out Signature>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
