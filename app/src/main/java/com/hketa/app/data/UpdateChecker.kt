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

    /**
     * **彈窗用嘅「短版」更新說明** —— 由 GitHub Release body 抽出重點，
     * 淨係留幾句人睇得明嘅要點，**唔會**塞成段 Markdown 同對照表落去。
     *
     * 點解要呢個：完整 Release notes 係開發者寫嘅長文，入面有大量
     * `| 之前 | 而家 |` 對照表同程式碼塊。放落一個細對話框入面要捲好耐，
     * 手機上根本睇唔到重點（就係原先嗰個「發現新版本」彈窗嘅情況）。
     *
     * 規則（全部刻意寫死，改之前請諗清楚）：
     *  1. **掉走表格行**（`|` 開頭）—— 對照表喺細螢幕淨係得噪音
     *  2. **掉走分隔線**（`---`）、**標題**（`#`）、**HTML 註解**、**程式碼塊**
     *  3. **揀嘅次序**（呢個次序係刻意嘅，唔好掉亂）：
     *       a. 整行加粗嘅**編號小標題**（`**① 領域核心改用 Rust**`）← 最優先
     *       b. 其他整行加粗嘅行
     *       c. `- ` / `* ` / `1. ` bullet 要點
     *       d. 散文
     *
     *     **點解 bullet 唔排第一**：Release notes 嘅「已知限制」嗰段一樣係
     *     `- **xxx**` bullet，淨揀 bullet 嘅話彈窗會變「限制清單」——
     *     用家睇完成個 update 淨係見到缺點，會以為今版淨係得衰嘢。
     */
    fun briefNotes(body: String, maxLines: Int = 8, maxChars: Int = 64): String? {
        val raw = body.trim()
        if (raw.isBlank()) return null

        // 掉走所有「結構性」嘢，淨返有語義嘅行
        val lines = ArrayList<String>()
        var inCode = false
        for (line in raw.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("```")) { inCode = !inCode; continue }
            if (inCode) continue
            if (t.startsWith("|")) continue
            if (t.matches(Regex("-{3,}|={3,}|\\*{3,}"))) continue
            if (t.startsWith("#")) continue
            if (t.startsWith("<!--")) continue
            if (t.isBlank()) continue
            lines.add(t)
        }
        if (lines.isEmpty()) return null

        fun strip(t: String): String {
            val bullet = t.removePrefix("- ").removePrefix("* ").trim()
            val numbered = bullet.replace(Regex("^\\d+[.)]\\s*"), "")
            val circled = numbered.replace(Regex("^[①-\u20b4]\\s*"), "")
            return circled
                .replace("**", "")
                .replace("`", "")
                .trim()
        }

        // ★ 揀嘅次序好緊要 —— 唔可以淨係「有 bullet 就用 bullet」：
        //   Release notes 入面「已知限制」嗰段都係 `- **xxx**` bullet，
        //   冇腦淨揀 bullet 嘅話，彈窗會變成「限制清單」，用家睇完以為淨係得缺點。
        //   所以「整行加粗嘅編號小標題」（即 `**① 領域核心改用 Rust**`）優先級最高。
        val numbered = lines.mapNotNull { t ->
            // 整行加粗（`**…**`）先算小標題 —— `**一定會回退**：xxx` 呢類唔算
            if (!t.startsWith("**") || !t.endsWith("**") || t.length < 6) null
            else strip(t).takeIf { it.matches(Regex("^[①-\u20b4].*")) || it.matches(Regex("^\\d+[.)].*")) }
        }
        val headings = lines.mapNotNull { t ->
            if (!t.startsWith("**") || !t.endsWith("**") || t.length < 6) null
            else strip(t).takeIf { it.isNotBlank() && it.length >= 4 }
        }
        val bullets = lines.mapNotNull { t ->
            val isBullet = t.startsWith("- ") || t.startsWith("* ") ||
                t.matches(Regex("^\\d+[.)]\\s+.*")) || t.matches(Regex("^[①-\u20b4]\\s*.*"))
            if (!isBullet) null else strip(t).takeIf { it.isNotBlank() }
        }
        val prose = lines.map { strip(it) }.filter { it.isNotBlank() }

        val picked = when {
            numbered.size >= 2 -> numbered
            headings.size >= 2 -> headings
            bullets.size >= 2 -> bullets
            numbered.isNotEmpty() -> numbered
            headings.isNotEmpty() -> headings
            else -> prose
        }
        if (picked.isEmpty()) return null

        return picked.take(maxLines).joinToString("\n") { p ->
            if (p.length > maxChars) p.take(maxChars - 1) + "\u2026" else p
        }
    }

}
