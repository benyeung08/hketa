package com.hketa.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReleaseAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0L
)

@Serializable
data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val html_url: String = "",
    val published_at: String = "",
    val assets: List<ReleaseAsset> = emptyList()
)

enum class UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, NO_RELEASE, ERROR }

/**
 * 版本更新檢查：直接讀 GitHub Releases 嘅最新一版，對比 App 目前安裝嘅版本。
 *
 * 唔使自己架伺服器 —— Release 係 CI（push v* tag）自動產生嘅，
 * 所以 Release 一出，App 入面就即刻查得到。
 */
object UpdateChecker {

    private const val OWNER = "benyeung08"
    private const val REPO = "hketa"
    private const val LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val ALL = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 目前安裝嘅版本名。由 PackageManager 讀取，
     * 所以會自動跟住 build.gradle.kts 嘅 versionName，唔使喺代碼硬編碼。
     */
    @Suppress("DEPRECATION")
    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrDefault("").orEmpty()

    /**
     * 抓取最新 Release。
     * @return null = 仲未發布過 Release（HTTP 404）；其他錯誤會丟出例外由呼叫方處理。
     */
    suspend fun fetchLatest(): GitHubRelease? {
        val text = suspendCatching { Http.get(LATEST) }
            .getOrElse { e ->
                // Http.get 失敗時嘅 message 形如 "HTTP 404 @ <url>"
                if (e.message.orEmpty().contains("404")) return null
                throw e
            }
        return runCatching { json.decodeFromString<GitHubRelease>(text) }.getOrNull()
    }

    /**
     * 抓取版本歷史（全部 Release，由新到舊）。
     * 失敗時丟出例外由呼叫方處理；404（未發布過）會當做空清單。
     */
    suspend fun fetchHistory(): List<GitHubRelease> {
        val text = suspendCatching { Http.get(ALL) }
            .getOrElse { e ->
                if (e.message.orEmpty().contains("404")) return emptyList()
                throw e
            }
        return runCatching { json.decodeFromString<List<GitHubRelease>>(text) }.getOrDefault(emptyList())
    }

    /**
     * 版本號比較，支援 "v1.2.3" / "1.2.3" / "v1.0.0-mobilecode" 等寫法。
     * 只取每段開頭嘅數字比較（後綴如 -mobilecode 會被忽略）。
     */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(s: String): List<Int> =
            s.trim()
                .removePrefix("v").removePrefix("V")
                .split(".")
                .mapNotNull { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() }

        val a = parts(latest)
        val b = parts(current)
        if (a.isEmpty() || b.isEmpty()) return false

        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 搵 APK 安裝檔；搵唔到就退返第一個附件 */
    fun apkAsset(release: GitHubRelease): ReleaseAsset? =
        release.assets.firstOrNull { it.browser_download_url.endsWith(".apk", ignoreCase = true) }
            ?: release.assets.firstOrNull()

    /** 附件大小（MB，一位小數；0 表示冇資料） */
    fun sizeMb(asset: ReleaseAsset?): Float =
        if (asset == null || asset.size <= 0L) 0f else asset.size / 1024f / 1024f
}
