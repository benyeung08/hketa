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
     * 分隔 Release body 入面嘅中英兩半。
     *
     * 移植自 code-to-app 嘅做法：Release notes 用一行
     * `<!-- zh-CN -->` 做分隔（英文喺前，中文喺後）。
     * 呢個 marker 喺 GitHub 網頁上渲染成空白，所以讀者睇到嘅
     * 順序係「英文 → 中文」，而 App 會按目前介面語言自動揀其中一半。
     *
     * 冇 marker 嘅 Release（舊 notes 或未本地化嘅）就原樣顯示成段 body。
     */
    private const val ZH_MARKER = "<!-- zh-CN -->"

    /** 按目前語言揀 Release body 嘅其中一半；簡體同繁體都當中文 */
    fun localizeBody(body: String, lang: String): String {
        val raw = body.trim()
        val idx = raw.indexOf(ZH_MARKER)
        if (idx < 0) return raw
        val english = raw.substring(0, idx).trim()
        val chinese = raw.substring(idx + ZH_MARKER.length).trim()
        val isChinese = lang.startsWith("zh", ignoreCase = true)
        return if (isChinese && chinese.isNotBlank()) chinese
        else if (english.isNotBlank()) english
        else raw
    }

    /** 版本號比較（用語義化 Version，支援 pre-release 後綴） */
    fun isNewer(latest: String, current: String): Boolean =
        Version.isNewer(latest, current)

    /** 兩個版本係唔係一樣（畀版本歷史標「已安裝」用） */
    fun sameVersion(a: String, b: String): Boolean = Version.same(a, b)

    /** 搵 APK 安裝檔；搵唔到就退返第一個附件 */
    fun apkAsset(release: GitHubRelease): ReleaseAsset? =
        release.assets.firstOrNull { it.browser_download_url.endsWith(".apk", ignoreCase = true) }
            ?: release.assets.firstOrNull()

    /** 附件大小（MB，一位小數；0 表示冇資料） */
    fun sizeMb(asset: ReleaseAsset?): Float =
        if (asset == null || asset.size <= 0L) 0f else asset.size / 1024f / 1024f
}
