package hk.eta.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 設定頁：簽名一致性、App 版本、檢查更新、**路線資料更新**、線上版本紀錄、關於。
 *
 * 路線資料更新涵蓋巴士／專線小巴／地鐵／輕鐵嘅收費、路線、服務班次，
 * 由 GitHub raw JSON 抓取、驗證後套用並快取（離線可用）。
 *
 * 版本紀錄只有一個來源：GitHub Releases（線上）。每次開頁／撳重新載入
 * 都即時向 API 查詢，唔快取、唔寫入本機，裝置上唔會留低任何更新 log。
 *
 * 更新流程刻意做成「簽名一致先有得撳安裝」：
 * GitHub Releases → 下載 APK 到 cache → getPackageArchiveInfo 讀候選憑證 →
 * 同目前安裝版 + 預期指紋比對 → 完全一致先顯示安裝按鈕。
 */
class SettingsActivity : Activity() {

    private lateinit var settingsScroll: ScrollView
    private lateinit var settingsTitle: TextView
    private lateinit var settingsLangBtn: Button
    private lateinit var dataSectionTitle: TextView
    private lateinit var dataHint: TextView
    private lateinit var dataStatus: TextView
    private lateinit var dataVersion: TextView
    private lateinit var dataStats: LinearLayout
    private lateinit var dataNotes: TextView
    private lateinit var dataUpdateBtn: Button
    private lateinit var dataRestoreBtn: Button
    private lateinit var sigSectionTitle: TextView
    private lateinit var sigStatus: TextView
    private lateinit var sigActual: TextView
    private lateinit var sigExpected: TextView
    private lateinit var sigRecheckBtn: Button
    private lateinit var sigNote: TextView
    private lateinit var verSectionTitle: TextView
    private lateinit var verAppTitle: TextView
    private lateinit var verAppDetail: TextView
    private lateinit var verCurrent: TextView
    private lateinit var verLatest: TextView
    private lateinit var verNotes: TextView
    private lateinit var verProgress: TextView
    private lateinit var verCheckBtn: Button
    private lateinit var verDownloadBtn: Button
    private lateinit var verInstallBtn: Button
    private lateinit var verHistoryTitle: TextView
    private lateinit var verHistoryHint: TextView
    private lateinit var verHistoryBtn: Button
    private lateinit var verHistoryStatus: TextView
    private lateinit var verHistoryTime: TextView
    private lateinit var verHistoryList: LinearLayout
    private lateinit var aboutSectionTitle: TextView
    private lateinit var aboutText: TextView

    private var latest: ReleaseInfo? = null
    private var history: List<ReleaseInfo> = emptyList()
    private var historyTime = ""
    private var busy = false
    private var historyBusy = false
    private var dataBusy = false
    /** 邊幾筆線上紀錄而家展開咗完整更新說明（記 index，唔落本機儲存）。 */
    private val expanded = HashSet<Int>()
    private var onlineOk = false
    private var onlineError = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsScroll = findViewById(R.id.settingsScroll)
        settingsTitle = findViewById(R.id.settingsTitle)
        settingsLangBtn = findViewById(R.id.settingsLangBtn)
        dataSectionTitle = findViewById(R.id.dataSectionTitle)
        dataHint = findViewById(R.id.dataHint)
        dataStatus = findViewById(R.id.dataStatus)
        dataVersion = findViewById(R.id.dataVersion)
        dataStats = findViewById(R.id.dataStats)
        dataNotes = findViewById(R.id.dataNotes)
        dataUpdateBtn = findViewById(R.id.dataUpdateBtn)
        dataRestoreBtn = findViewById(R.id.dataRestoreBtn)
        sigSectionTitle = findViewById(R.id.sigSectionTitle)
        sigStatus = findViewById(R.id.sigStatus)
        sigActual = findViewById(R.id.sigActual)
        sigExpected = findViewById(R.id.sigExpected)
        sigRecheckBtn = findViewById(R.id.sigRecheckBtn)
        sigNote = findViewById(R.id.sigNote)
        verSectionTitle = findViewById(R.id.verSectionTitle)
        verAppTitle = findViewById(R.id.verAppTitle)
        verAppDetail = findViewById(R.id.verAppDetail)
        verCurrent = findViewById(R.id.verCurrent)
        verLatest = findViewById(R.id.verLatest)
        verNotes = findViewById(R.id.verNotes)
        verProgress = findViewById(R.id.verProgress)
        verCheckBtn = findViewById(R.id.verCheckBtn)
        verDownloadBtn = findViewById(R.id.verDownloadBtn)
        verInstallBtn = findViewById(R.id.verInstallBtn)
        verHistoryTitle = findViewById(R.id.verHistoryTitle)
        verHistoryHint = findViewById(R.id.verHistoryHint)
        verHistoryBtn = findViewById(R.id.verHistoryBtn)
        verHistoryStatus = findViewById(R.id.verHistoryStatus)
        verHistoryTime = findViewById(R.id.verHistoryTime)
        verHistoryList = findViewById(R.id.verHistoryList)
        aboutSectionTitle = findViewById(R.id.aboutSectionTitle)
        aboutText = findViewById(R.id.aboutText)

        findViewById<Button>(R.id.settingsBackBtn).setOnClickListener { finish() }
        settingsLangBtn.setOnClickListener {
            AppState.lang = if (AppState.lang == Lang.ZH) Lang.EN else Lang.ZH
            renderTexts()
            renderDataCard()
            renderHistoryStatus()
            renderHistoryTime()
            renderHistory()
        }
        sigRecheckBtn.setOnClickListener { renderSignature() }
        verCheckBtn.setOnClickListener { checkUpdate() }
        verDownloadBtn.setOnClickListener { downloadAndVerify() }
        verHistoryBtn.setOnClickListener { loadHistory() }
        verInstallBtn.setOnClickListener { triggerInstall() }
        dataUpdateBtn.setOnClickListener { updateRouteData() }
        dataRestoreBtn.setOnClickListener { restoreRouteData() }

        renderTexts()
        renderDataCard()
        renderHistoryStatus()
        renderHistoryTime()
        renderHistory()
        // 一開設定頁就向線上抓版本紀錄；全部資料即時，唔落本機、唔快取。
        if (AppConfig.isConfigured) loadHistory()
        // 由首頁「資料更新」掣入嚟会帶 focus=data，直接捲去路線資料區。
        if (intent.getStringExtra("focus") == "data") {
            settingsScroll.post {
                settingsScroll.smoothScrollTo(0, dataSectionTitle.top)
            }
        }
    }

    private fun renderTexts() {
        val lang = AppState.lang
        settingsTitle.text = Fmt.zh(lang, "設定", "Settings")
        settingsLangBtn.text = if (lang == Lang.ZH) "EN" else "中"
        sigSectionTitle.text = Fmt.zh(lang, "簽名一致性", "Signature consistency")
        sigRecheckBtn.text = Fmt.zh(lang, "重新驗證簽名", "Re-verify signature")
        sigNote.text = Fmt.zh(
            lang,
            "預期指紋由建置時嘅永久金鑰注入，唔可以喺 App 內修改（否則防重新打包就失效）。更新包必須同目前版本同一把 key 先可以安裝。",
            "The expected fingerprint is injected at build time from the permanent key and cannot be changed in-app. Updates must share this key to be installable."
        )
        verSectionTitle.text = Fmt.zh(lang, "檢查更新", "Check for updates")
        verAppTitle.text = Fmt.zh(lang, "App 版本", "App version")
        verCheckBtn.text = Fmt.zh(lang, "檢查 GitHub 更新", "Check GitHub for updates")
        verDownloadBtn.text = Fmt.zh(lang, "下載並驗證簽名", "Download & verify signature")
        verInstallBtn.text = Fmt.zh(lang, "簽名一致，安裝更新", "Signature OK — install update")
        dataSectionTitle.text = Fmt.zh(lang, "路線資料更新", "Route data update")
        dataHint.text = Fmt.zh(
            lang,
            "巴士／專線小巴／地鐵／輕鐵：收費、路線站位、服務班次。由 GitHub 線上 JSON 更新，驗證通過先套用；更新後會快取資料供離線使用（唔係版本紀錄，版本紀錄仍然只喺線上）。",
            "Bus / minibus / metro / light rail: fares, routes & stops, service schedules. Fetched from GitHub online JSON, applied only after validation. Updated data is cached for offline use (this is data, not a version log — history stays online-only)."
        )
        dataUpdateBtn.text = Fmt.zh(lang, "檢查並更新路線資料", "Check & update route data")
        dataRestoreBtn.text = Fmt.zh(lang, "還原內建資料", "Restore bundled data")
        verHistoryTitle.text = Fmt.zh(lang, "線上版本紀錄", "Online release history")
        verHistoryHint.text = Fmt.zh(
            lang,
            "所有版本紀錄即時由 GitHub Releases 讀取，唔快取、唔寫入本機；對照目前安裝版本標示狀態，撳一筆可展開完整更新說明。",
            "All version records are fetched live from GitHub Releases — no cache, nothing stored on-device. Compares against your installed version; tap a row to expand full release notes."
        )
        verHistoryBtn.text = Fmt.zh(lang, "重新載入線上紀錄", "Reload online history")
        aboutSectionTitle.text = Fmt.zh(lang, "關於", "About")

        renderAppVersion()
        if (verLatest.text.isNullOrBlank() || latest == null) {
            verLatest.text = Fmt.zh(lang, "最新版本：尚未檢查", "Latest version: not checked")
        }
        aboutText.text = Fmt.zh(
            lang,
            "HKETA · Kotlin / JSON 重製版\n支援 Android 8.0 (API 26) – Android 17 (API 37)\n內建 JSON 為示範資料，並非即時到站時間。\n\n所有版本共用同一把永久簽名金鑰，可直接覆蓋升級；版本紀錄以線上 GitHub Releases 為準。",
            "HKETA · Kotlin / JSON rebuild\nSupports Android 8.0 (API 26) – Android 17 (API 37)\nBundled JSON is demo data, not live ETA.\n\nAll versions share one permanent signing key for in-place upgrades; version history comes from online GitHub Releases."
        )
        renderSignature()
        renderNotes(verNotes.text?.toString())
    }

    /** App 版本：名稱、versionCode、首次安裝／最後更新、憑證短碼。 */
    private fun renderAppVersion() {
        val lang = AppState.lang
        val v = UpdateChecker.appVersion(this)
        verCurrent.text = Fmt.zh(lang, "目前版本：${v.name}", "Current version: ${v.name}")
        verAppDetail.text = buildString {
            append(Fmt.zh(lang, "版本名稱", "Version name")).append("：").append(v.name).append('\n')
            append("versionCode：").append(v.code).append('\n')
            append(Fmt.zh(lang, "首次安裝", "First installed")).append("：").append(v.firstInstall).append('\n')
            append(Fmt.zh(lang, "最後更新", "Last updated")).append("：").append(v.lastUpdate).append('\n')
            append(Fmt.zh(lang, "簽名憑證", "Signing cert")).append("：").append(v.certShort).append("…")
        }
    }

    private fun renderSignature() {
        val lang = AppState.lang
        val got = SignatureVerifier.actual(this)
        val want = SignatureVerifier.expected(this)
        val consistent = SignatureVerifier.isConsistent(this)

        sigStatus.text = when {
            want.isEmpty() -> Fmt.zh(lang, "⚠️ 未設定預期指紋（開發版）", "⚠️ Expected fingerprint not set (dev build)")
            consistent -> Fmt.zh(lang, "✅ 簽名一致，可安全升級", "✅ Signature consistent, safe to upgrade")
            else -> Fmt.zh(lang, "❌ 簽名不一致，可能被重新打包", "❌ Signature mismatch, possibly repackaged")
        }
        sigStatus.setTextColor(
            when {
                want.isEmpty() -> Fmt.muted
                consistent -> Fmt.green
                else -> Color.parseColor("#D93025")
            }
        )
        sigActual.text = buildString {
            append(Fmt.zh(lang, "實際憑證 SHA-256", "Actual cert SHA-256")).append("\n")
            append(got ?: Fmt.zh(lang, "讀取失敗", "Unavailable"))
        }
        sigExpected.text = buildString {
            append(Fmt.zh(lang, "預期憑證 SHA-256", "Expected cert SHA-256")).append("\n")
            append(
                if (want.isEmpty()) Fmt.zh(lang, "（未設定，校驗關閉）", "(not set, verification off)")
                else want
            )
        }
    }

    // ── 路線資料更新（巴士／小巴／地鐵／輕鐵） ─────────────────────

    /** 顯示目前資料版本、來源，以及四類各自嘅路線／站位／收費／班次覆蓋。 */
    private fun renderDataCard() {
        val lang = AppState.lang
        val d = AppState.data ?: return
        val s = RouteDataUpdate.stats(d)
        val src = if (AppState.dataCached)
            Fmt.zh(lang, "線上路線資料（已快取，離線可用）", "Online route data (cached, offline-ready)")
        else
            Fmt.zh(lang, "APK 內建資料", "Bundled APK data")
        dataStatus.text = Fmt.zh(lang, "● $src", "● $src")
        dataStatus.setTextColor(if (AppState.dataCached) Fmt.green else Fmt.primary)
        dataVersion.text = buildString {
            append("v").append(s.version)
            if (s.generated.isNotBlank()) append(" · ").append(s.generated)
            if (AppState.dataUpdatedAt.isNotBlank())
                append(" · ").append(Fmt.zh(lang, "更新於 ", "updated ")).append(AppState.dataUpdatedAt)
            if (s.demo) append(" · ").append(Fmt.zh(lang, "示範資料", "demo data"))
        }
        dataStats.removeAllViews()
        for (m in s.modes) dataStats.addView(statLine(m))
        if (dataNotes.text.isNullOrBlank()) {
            dataNotes.text = Fmt.zh(
                lang,
                "撳上面個掣由 GitHub 抓最新 hketa.json，驗證四類資料齊全、收費／班次完整同站位參照正確之後先會套用。",
                "Tap the button above to fetch the latest hketa.json from GitHub. It is applied only after all four modes, fares, schedules and stop references pass validation."
            )
        }
    }

    private fun statLine(m: RouteDataUpdate.ModeStat): TextView {
        val lang = AppState.lang
        val icon = when (m.id) {
            "bus" -> "🚌"
            "minibus" -> "🚐"
            "metro" -> "🚇"
            "light_rail" -> "🚋"
            else -> "•"
        }
        val name = AppState.get().modeName(m.id, lang)
        val zh = "$icon $name  ${m.routes} 條 · 站位 ${m.stops} · 收費 ${m.withFare}/${m.routes} · 班次 ${m.withService}/${m.routes}"
        val en = "$icon $name  ${m.routes} routes · ${m.stops} stops · fares ${m.withFare}/${m.routes} · schedules ${m.withService}/${m.routes}"
        return itemText(Fmt.zh(lang, zh, en), Fmt.muted, selectable = false)
    }

    private fun updateRouteData() {
        if (dataBusy) return
        if (!AppConfig.isConfigured) {
            alert(
                Fmt.zh(AppState.lang, "尚未設定 GitHub 座標", "GitHub repo not configured"),
                Fmt.zh(
                    AppState.lang,
                    "請喺 AppConfig.kt 填入 OWNER / REPO，重新出 APK 後先可以線上路線資料更新。\n\n目前仍可撳「還原內建資料」離線使用。",
                    "Fill in OWNER / REPO in AppConfig.kt and rebuild to enable online route data updates.\n\nYou can still use 'Restore bundled data' offline."
                )
            )
            return
        }
        dataBusy = true
        dataUpdateBtn.isEnabled = false
        dataRestoreBtn.isEnabled = false
        dataNotes.text = Fmt.zh(
            AppState.lang, "正在由 GitHub 下載並驗證路線資料…", "Downloading & validating route data from GitHub…"
        )
        val before = RouteDataUpdate.stats(AppState.get())

        Thread {
            try {
                val text = RouteDataUpdate.fetchRemoteText()
                // 先喺背景 parse + 驗證；唔合格直接 throw，原有資料完全唔郁。
                val v = RouteDataUpdate.validate(JsonParser.parse(text))
                if (!v.ok) error(v.problems.joinToString("\n"))
                runOnUiThread {
                    val d = RouteDataUpdate.applyRemote(this, text)
                    val after = RouteDataUpdate.stats(d)
                    val diff = RouteDataUpdate.diff(before, after, AppState.lang)
                    val same = before.version == after.version
                    val head = Fmt.zh(
                        AppState.lang,
                        if (same) "✅ 驗證通過，已重新套用線上資料（版本相同）"
                        else "✅ 驗證通過，路線資料已更新",
                        if (same) "✅ Validated, online data re-applied (same version)"
                        else "✅ Validated, route data updated"
                    )
                    dataNotes.text = buildString {
                        append(head).append('\n')
                        append("v").append(before.version).append(" → v").append(after.version).append('\n')
                        append(diff).append('\n')
                        append(Fmt.zh(AppState.lang, "已快取，離線可用。", "Cached for offline use."))
                    }
                    renderDataCard()
                    toast(Fmt.zh(AppState.lang, "路線資料已更新", "Route data updated"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dataNotes.text = Fmt.zh(
                        AppState.lang,
                        "❌ 更新失敗，原有資料維持不變：\n${e.message}",
                        "❌ Update failed, existing data kept:\n${e.message}"
                    )
                }
            } finally {
                runOnUiThread {
                    dataBusy = false
                    dataUpdateBtn.isEnabled = true
                    dataRestoreBtn.isEnabled = true
                }
            }
        }.start()
    }

    private fun restoreRouteData() {
        if (dataBusy) return
        val lang = AppState.lang
        AlertDialog.Builder(this)
            .setTitle(Fmt.zh(lang, "還原內建資料", "Restore bundled data"))
            .setMessage(
                Fmt.zh(
                    lang,
                    "會刪除已快取嘅線上路線資料，改用 APK 內建嘅 hketa.json。確定？",
                    "This deletes the cached online route data and switches back to the APK's bundled hketa.json. Continue?"
                )
            )
            .setNegativeButton(Fmt.zh(lang, "取消", "Cancel"), null)
            .setPositiveButton(Fmt.zh(lang, "確定還原", "Restore")) { _, _ ->
                RouteDataUpdate.restoreBundled(this)
                dataNotes.text = Fmt.zh(
                    lang,
                    "✅ 已還原內建資料，並清除路線資料快取。",
                    "✅ Bundled data restored; route data cache cleared."
                )
                renderDataCard()
                toast(Fmt.zh(lang, "已還原內建資料", "Bundled data restored"))
            }
            .show()
    }

    // ── 線上版本紀錄（唯一版本紀錄來源） ──────────────────────────

    private fun loadHistory() {
        if (historyBusy) return
        if (!AppConfig.isConfigured) {
            onlineOk = false
            onlineError = Fmt.zh(AppState.lang, "尚未設定 GitHub 座標", "GitHub repo not configured")
            renderHistoryStatus()
            return
        }
        historyBusy = true
        onlineError = ""
        verHistoryBtn.isEnabled = false
        renderHistoryStatus()

        Thread {
            try {
                val list = UpdateChecker.fetchHistory()
                runOnUiThread {
                    historyBusy = false
                    verHistoryBtn.isEnabled = true
                    history = list
                    historyTime = Fmt.nowTime()
                    onlineOk = true
                    renderHistoryStatus()
                    renderHistoryTime()
                    renderHistory()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    historyBusy = false
                    verHistoryBtn.isEnabled = true
                    onlineOk = false
                    onlineError = e.message ?: "error"
                    renderHistoryStatus()
                }
            }
        }.start()
    }

    /** 線上連線狀態：載入中 / 已連線 / 失敗 / 未設定。 */
    private fun renderHistoryStatus() {
        val lang = AppState.lang
        when {
            historyBusy -> {
                verHistoryStatus.text = Fmt.zh(lang, "● 線上查詢中…", "● Connecting to GitHub…")
                verHistoryStatus.setTextColor(Fmt.primary)
            }
            onlineOk -> {
                verHistoryStatus.text = Fmt.zh(
                    lang,
                    "● 已連線 · GitHub Releases（線上，唔快取）· 共 ${history.size} 筆",
                    "● Online · GitHub Releases (no cache) · ${history.size} releases"
                )
                verHistoryStatus.setTextColor(Fmt.green)
            }
            onlineError.isNotEmpty() -> {
                verHistoryStatus.text = Fmt.zh(lang, "● 連線失敗：$onlineError", "● Connection failed: $onlineError")
                verHistoryStatus.setTextColor(Color.parseColor("#D93025"))
            }
            !AppConfig.isConfigured -> {
                verHistoryStatus.text = Fmt.zh(
                    lang, "○ 尚未設定 GitHub 座標（AppConfig.kt）", "○ GitHub repo not configured (AppConfig.kt)"
                )
                verHistoryStatus.setTextColor(Fmt.muted)
            }
            else -> {
                verHistoryStatus.text = Fmt.zh(lang, "○ 尚未載入（線上，唔快取）", "○ Not loaded yet (online, no cache)")
                verHistoryStatus.setTextColor(Fmt.muted)
            }
        }
    }

    private fun renderHistoryTime() {
        val lang = AppState.lang
        verHistoryTime.text = if (historyTime.isEmpty())
            Fmt.zh(lang, "最後同步：— · 來源 api.github.com/.../releases", "Last sync: — · source api.github.com/.../releases")
        else
            Fmt.zh(
                lang,
                "最後同步：$historyTime · 來源 api.github.com/.../releases",
                "Last sync: $historyTime · source api.github.com/.../releases"
            )
    }

    /** 動態畫出每筆線上 Release；撳標題展開／收合完整更新說明。 */
    private fun renderHistory() {
        val lang = AppState.lang
        verHistoryList.removeAllViews()
        if (history.isEmpty()) {
            verHistoryList.addView(itemText(
                Fmt.zh(
                    lang,
                    "暫無線上版本紀錄，撳「重新載入線上紀錄」即時查詢 GitHub Releases。",
                    "No online records yet — tap 'Reload online history' to query GitHub Releases."
                ),
                Fmt.muted, selectable = false
            ))
            return
        }
        val current = UpdateChecker.currentVersion(this)
        history.forEachIndexed { idx, r ->
            val isInstalled = r.version == current
            val isLatest = idx == 0
            val cmp = UpdateChecker.compareVersion(r.version, current)
            val tags = mutableListOf<String>()
            if (isLatest) tags.add(Fmt.zh(lang, "最新", "Latest"))
            if (isInstalled) tags.add(Fmt.zh(lang, "目前安裝", "Installed"))
            else if (cmp > 0) tags.add(Fmt.zh(lang, "可更新", "Newer"))
            else tags.add(Fmt.zh(lang, "較舊", "Older"))
            if (r.prerelease) tags.add(Fmt.zh(lang, "預覽", "Pre-release"))
            if (r.apkUrl.isEmpty()) tags.add(Fmt.zh(lang, "無 APK", "No APK"))

            val head = StringBuilder()
            head.append(if (expanded.contains(idx)) "▾ " else "▸ ")
            head.append(r.tag)
            if (r.name.isNotBlank() && r.name != r.tag) head.append(" · ").append(r.name)
            if (tags.isNotEmpty()) head.append("  [").append(tags.joinToString(" / ")).append("]")
            head.append('\n').append(r.dateText)
            if (r.apkName.isNotEmpty()) {
                head.append(" · ").append(r.apkName)
                if (r.apkSize > 0) {
                    val size = if (r.apkSize >= 1048576L)
                        "${r.apkSize / 1048576L} MB" else "${r.apkSize / 1024L} KB"
                    head.append(" (").append(size).append(")")
                }
            }
            val firstNote = r.notes.lines().firstOrNull { it.isNotBlank() }?.trim()
            if (!firstNote.isNullOrBlank()) {
                head.append('\n').append(firstNote.take(80))
                if (r.notes.length > firstNote.length + 1) head.append(" …")
            }
            head.append('\n').append(
                Fmt.zh(lang, "撳展開完整更新說明", "Tap to expand full release notes")
            )

            val color = when {
                isInstalled -> Fmt.primary
                isLatest || cmp > 0 -> Fmt.green
                else -> Color.parseColor("#334155")
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val headView = itemText(head.toString(), color, selectable = false)
            headView.setOnClickListener {
                if (expanded.contains(idx)) expanded.remove(idx) else expanded.add(idx)
                renderHistory()
            }
            row.addView(headView)

            if (expanded.contains(idx)) {
                val detail = StringBuilder()
                detail.append(Fmt.zh(lang, "完整更新說明", "Full release notes")).append("\n")
                detail.append(r.notes.trim().ifBlank { Fmt.zh(lang, "（此版本無更新說明）", "(no release notes)") })
                detail.append("\n\n")
                detail.append(Fmt.zh(lang, "APK", "APK")).append("：")
                    .append(if (r.apkName.isNotEmpty()) r.apkName else Fmt.zh(lang, "無", "none"))
                if (r.pageUrl.isNotBlank()) {
                    detail.append("\n").append(Fmt.zh(lang, "Release 頁面", "Release page"))
                        .append("：").append(r.pageUrl)
                }
                row.addView(itemText(detail.toString(), Fmt.muted, selectable = true))
                if (r.pageUrl.isNotBlank()) {
                    val link = itemText(
                        Fmt.zh(lang, "→ 開啟 GitHub Release 頁", "→ Open GitHub release page"),
                        Fmt.primary, selectable = false
                    )
                    link.setOnClickListener {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl)))
                        } catch (e: Exception) {
                            toast(e.message ?: "")
                        }
                    }
                    row.addView(link)
                }
            }
            verHistoryList.addView(row)
        }
    }

    private fun itemText(text: String, color: Int, selectable: Boolean = true): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = 11f
            setPadding(0, 8, 0, 8)
            setLineSpacing(2f, 1f)
            setTextIsSelectable(selectable)
        }

    // ── 檢查更新 / 下載 / 安裝 ──────────────────────────────────

    private fun checkUpdate() {
        if (busy) return
        if (!AppConfig.isConfigured) {
            alert(
                Fmt.zh(AppState.lang, "尚未設定 GitHub 座標", "GitHub repo not configured"),
                Fmt.zh(
                    AppState.lang,
                    "請喺 AppConfig.kt 填入 OWNER / REPO，重新出 APK 後先可以用 App 內更新。",
                    "Fill in OWNER / REPO in AppConfig.kt and rebuild to enable in-app updates."
                )
            )
            return
        }
        busy = true
        verCheckBtn.isEnabled = false
        verDownloadBtn.visibility = View.GONE
        verInstallBtn.visibility = View.GONE
        verProgress.visibility = View.GONE
        setNotes(Fmt.zh(AppState.lang, "正在向 GitHub 查詢…", "Querying GitHub…"))

        Thread {
            try {
                val r = UpdateChecker.fetchLatest()
                val cmp = UpdateChecker.compareVersion(r.tag, UpdateChecker.currentVersion(this))
                runOnUiThread {
                    busy = false
                    verCheckBtn.isEnabled = true
                    latest = if (cmp > 0) r else null
                    verLatest.text = Fmt.zh(
                        AppState.lang,
                        "最新版本：${r.tag}（${r.apkName}）",
                        "Latest version: ${r.tag} (${r.apkName})"
                    )
                    if (cmp > 0) {
                        setNotes(
                            (r.name + "\n" + r.notes).trim().take(400).ifBlank {
                                Fmt.zh(AppState.lang, "有新版本可供下載。", "A new version is available.")
                            }
                        )
                        verDownloadBtn.visibility = View.VISIBLE
                    } else {
                        setNotes(Fmt.zh(AppState.lang, "✅ 已經係最新版本。", "✅ You are up to date."))
                        toast(Fmt.zh(AppState.lang, "已經係最新版本", "Already up to date"))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    busy = false
                    verCheckBtn.isEnabled = true
                    setNotes(
                        Fmt.zh(
                            AppState.lang,
                            "查詢失敗：${e.message}\n請確認 repo 有 Release 而且附带 .apk 檔案。",
                            "Check failed: ${e.message}\nMake sure the repo has a Release with an .apk asset."
                        )
                    )
                }
            }
        }.start()
    }

    private fun downloadAndVerify() {
        val r = latest ?: return
        if (busy) return
        busy = true
        verDownloadBtn.isEnabled = false
        verInstallBtn.visibility = View.GONE
        verProgress.visibility = View.VISIBLE
        setNotes(Fmt.zh(AppState.lang, "下載中，完成後會自動核對簽名…", "Downloading, signature will be verified after…"))

        val dest = UpdateChecker.apkFile(this)
        Thread {
            try {
                UpdateChecker.download(r.apkUrl, dest) { pct, done, total ->
                    runOnUiThread {
                        verProgress.text = Fmt.zh(
                            AppState.lang,
                            "下載 $pct%（${done / 1024} / ${total / 1024} KB）",
                            "Downloaded $pct% (${done / 1024} / ${total / 1024} KB)"
                        )
                    }
                }
                val check = UpdateChecker.checkApk(this, dest)
                runOnUiThread {
                    busy = false
                    verDownloadBtn.isEnabled = true
                    verProgress.visibility = View.GONE
                    when (check.code) {
                        CheckCode.OK -> {
                            setNotes(
                                Fmt.zh(
                                    AppState.lang,
                                    "✅ 簽名一致，與目前版本同一把永久金鑰。\n候選憑證：\n${check.cert}\n\n可以安全覆蓋安裝。",
                                    "✅ Signature matches the installed permanent key.\nCandidate cert:\n${check.cert}\n\nSafe to install as an update."
                                )
                            )
                            verInstallBtn.visibility = View.VISIBLE
                            renderSignature()
                        }
                        CheckCode.PACKAGE_MISMATCH -> {
                            dest.delete()
                            setNotes(
                                Fmt.zh(
                                    AppState.lang,
                                    "❌ 已拒絕：更新包 package name 唔係 ${packageName}（係 ${check.packageName}）。",
                                    "❌ Blocked: package name is not ${packageName} (got ${check.packageName})."
                                )
                            )
                        }
                        CheckCode.CERT_MISMATCH -> {
                            dest.delete()
                            setNotes(
                                Fmt.zh(
                                    AppState.lang,
                                    "❌ 已拒絕：簽名憑證同目前版本唔一致，安裝會導致無法覆蓋升級。\n候選憑證：\n${check.cert}",
                                    "❌ Blocked: signing cert differs from the installed version; updating would break in-place upgrade.\nCandidate cert:\n${check.cert}"
                                )
                            )
                        }
                        CheckCode.ARCHIVE_UNREADABLE -> {
                            dest.delete()
                            setNotes(
                                Fmt.zh(
                                    AppState.lang,
                                    "❌ 已拒絕：讀取唔到 APK 簽名，檔案可能損毀或未簽署。",
                                    "❌ Blocked: cannot read APK signature; file may be corrupt or unsigned."
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                dest.delete()
                runOnUiThread {
                    busy = false
                    verDownloadBtn.isEnabled = true
                    verProgress.visibility = View.GONE
                    setNotes(Fmt.zh(AppState.lang, "下載失敗：${e.message}", "Download failed: ${e.message}"))
                }
            }
        }.start()
    }

    private fun triggerInstall() {
        val f = UpdateChecker.apkFile(this)
        if (f.isFile && UpdateChecker.checkApk(this, f).code == CheckCode.OK) {
            try {
                UpdateChecker.install(this, f)
            } catch (e: Exception) {
                alert(
                    Fmt.zh(AppState.lang, "無法啟動安裝", "Cannot start installer"),
                    Fmt.zh(
                        AppState.lang,
                        "請到系統設定開啟「允許安裝未知來源」，或直接到 GitHub Releases 下載。\n\n${e.message}",
                        "Enable 'Install unknown apps' for HKETA in system settings, or download from GitHub Releases.\n\n${e.message}"
                    )
                )
            }
        } else {
            renderSignature()
            toast(Fmt.zh(AppState.lang, "簽名已失效，請重新下載", "Signature invalid, please re-download"))
        }
    }

    private fun setNotes(s: String) {
        verNotes.text = s
    }

    private fun renderNotes(current: String?) {
        // 語系切換時唔覆蓋「檢查／下載」結果訊息，只更新靜態標題，
        // 內容由下一次操作自然刷新，避免把失敗訊息洗走。
        if (current.isNullOrBlank()) {
            setNotes(
                Fmt.zh(
                    AppState.lang,
                    "會由 GitHub Releases 搵最新 .apk，下載後先核對簽名一致先至允許安裝。",
                    "Finds the latest .apk on GitHub Releases, then verifies signature consistency before allowing install."
                )
            )
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun alert(title: String, msg: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(Fmt.zh(AppState.lang, "關閉", "Close"), null)
            .show()
    }
}
