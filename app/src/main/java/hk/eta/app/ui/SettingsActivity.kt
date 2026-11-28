package hk.eta.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import hk.eta.app.R
import hk.eta.app.api.AppUpdate
import hk.eta.app.api.RouteDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Settings screen: route-catalogue update + in-app application update. */
class SettingsActivity : AppCompatActivity() {

    // --- 懸浮液態玻璃導航欄 --------------------------------------------------
    private lateinit var swNavGlass: SwitchCompat
    private lateinit var tvNavGlassStatus: TextView
    private lateinit var spNavMaterial: Spinner

    // --- ⟳ ETA 自動刷新 -------------------------------------------------------
    private lateinit var swEtaAutoRefresh: SwitchCompat
    private lateinit var etaRefreshPickerRoot: View
    private lateinit var etaRefreshPickerValue: TextView
    private lateinit var tvEtaRefreshStatus: TextView

    // --- 定位附近路線嘅距離（對照截圖「50 ▼」）------------------------------
    private lateinit var radiusPickerRoot: View
    private lateinit var radiusPickerValue: TextView

    // --- 線路資料更新 ---------------------------------------------------------
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var tvSources: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnUpdate: View
    private lateinit var spInterval: Spinner

    // --- 更新應用程式：深色版本膠囊 -------------------------------------------
    private lateinit var tvAppVersion: TextView
    private lateinit var tvAppBuild: TextView
    private lateinit var pillRefresh: ImageView
    private lateinit var pillHistory: ImageView
    private lateinit var pillCopy: ImageView
    private lateinit var tvPillStatus: TextView
    private lateinit var btnAdvanced: TextView
    private lateinit var advancedAppUpdate: View
    private lateinit var etRepo: EditText
    private lateinit var swAutoApp: SwitchCompat
    private lateinit var btnCheckApp: View
    private lateinit var btnDownloadApk: View
    private lateinit var btnReleasePage: View
    private lateinit var progressApp: ProgressBar
    private lateinit var tvAppUpdateStatus: TextView
    private lateinit var tvReleaseNotes: TextView

    // --- App 簽名一致性 -------------------------------------------------------
    private lateinit var tvSigningFingerprint: TextView
    private lateinit var tvSigningStatus: TextView
    private lateinit var tvSigningDetail: TextView

    private var currentVersion = "0"
    private var latestRelease: AppUpdate.Release? = null
    private var pendingApk: File? = null
    private var checking = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val unknownSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = pendingApk
        if (apk != null && ApkInstaller.canRequestInstalls(this)) {
            val check = ApkSignature.verify(
                this, apk, getString(R.string.expected_signing_sha256)
            )
            renderSignatureCheck(check)
            if (check.ok && ApkInstaller.install(this, apk)) {
                tvAppUpdateStatus.text = getString(R.string.app_update_download_done)
            } else if (!check.ok) {
                apk.delete()
                pendingApk = null
                tvAppUpdateStatus.text = getString(
                    R.string.app_update_signature_failed, check.reason
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        RouteDataStore.init(applicationContext)

        bindViews()
        setupNavGlassSection()
        setupEtaRefreshSection()
        setupRadiusSection()
        setupCatalogueSection()
        setupAppUpdateSection()
        showInstalledFingerprint()

        render(RouteDataStore.meta())
        if (AppUpdatePrefs.isDue(this) &&
            AppUpdatePrefs.repo(this).isNotBlank()
        ) {
            checkForUpdate(manual = false)
        }
    }

    override fun onResume() {
        super.onResume()
        // 由系統設定改外觀返嚟：狀態文字同步
        renderNavGlassStatus()
        renderEtaRefreshSection()
        // An automatic background update may have finished while this screen was paused.
        if (!RouteDataStore.refreshBusy()) render(RouteDataStore.meta())
    }

    private fun bindViews() {
        swNavGlass = findViewById(R.id.swNavGlass)
        tvNavGlassStatus = findViewById(R.id.tvNavGlassStatus)
        spNavMaterial = findViewById(R.id.spNavMaterial)

        swEtaAutoRefresh = findViewById(R.id.swEtaAutoRefresh)
        etaRefreshPickerRoot = findViewById(R.id.etaRefreshPickerRoot)
        etaRefreshPickerValue = findViewById(R.id.etaRefreshPickerValue)
        tvEtaRefreshStatus = findViewById(R.id.tvEtaRefreshStatus)

        radiusPickerRoot = findViewById(R.id.radiusPickerRoot)
        radiusPickerValue = findViewById(R.id.radiusPickerValue)

        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvSummary = findViewById(R.id.tvSummary)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)
        tvSources = findViewById(R.id.tvSources)
        progress = findViewById(R.id.progressUpdate)
        btnUpdate = findViewById(R.id.btnUpdate)
        spInterval = findViewById(R.id.spInterval)

        tvAppVersion = findViewById(R.id.tvAppVersion)
        tvAppBuild = findViewById(R.id.tvAppBuild)
        pillRefresh = findViewById(R.id.pillRefresh)
        pillHistory = findViewById(R.id.pillHistory)
        pillCopy = findViewById(R.id.pillCopy)
        tvPillStatus = findViewById(R.id.tvPillStatus)
        btnAdvanced = findViewById(R.id.btnAdvanced)
        advancedAppUpdate = findViewById(R.id.advancedAppUpdate)
        etRepo = findViewById(R.id.etRepo)
        swAutoApp = findViewById(R.id.swAutoApp)
        btnCheckApp = findViewById(R.id.btnCheckApp)
        btnDownloadApk = findViewById(R.id.btnDownloadApk)
        btnReleasePage = findViewById(R.id.btnReleasePage)
        progressApp = findViewById(R.id.progressApp)
        tvAppUpdateStatus = findViewById(R.id.tvAppUpdateStatus)
        tvReleaseNotes = findViewById(R.id.tvReleaseNotes)

        tvSigningFingerprint = findViewById(R.id.tvSigningFingerprint)
        tvSigningStatus = findViewById(R.id.tvSigningStatus)
        tvSigningDetail = findViewById(R.id.tvSigningDetail)
    }

    // -------------------------------------------------- 懸浮液態玻璃導航欄

    private fun setupNavGlassSection() {
        swNavGlass.isChecked = NavGlassPrefs.followSystem(this)
        swNavGlass.setOnCheckedChangeListener { _, enabled ->
            NavGlassPrefs.setFollowSystem(this, enabled)
            renderNavGlassStatus()
        }

        // CN_ADVANCED_MATERIAL: 0=跟隨系統 / 1=完整液態 / 2=簡化靜態。
        // 只寫本 App SharedPreferences，唔掂任何系統／廠商設定。
        val labels = listOf(
            getString(R.string.nav_glass_material_follow),
            getString(R.string.nav_glass_material_full),
            getString(R.string.nav_glass_material_simple)
        )
        spNavMaterial.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spNavMaterial.setSelection(NavGlassPrefs.materialMode(this))
        spNavMaterial.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                NavGlassPrefs.setMaterialMode(this@SettingsActivity, position)
                renderNavGlassStatus()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        renderNavGlassStatus()
    }

    private fun renderNavGlassStatus() {
        tvNavGlassStatus.text = getString(
            R.string.nav_glass_status, NavGlass.status(this)
        )
    }

    // -------------------------------------------------- ⟳ ETA 自動刷新

    private fun setupEtaRefreshSection() {
        swEtaAutoRefresh.isChecked = EtaRefreshPrefs.enabled(this)
        swEtaAutoRefresh.setOnCheckedChangeListener { _, on ->
            EtaRefreshPrefs.setEnabled(this, on)
            renderEtaRefreshSection()
        }
        etaRefreshPickerRoot.setOnClickListener {
            if (!EtaRefreshPrefs.enabled(this)) {
                Toast.makeText(
                    this, R.string.eta_refresh_status_off, Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val labels = resources.getStringArray(
                R.array.eta_refresh_interval_labels
            )
            val current = EtaRefreshPrefs.intervalIndex(this)
            AlertDialog.Builder(this)
                .setTitle(R.string.eta_refresh_interval_title)
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    EtaRefreshPrefs.setIntervalMs(
                        this, EtaRefreshPrefs.INTERVALS_MS[which]
                    )
                    renderEtaRefreshSection()
                    dialog.dismiss()
                    Toast.makeText(
                        this,
                        getString(
                            R.string.eta_refresh_settings_on, labels[which]
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(R.string.pill_cancel, null)
                .show()
        }
        renderEtaRefreshSection()
    }

    private fun renderEtaRefreshSection() {
        val on = EtaRefreshPrefs.enabled(this)
        etaRefreshPickerValue.text =
            (EtaRefreshPrefs.selectedIntervalMs(this) / 1000L).toString()
        etaRefreshPickerRoot.alpha = if (on) 1f else 0.45f
        etaRefreshPickerRoot.isEnabled = on
        tvEtaRefreshStatus.text = EtaRefreshPrefs.settingsLabel(this)
    }

    // -------------------------------------------------- 定位附近路線嘅距離

    /** Big-number picker: tapping the bar opens a single-choice radius dialog. */
    private fun setupRadiusSection() {
        renderRadiusPicker()
        radiusPickerRoot.setOnClickListener {
            val labels = resources.getStringArray(R.array.radius_labels)
            val current = NearbyRadiusPrefs.radiusIndex(this)
            AlertDialog.Builder(this)
                .setTitle(R.string.nearby_radius_title)
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    NearbyRadiusPrefs.setRadiusMeters(
                        this, NearbyRadiusPrefs.RADIUS_METERS[which]
                    )
                    renderRadiusPicker()
                    dialog.dismiss()
                    Toast.makeText(
                        this,
                        getString(R.string.nearby_radius_saved, labels[which]),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(R.string.pill_cancel, null)
                .show()
        }
    }

    private fun renderRadiusPicker() {
        radiusPickerValue.text = NearbyRadiusPrefs.radiusMeters(this).toString()
    }

    // ------------------------------------------------------------- 線路資料更新

    private fun setupCatalogueSection() {
        spInterval.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.update_interval_labels)
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spInterval.setSelection(RouteDataPrefs.intervalIndex(this))
        spInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                RouteDataPrefs.setIntervalDays(
                    this@SettingsActivity,
                    RouteDataPrefs.INTERVAL_DAYS[position]
                )
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnUpdate.setOnClickListener { doUpdate() }
        findViewById<View>(R.id.btnClear).setOnClickListener {
            RouteDataStore.clear()
            render(RouteDataStore.meta())
            tvUpdateStatus.text = getString(R.string.clear_done)
            Toast.makeText(this, R.string.clear_done, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun doUpdate() {
        // NO_NETWORK_GATE：唔預先擋低；來源失敗由 Report 如實列出。
        btnUpdate.isEnabled = false
        progress.visibility = View.VISIBLE
        tvUpdateStatus.text = getString(R.string.updating)
        lifecycleScope.launch {
            // Any network: the Wi-Fi-only restriction was removed.
            val report = withContext(Dispatchers.IO) { RouteDataStore.refresh() }
            btnUpdate.isEnabled = true
            progress.visibility = View.GONE
            tvUpdateStatus.text = if (report.skipped || report.busy) {
                report.message
            } else {
                "${report.message} · ${getString(R.string.update_note_live_eta)}"
            }
            render(report)
            if (report.okCount > 0) RouteDataPrefs.markChecked(this@SettingsActivity)
            Toast.makeText(this@SettingsActivity, report.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun render(report: RouteDataStore.Report) {
        tvLastUpdate.text = if (report.updatedAt > 0L) {
            getString(R.string.last_update_at, hkt(report.updatedAt))
        } else {
            getString(R.string.last_update_never)
        }
        tvSummary.text = getString(
            R.string.summary_format, report.okCount, report.total, report.records
        )
        tvSources.text = if (report.sources.isEmpty()) {
            getString(R.string.sources_placeholder)
        } else {
            report.sources.joinToString("\n") { s ->
                // GOV_IN_ROUTE_UPDATE: 政府班次資料係正式來源，顯示「N 個 GTFS
                // 檔案」而唔係當普通「N 筆」目錄紀錄；失敗／略過如實列出。
                val detail = when {
                    s.file == RouteDataStore.GOV_SOURCE && s.ok ->
                        getString(R.string.sources_gov_files, s.count)
                    s.file == RouteDataStore.GOV_SOURCE && s.skipped ->
                        getString(R.string.sources_gov_skipped, s.error)
                    s.ok && s.count > 0 -> "${s.count} 筆"
                    s.ok -> "按需建立"
                    s.skipped -> "選用／按需略過：${s.error}"
                    else -> "失敗：${s.error}"
                }
                "• ${s.label} — $detail"
            }
        }
    }

    // ------------------------------------------------------------- 更新應用程式

    private fun setupAppUpdateSection() {
        currentVersion = AppUpdatePrefs.installedVersion(this)
        // 對照截圖：v2.8.4 · 83。版本名統一加 "v"，右邊係 versionCode（build）。
        tvAppVersion.text = versionLabel()
        tvAppBuild.text = AppUpdatePrefs.installedVersionCode(this).toString()

        etRepo.setText(AppUpdatePrefs.repo(this))
        swAutoApp.isChecked = AppUpdatePrefs.autoCheck(this)
        swAutoApp.setOnCheckedChangeListener { _, enabled ->
            AppUpdatePrefs.setAutoCheck(this, enabled)
            renderPillStatus()
        }

        // 三個捷徑：⟳ 重新檢查 / 🕐 更新記錄 / ⧉ 複製版本資訊
        pillRefresh.setOnClickListener { triggerCheck() }
        pillHistory.setOnClickListener { showUpdateHistory() }
        pillCopy.setOnClickListener { copyVersionInfo() }
        btnAdvanced.setOnClickListener { toggleAdvanced() }

        btnCheckApp.setOnClickListener { triggerCheck() }
        btnDownloadApk.setOnClickListener { downloadAndInstall() }
        btnReleasePage.setOnClickListener { openReleasePage() }

        if (AppUpdatePrefs.hasUpdate(this) && AppUpdatePrefs.latestTag(this).isNotEmpty()) {
            tvAppUpdateStatus.text = getString(
                R.string.app_update_cached_found,
                AppUpdatePrefs.latestTag(this)
            )
        }
        renderPillStatus()
    }

    private fun versionLabel(): String = "v" + currentVersion.removePrefix("v")

    /** Single entry point for both the pill's ⟳ and the advanced 檢查更新 button. */
    private fun triggerCheck() {
        AppUpdatePrefs.setRepo(this, etRepo.text.toString())
        checkForUpdate(manual = true)
    }

    private fun toggleAdvanced() {
        if (advancedAppUpdate.visibility == View.VISIBLE) {
            advancedAppUpdate.visibility = View.GONE
            btnAdvanced.text = getString(R.string.pill_advanced_open)
        } else {
            expandAdvanced()
        }
    }

    /** Also opened automatically when a newer release is found. */
    private fun expandAdvanced() {
        if (advancedAppUpdate.visibility != View.VISIBLE) {
            advancedAppUpdate.visibility = View.VISIBLE
            btnAdvanced.text = getString(R.string.pill_advanced_close)
        }
    }

    private fun renderPillStatus(failed: Boolean = false) {
        val last = AppUpdatePrefs.lastCheck(this)
        val tag = AppUpdatePrefs.latestTag(this)
        val state = when {
            failed -> getString(R.string.pill_status_failed)
            AppUpdatePrefs.hasUpdate(this) && tag.isNotEmpty() ->
                getString(R.string.pill_status_new, tag)
            tag.isNotEmpty() -> getString(R.string.pill_status_latest)
            else -> getString(R.string.pill_status_never)
        }
        tvPillStatus.text = if (last > 0L) {
            getString(R.string.pill_status_checked, hkt(last), state)
        } else {
            state
        }
    }

    /** 🕐 — last check, cached result, repo and auto-check state. Read-only. */
    private fun showUpdateHistory() {
        val last = AppUpdatePrefs.lastCheck(this)
        val tag = AppUpdatePrefs.latestTag(this)
        val state = when {
            AppUpdatePrefs.hasUpdate(this) && tag.isNotEmpty() ->
                getString(R.string.pill_status_new, tag)
            tag.isNotEmpty() -> getString(R.string.pill_status_latest)
            else -> getString(R.string.pill_status_never)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pill_history_title)
            .setMessage(
                getString(
                    R.string.pill_history_body,
                    versionLabel(),
                    AppUpdatePrefs.installedVersionCode(this),
                    AppUpdatePrefs.repo(this),
                    getString(
                        if (AppUpdatePrefs.autoCheck(this)) R.string.pill_on else R.string.pill_off
                    ),
                    if (last > 0L) hkt(last) else getString(R.string.pill_history_never),
                    tag.ifEmpty { getString(R.string.pill_history_no_tag) },
                    state
                )
            )
            .setPositiveButton(R.string.pill_ok, null)
            .show()
    }

    /** ⧉ — copy "vX · build · owner/repo" for bug reports. */
    private fun copyVersionInfo() {
        val info = versionLabel() +
                " · " + AppUpdatePrefs.installedVersionCode(this) +
                " · " + AppUpdatePrefs.repo(this)
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("HK ETA version", info))
        Toast.makeText(this, getString(R.string.pill_copied, info), Toast.LENGTH_SHORT).show()
    }

    private fun checkForUpdate(manual: Boolean) {
        // NO_NETWORK_GATE：唔預先擋低；catch 會如實報 app_update_failed。
        val repo = etRepo.text.toString().trim()
        if (repo.isEmpty()) {
            tvAppUpdateStatus.text = getString(R.string.app_update_no_repo)
            if (manual) Toast.makeText(this, R.string.app_update_no_repo, Toast.LENGTH_SHORT).show()
            return
        }
        if (checking) return
        checking = true
        AppUpdatePrefs.setRepo(this, repo)
        btnCheckApp.isEnabled = false
        btnDownloadApk.isEnabled = false
        btnReleasePage.isEnabled = false
        pillRefresh.isEnabled = false
        pillRefresh.alpha = 0.45f
        tvAppUpdateStatus.text = getString(R.string.app_update_checking)
        tvPillStatus.text = getString(R.string.pill_status_checking)

        lifecycleScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { AppUpdate.latest(repo) }
                latestRelease = release
                val newer = AppUpdate.compareVersions(currentVersion, release.tag) > 0
                AppUpdatePrefs.cacheResult(this@SettingsActivity, release.tag, newer)
                AppUpdatePrefs.markChecked(this@SettingsActivity)
                renderPillStatus()

                tvAppUpdateStatus.text = when {
                    newer && release.hasApk() ->
                        getString(R.string.app_update_found, release.tag,
                            AppUpdate.humanSize(release.apkSize))
                    newer -> getString(R.string.app_update_no_apk, release.tag)
                    else -> getString(R.string.app_update_up_to_date, release.tag)
                }
                val pre = if (release.prerelease) getString(R.string.app_update_prerelease) else ""
                tvReleaseNotes.text = buildString {
                    append(release.name).append(' ').append(pre).append('\n')
                    if (release.publishedAt.isNotEmpty()) {
                        append(release.publishedAt.take(10)).append('\n')
                    }
                    append(release.notes.take(700))
                }.trim()

                btnDownloadApk.isEnabled = newer && release.hasApk()
                btnReleasePage.isEnabled =
                    release.htmlUrl.isNotEmpty() && AppUpdate.isSafeUrl(release.htmlUrl)
                if (newer) expandAdvanced()
                if (manual) {
                    Toast.makeText(this@SettingsActivity,
                        tvAppUpdateStatus.text, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                tvAppUpdateStatus.text = getString(R.string.app_update_failed, reason)
                renderPillStatus(failed = true)
                if (reason.contains("rate limit", ignoreCase = true)) {
                    tvReleaseNotes.text = getString(R.string.app_update_rate_limit)
                }
                if (manual) {
                    Toast.makeText(this@SettingsActivity,
                        tvAppUpdateStatus.text, Toast.LENGTH_SHORT).show()
                }
            } finally {
                checking = false
                btnCheckApp.isEnabled = true
                pillRefresh.isEnabled = true
                pillRefresh.alpha = 1f
            }
        }
    }

    private fun downloadAndInstall() {
        val release = latestRelease ?: return
        if (!release.hasApk()) return
        expandAdvanced()
        btnDownloadApk.isEnabled = false
        progressApp.isIndeterminate = false
        progressApp.progress = 0
        progressApp.visibility = View.VISIBLE
        tvAppUpdateStatus.text = getString(
            R.string.app_update_downloading, ""
        )

        lifecycleScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    val dir = File(filesDir, "updates")
                    // API_AUDIT_C1: 校驗 Release 宣告嘅 size 同（如有）
                    // body 入面嘅 sha256，唔符合就直接拒裝。
                    AppUpdate.downloadVerified(
                        release.apkUrl, dir, "hketa-${release.tag}.apk",
                        { done, total ->
                        mainHandler.post {
                            progressApp.progress = if (total > 0L) {
                                (done * 100L / total).toInt().coerceIn(0, 100)
                            } else 0
                            val size = if (total > 0L) {
                                "${AppUpdate.humanSize(done)} / ${AppUpdate.humanSize(total)}"
                            } else AppUpdate.humanSize(done)
                            tvAppUpdateStatus.text =
                                getString(R.string.app_update_downloading, size)
                        }
                    },
                        AppUpdate.sha256FromNotes(release.notes), release.apkSize)
                }
                val check = withContext(Dispatchers.IO) {
                    ApkSignature.verify(
                        this@SettingsActivity,
                        apk,
                        getString(R.string.expected_signing_sha256)
                    )
                }
                renderSignatureCheck(check)
                if (!check.ok) {
                    pendingApk = null
                    apk.delete()
                    tvAppUpdateStatus.text = getString(
                        R.string.app_update_signature_failed, check.reason
                    )
                    return@launch
                }

                pendingApk = apk
                if (ApkInstaller.install(this@SettingsActivity, apk)) {
                    tvAppUpdateStatus.text = getString(R.string.app_update_download_done)
                } else {
                    tvAppUpdateStatus.text = getString(R.string.app_update_need_permission)
                    unknownSourcesLauncher.launch(
                        ApkInstaller.unknownSourcesIntent(this@SettingsActivity)
                    )
                }
            } catch (e: Exception) {
                tvAppUpdateStatus.text = getString(
                    R.string.app_update_download_failed, e.message ?: "未知錯誤"
                )
            } finally {
                progressApp.visibility = View.GONE
                btnDownloadApk.isEnabled = true
            }
        }
    }

    private fun openReleasePage() {
        val url = latestRelease?.htmlUrl ?: return
        if (!AppUpdate.isSafeUrl(url)) return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    // ------------------------------------------------------------- 簽名一致性

    private fun showInstalledFingerprint() {
        val certs = ApkSignature.installedFingerprints(this)
        tvSigningFingerprint.text = if (certs.isEmpty()) {
            getString(R.string.signing_unavailable)
        } else {
            getString(R.string.signing_fingerprint, certs.joinToString("\n"))
        }
    }

    private fun renderSignatureCheck(check: ApkSignature.Check) {
        tvSigningStatus.text = if (check.ok) {
            getString(R.string.signing_status_ok, check.reason)
        } else {
            getString(R.string.signing_status_fail, check.reason)
        }
        tvSigningDetail.text = getString(
            R.string.signing_detail,
            check.installed.joinToString("\n").ifEmpty { "—" },
            check.downloaded.joinToString("\n").ifEmpty { "—" }
        )
    }

    private fun hkt(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        format.timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
        return format.format(Date(millis)) + " HKT"
    }
}
