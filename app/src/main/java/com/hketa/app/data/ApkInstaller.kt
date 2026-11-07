package com.hketa.app.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * APK 下載 + 自動安裝（移植自 code-to-app 嘅 `core/update/ApkUpdateInstaller.kt`）。
 *
 * 流程：
 *   ① 下載去 app 私有目錄（filesDir/update/）
 *   ② 校驗 SHA-256（對比 CI 生成嘅 `<asset>.sha256`）
 *   ③ 校驗簽名證書（同目前已安裝 App 比對，見 ApkSignatureVerifier）
 *   ④ 經 FileProvider 開系統安裝器
 *
 * 點解要 FileProvider：Android 7.0（API 24）起，
 * 直接畀 `file://` Uri 出去會抛 `FileUriExposedException`，
 * 一定要用 `content://`。
 */
object ApkInstaller {

    /** 下載狀態 */
    sealed class State {
        object Idle : State()
        data class Downloading(val percent: Int, val receivedMb: Float, val totalMb: Float) : State()
        object Verifying : State()
        data class Done(val file: File) : State()
        data class Failed(val message: String) : State()
    }

    private const val CONNECT_SEC = 20L
    private const val READ_SEC = 60L

    /** 目前進行緊嘅下載（cancel 會關掉佢） */
    @Volatile
    private var currentCall: okhttp3.Call? = null

    fun cancel() {
        runCatching { currentCall?.cancel() }
        currentCall = null
    }

    private fun dir(context: Context): File =
        File(context.filesDir, "update").apply { mkdirs() }

    /** 下載 APK（帶進度） */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        expectedSha256: String? = null,
        onProgress: (State) -> Unit
    ): State = withContext(Dispatchers.IO) {
        // 開波前清走上次嘅殘留
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }

        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_SEC, TimeUnit.SECONDS)
            .build()

        val out = File(dir(context), fileName)

        // 只接受固定、可信嘅下載來源；Release JSON 被改都唔會指向任意 URL
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val allowedHosts = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "raw.githubusercontent.com"
        )
        val host = uri?.host
        if (uri == null || uri.scheme?.equals("https", ignoreCase = true) != true ||
            host == null || host !in allowedHosts
        ) {
            return@withContext State.Failed("Untrusted download host")
        }

        var state: State = State.Idle

        try {
            val call = client.newCall(Request.Builder().url(url).build())
            currentCall = call
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext State.Failed("HTTP ${resp.code}")
                }
                val body = resp.body ?: return@withContext State.Failed("empty body")
                val total = body.contentLength()
                var received = 0L
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            received += n
                            val pct = if (total > 0) (received * 100 / total).toInt() else 0
                            val s = State.Downloading(
                                percent = pct,
                                receivedMb = received / 1_048_576f,
                                totalMb = if (total > 0) total / 1_048_576f else 0f
                            )
                            state = s
                            withContext(Dispatchers.Main) { onProgress(s) }
                        }
                    }
                }
            }
            currentCall = null

            // ---- 校驗 ----
            if (!expectedSha256.isNullOrBlank()) {
                withContext(Dispatchers.Main) { onProgress(State.Verifying) }
                state = State.Verifying
                val actual = sha256(out)
                if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                    runCatching { out.delete() }
                    return@withContext State.Failed("SHA-256 mismatch")
                }
            }

            // ---- 簽名證書校驗（同目前已安裝 App 比對）----
            // SHA-256 同 APK 放同一個 Release，唔足以證明來源；呢度係最後一道防線。
            if (!ApkSignatureVerifier.matchesInstalled(context, out)) {
                runCatching { out.delete() }
                val f = State.Failed("Signing certificate mismatch")
                withContext(Dispatchers.Main) { onProgress(f) }
                return@withContext f
            }

            val done = State.Done(out)
            state = done
            withContext(Dispatchers.Main) { onProgress(done) }
            done
        } catch (e: Exception) {
            runCatching { out.delete() }
            val msg = if (e is java.io.IOException && e.message?.contains("Canceled", true) == true) {
                "cancelled"
            } else {
                e.message ?: e.javaClass.simpleName
            }
            val f = State.Failed(msg)
            state = f
            withContext(Dispatchers.Main) { onProgress(f) }
            f
        }
    }

    /** 開系統安裝器 */
    fun install(context: Context, file: File) {
        if (!file.exists()) return
        // 開系統安裝器前再驗一次，避免 Done 狀態同實際檔案之間有任何時間差
        if (!ApkSignatureVerifier.matchesInstalled(context, file)) {
            runCatching { file.delete() }
            Toast.makeText(context, "更新檔簽名不符，已取消安裝", Toast.LENGTH_LONG).show()
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** Android 8+ 要用戶授權「允許安裝未知來源」先裝到 */
    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** 跳去授權頁 */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                Toast.makeText(context, "無法開啟授權頁", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
