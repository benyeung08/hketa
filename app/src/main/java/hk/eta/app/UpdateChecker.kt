package hk.eta.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 目前已安裝 App 嘅完整版本資訊（設定頁「App 版本」區顯示）。 */
data class AppVersion(
    val name: String,
    val code: Long,
    val firstInstall: String,
    val lastUpdate: String,
    val certShort: String
)

/** GitHub Releases 上嘅一筆線上版本紀錄／可用更新。 */
data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val pageUrl: String,
    val publishedAt: String = "",
    val apkSize: Long = 0L,
    val prerelease: Boolean = false,
    val author: String = "",
    val assetCount: Int = 0,
    val apkDownloads: Long = 0L
) {
    /** `v1.2.0` → `1.2.0`，用嚟同 versionName 對照。 */
    val version: String get() = tag.trim().removePrefix("v").removePrefix("V")

    /** `2026-11-10T18:40:00Z` → `2026-11-10 18:40`（直接用 GitHub 嘅 UTC 字串，唔做時區換算）。 */
    val dateText: String
        get() = if (publishedAt.length >= 16)
            publishedAt.substring(0, 10) + " " + publishedAt.substring(11, 16) + " UTC"
        else publishedAt
}

/** 候選 APK 嘅核對結果；UI 按 code 出雙語訊息。 */
enum class CheckCode {
    OK,
    PACKAGE_MISMATCH,
    ARCHIVE_UNREADABLE,
    CERT_MISMATCH
}

data class ApkCheck(val code: CheckCode, val cert: String?, val packageName: String?)

/**
 * 設定頁「檢查更新」嘅全部邏輯。
 *
 * 流程：GitHub Releases API → 搵 .apk asset → 下載到 cache →
 * 用 PackageManager 讀候選 APK 嘅 package name 同簽名憑證 →
 * 與「目前安裝版本」同「預期指紋」雙重比對 → 一致先畀安裝。
 *
 * 另外提供 `appVersion()` 同 `fetchHistory()` 畀「App 版本」／「線上版本紀錄」用；
 * 版本紀錄只存在於線上，裝置上唔會寫入任何本機 log。
 * 任何一步失敗都唔會出現安裝按鈕，避免用戶裝到換咗 key 嘅包。
 */
object UpdateChecker {

    private const val UA = "HKETA-Android/1.0"

    fun apkFile(ctx: Context): File =
        File(File(ctx.cacheDir, "updates").apply { mkdirs() }, "hketa-update.apk")

    /** 完整版本資訊：versionName、versionCode、首次安裝／最後更新時間、憑證短碼。 */
    @Suppress("DEPRECATION")
    fun appVersion(ctx: Context): AppVersion {
        val pm = ctx.packageManager
        val info: PackageInfo = try {
            pm.getPackageInfo(ctx.packageName, 0)
        } catch (e: Exception) {
            PackageInfo()
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val cert = SignatureVerifier.actual(ctx)
        return AppVersion(
            name = info.versionName ?: "—",
            code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                info.longVersionCode else info.versionCode.toLong(),
            firstInstall = if (info.firstInstallTime > 0) fmt.format(Date(info.firstInstallTime)) else "—",
            lastUpdate = if (info.lastUpdateTime > 0) fmt.format(Date(info.lastUpdateTime)) else "—",
            certShort = cert?.take(12)?.uppercase() ?: "—"
        )
    }

    @Suppress("DEPRECATION")
    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** `v1.2.0` → `1.2.0`；逐段數字比較，非純字串比較。 */
    fun compareVersion(latest: String, current: String): Int {
        fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V")
            .split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toLongOrNull() ?: 0L }
        val a = parts(latest)
        val b = parts(current)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0L }
            val y = b.getOrElse(i) { 0L }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    /** 打 GitHub API，失敗 throw。 */
    fun fetchLatest(): ReleaseInfo {
        val j = getJson(AppConfig.RELEASES_API)
        val r = parseRelease(j)
        if (r.apkUrl.isEmpty()) error("Release has no .apk asset")
        return r
    }

    /**
     * 線上版本紀錄：每次呼叫都重新打 GitHub `/releases`，唔做快取、唔落本機。
     * 過濾 draft，保留 prerelease（會標示），最多 30 筆；API 本身按發布時間倒序。
     */
    fun fetchHistory(): List<ReleaseInfo> {
        val arr = getJsonArray(AppConfig.RELEASES_HISTORY_API)
        val out = ArrayList<ReleaseInfo>()
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            if (j.optBoolean("draft", false)) continue
            out.add(parseRelease(j))
        }
        return out
    }

    private fun getJson(url: String): JSONObject {
        val text = httpGet(url)
        return JSONObject(text)
    }

    private fun getJsonArray(url: String): JSONArray {
        val text = httpGet(url)
        return JSONArray(text)
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Cache-Control", "no-cache")
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRelease(j: JSONObject): ReleaseInfo {
        val tag = j.optString("tag_name", "")
        var apkName = ""
        var apkUrl = ""
        var apkSize = 0L
        var apkDownloads = 0L
        val assets = j.optJSONArray("assets")
        val assetCount = assets?.length() ?: 0
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.optString("name", "")
                if (n.endsWith(".apk", ignoreCase = true)) {
                    apkName = n
                    apkUrl = a.optString("browser_download_url", "")
                    apkSize = a.optLong("size", 0L)
                    apkDownloads = a.optLong("download_count", 0L)
                    break
                }
            }
        }
        return ReleaseInfo(
            tag = tag,
            name = j.optString("name", tag),
            notes = j.optString("body", ""),
            apkName = apkName,
            apkUrl = apkUrl,
            pageUrl = j.optString("html_url", ""),
            publishedAt = j.optString("published_at", j.optString("created_at", "")),
            apkSize = apkSize,
            prerelease = j.optBoolean("prerelease", false),
            author = j.optJSONObject("author")?.optString("login", "") ?: "",
            assetCount = assetCount,
            apkDownloads = apkDownloads
        )
    }

    /** 下載 APK 到 cache；onProgress(0..100, 已下載 bytes, 總 bytes)。 */
    fun download(url: String, dest: File, onProgress: (Int, Long, Long) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", UA)
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val total = conn.contentLength.toLong().coerceAtLeast(1L)
            val tmp = File(dest.parentFile, dest.name + ".part")
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(16 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(((done * 100) / total).toInt().coerceIn(0, 100), done, total)
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) error("Cannot finalize download")
        } finally {
            conn.disconnect()
        }
    }

    /** 安裝前核對：package name + 憑證指紋（對目前安裝版 + 預期值）。 */
    @Suppress("DEPRECATION")
    fun checkApk(ctx: Context, apk: File): ApkCheck {
        // flags 0 就夠拎 packageName；憑證交由 SignatureVerifier 按 API 26/28 分支讀取，
        // 避免 GET_SIGNING_CERTIFICATES 喺舊系統回傳一個 signingInfo 為 null 嘅物件。
        val info = ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return ApkCheck(CheckCode.ARCHIVE_UNREADABLE, null, null)

        val pkg = info.packageName
        if (pkg != ctx.packageName) {
            return ApkCheck(CheckCode.PACKAGE_MISMATCH, null, pkg)
        }
        val cert = SignatureVerifier.actualOfArchive(ctx, apk)
            ?: return ApkCheck(CheckCode.ARCHIVE_UNREADABLE, null, pkg)
        val installed = SignatureVerifier.actual(ctx)
        val expected = SignatureVerifier.expected(ctx)
        val ok = installed != null &&
            installed.equals(cert, ignoreCase = true) &&
            (expected.isEmpty() || expected.equals(cert, ignoreCase = true))
        return ApkCheck(
            if (ok) CheckCode.OK else CheckCode.CERT_MISMATCH,
            cert, pkg
        )
    }

    /** 已通過 checkApk 之後先好呼叫。 */
    fun install(ctx: Context, apk: File) {
        val uri = Uri.parse("content://${ctx.packageName}.apkprovider/${apk.name}")
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }
}
