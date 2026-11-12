package com.hketa.nativebridge

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Native half of the HKETA app.
 *
 * Enabled by <preference name="GradlePluginKotlinEnabled" value="true" />
 * in config.xml — cordova-android 15 supplies kotlin-gradle-plugin,
 * kotlin-android, kotlin-stdlib and kotlinOptions automatically.
 *
 * Update actions use the system DownloadManager plus its content:// URI,
 * so no FileProvider / extra AndroidX dependency is required.
 */
class HkEtaNative : CordovaPlugin() {

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        return when (action) {
            "deviceInfo" -> {
                callbackContext.success(deviceInfo())
                true
            }
            "appVersion" -> {
                try {
                    callbackContext.success(appVersion())
                } catch (e: Exception) {
                    callbackContext.error("appVersion failed: ${e.message}")
                }
                true
            }
            "openMap" -> {
                openMap(
                    args.optDouble(0, 0.0),
                    args.optDouble(1, 0.0),
                    args.optString(2, "HKETA stop"),
                    callbackContext
                )
                true
            }
            "toast" -> {
                showToast(args.optString(0, ""), callbackContext)
                true
            }
            "keepAwake" -> {
                keepAwake(args.optBoolean(0, false), callbackContext)
                true
            }
            "canInstallUnknown" -> {
                // CallbackContext has no success(Boolean) overload — wrap it in JSON.
                callbackContext.success(JSONObject().put("allowed", canInstallUnknown()))
                true
            }
            "requestInstallPermission" -> {
                requestInstallPermission(callbackContext)
                true
            }
            "downloadApk" -> {
                downloadApk(
                    args.optString(0, ""),
                    args.optString(1, "HKETA-release.apk"),
                    callbackContext
                )
                true
            }
            "downloadProgress" -> {
                downloadProgress(args.optLong(0, -1L), callbackContext)
                true
            }
            "installApk" -> {
                installApk(args.optLong(0, -1L), callbackContext)
                true
            }
            "openUrl" -> {
                openUrl(args.optString(0, ""), callbackContext)
                true
            }
            else -> false
        }
    }

    private fun downloadManager(): DownloadManager =
        cordova.activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun deviceInfo(): JSONObject = JSONObject().apply {
        put("model", Build.MODEL)
        put("sdk", Build.VERSION.SDK_INT)
        put("release", Build.VERSION.RELEASE)
        put("package", cordova.activity.packageName)
    }

    private fun appVersion(): JSONObject {
        val ctx = cordova.activity
        val pm = ctx.packageManager
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, 0)
        }
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return JSONObject().apply {
            put("version", info.versionName ?: "unknown")
            put("build", code)
        }
    }

    private fun openMap(
        lat: Double,
        lng: Double,
        label: String,
        callbackContext: CallbackContext
    ) {
        try {
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
            val intent = Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Do not gate on resolveActivity(): on Android 11+ package visibility
            // can hide handlers even with <queries>. Launch and catch instead.
            cordova.activity.startActivity(intent)
            callbackContext.success()
        } catch (e: ActivityNotFoundException) {
            callbackContext.error("No app can handle geo: intent")
        } catch (e: Exception) {
            callbackContext.error("openMap failed: ${e.message}")
        }
    }

    private fun showToast(message: String, callbackContext: CallbackContext) {
        cordova.activity.runOnUiThread {
            Toast.makeText(cordova.activity, message, Toast.LENGTH_SHORT).show()
        }
        callbackContext.success()
    }

    private fun keepAwake(enable: Boolean, callbackContext: CallbackContext) {
        cordova.activity.runOnUiThread {
            val window = cordova.activity.window
            if (enable) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        callbackContext.success()
    }

    // ---- in-app update ----

    private fun canInstallUnknown(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cordova.activity.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    private fun requestInstallPermission(callbackContext: CallbackContext) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${cordova.activity.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                cordova.activity.startActivity(intent)
            }
            callbackContext.success()
        } catch (e: Exception) {
            callbackContext.error("requestInstallPermission failed: ${e.message}")
        }
    }

    /** 只接受 GitHub 官方下載 host；同時擋掉 userinfo 形式的钓鱼网址。 */
    private fun isAllowedHost(url: String, hosts: Array<String>): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return hosts.any { h -> host == h || host.endsWith(".$h") }
    }

    private fun downloadApk(url: String, fileName: String, callbackContext: CallbackContext) {
        if (!url.startsWith("https://")) {
            callbackContext.error("Only https:// download URLs are allowed")
            return
        }
        if (!isAllowedHost(url, arrayOf("github.com", "objects.githubusercontent.com", "api.github.com"))) {
            callbackContext.error("Download host is not allowed")
            return
        }
        try {
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("HKETA update")
                setDescription("Downloading $safeName")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                setMimeType("application/vnd.android.package-archive")
            }
            val id = downloadManager().enqueue(request)
            callbackContext.success(JSONObject().put("id", id))
        } catch (e: Exception) {
            callbackContext.error("downloadApk failed: ${e.message}")
        }
    }

    private fun downloadProgress(id: Long, callbackContext: CallbackContext) {
        try {
            val dm = downloadManager()
            val query = DownloadManager.Query().setFilterById(id)
            val cursor = dm.query(query)
            val out = JSONObject()
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    val bytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    val progress = if (total > 0) ((bytes * 100L) / total).toInt().coerceIn(0, 100) else 0
                    val state = when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> "success"
                        DownloadManager.STATUS_FAILED -> "failed"
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_PAUSED -> "running"
                        else -> "unknown"
                    }
                    out.put("status", status)
                        .put("state", state)
                        .put("reason", reason)
                        .put("bytes", bytes)
                        .put("total", total)
                        .put("progress", progress)
                } else {
                    out.put("status", DownloadManager.STATUS_FAILED)
                        .put("state", "failed")
                        .put("reason", 0)
                        .put("bytes", 0)
                        .put("total", 0)
                        .put("progress", 0)
                }
            } finally {
                cursor?.close()
            }
            val downloadedUri = dm.getUriForDownloadedFile(id)
            out.put("uri", downloadedUri?.toString() ?: JSONObject.NULL)
            callbackContext.success(out)
        } catch (e: Exception) {
            callbackContext.error("downloadProgress failed: ${e.message}")
        }
    }

    // ---- APK signature verification (defence against a swapped / wrong-signed update) ----

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Suppress("DEPRECATION")
    private fun signaturesOf(info: PackageInfo): Array<Signature> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            info.signatures ?: emptyArray()
        }

    private fun currentSignerDigests(): Set<String> {
        val ctx = cordova.activity
        val pm = ctx.packageManager
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, signingFlags())
        }
        return signaturesOf(info).map { sha256(it.toByteArray()) }.toSet()
    }

    private fun downloadedSignerDigests(uri: Uri): Set<String> {
        val ctx = cordova.activity
        val tmp = File(ctx.cacheDir, "hketa-verify-${System.currentTimeMillis()}.apk")
        val input = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot read downloaded APK")
        input.use { src -> FileOutputStream(tmp).use { dst -> src.copyTo(dst) } }
        try {
            val pm = ctx.packageManager
            val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(tmp.absolutePath, PackageManager.PackageInfoFlags.of(signingFlags().toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(tmp.absolutePath, signingFlags())
            } ?: throw IllegalStateException("Downloaded file is not a valid APK")
            return signaturesOf(info).map { sha256(it.toByteArray()) }.toSet()
        } finally {
            tmp.delete()
        }
    }

    private fun installApk(id: Long, callbackContext: CallbackContext) {
        try {
            val dm = downloadManager()
            val uri = dm.getUriForDownloadedFile(id)
                ?: throw IllegalStateException("Downloaded file URI is not available")

            // 1) 只安裝已完成的下載，避免拿任意 id 觸發安裝。
            val cursor = dm.query(DownloadManager.Query().setFilterById(id))
            val complete = try {
                cursor != null && cursor.moveToFirst() &&
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                    DownloadManager.STATUS_SUCCESSFUL
            } finally {
                cursor?.close()
            }
            if (!complete) throw IllegalStateException("Download is not complete")

            // 2) 簽章必須與目前 App 相同，否則拒絕安裝（不再只靠系統事後擋）。
            if (downloadedSignerDigests(uri) != currentSignerDigests()) {
                throw SecurityException("APK signature does not match the installed app")
            }

            // 3) Android 8+ 需已授權安裝未知來源。
            if (!canInstallUnknown()) {
                throw SecurityException("Install permission not granted")
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            cordova.activity.startActivity(intent)
            callbackContext.success()
        } catch (e: ActivityNotFoundException) {
            callbackContext.error("No installer can handle this APK")
        } catch (e: Exception) {
            callbackContext.error("installApk failed: ${e.message}")
        }
    }

    private fun openUrl(url: String, callbackContext: CallbackContext) {
        if (!url.startsWith("https://")) {
            callbackContext.error("Only https:// URLs are allowed")
            return
        }
        if (!isAllowedHost(url, arrayOf("github.com", "objects.githubusercontent.com"))) {
            callbackContext.error("URL host is not allowed")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            cordova.activity.startActivity(intent)
            callbackContext.success()
        } catch (e: ActivityNotFoundException) {
            callbackContext.error("No browser can handle this URL")
        } catch (e: Exception) {
            callbackContext.error("openUrl failed: ${e.message}")
        }
    }
}
