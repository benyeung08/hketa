package hk.eta.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    // --- 線路資料更新 ---------------------------------------------------------
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvUpdateStatus: TextView
    private lateinit var tvSources: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnUpdate: View
    private lateinit var spInterval: Spinner
    private lateinit var swWifi: SwitchCompat

    // --- 更新應用程式 ---------------------------------------------------------
    private lateinit var tvAppVersion: TextView
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
        // An automatic background update may have finished while this screen was paused.
        if (!RouteDataStore.refreshBusy()) render(RouteDataStore.meta())
    }

    private fun bindViews() {
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvSummary = findViewById(R.id.tvSummary)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)
        tvSources = findViewById(R.id.tvSources)
        progress = findViewById(R.id.progressUpdate)
        btnUpdate = findViewById(R.id.btnUpdate)
        spInterval = findViewById(R.id.spInterval)
        swWifi = findViewById(R.id.swWifi)

        tvAppVersion = findViewById(R.id.tvAppVersion)
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

        swWifi.isChecked = RouteDataPrefs.wifiOnly(this)
        swWifi.setOnCheckedChangeListener { _, checked ->
            RouteDataPrefs.setWifiOnly(this@SettingsActivity, checked)
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
        btnUpdate.isEnabled = false
        progress.visibility = View.VISIBLE
        tvUpdateStatus.text = getString(R.string.updating)
        lifecycleScope.launch {
            // Manual update deliberately ignores the Wi-Fi-only restriction.
            val report = withContext(Dispatchers.IO) { RouteDataStore.refresh(false) }
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
                val detail = when {
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
        tvAppVersion.text = getString(
            R.string.app_version,
            currentVersion,
            AppUpdatePrefs.installedVersionCode(this)
        )

        etRepo.setText(AppUpdatePrefs.repo(this))
        swAutoApp.isChecked = AppUpdatePrefs.autoCheck(this)
        swAutoApp.setOnCheckedChangeListener { _, enabled ->
            AppUpdatePrefs.setAutoCheck(this, enabled)
        }

        btnCheckApp.setOnClickListener {
            AppUpdatePrefs.setRepo(this, etRepo.text.toString())
            checkForUpdate(manual = true)
        }
        btnDownloadApk.setOnClickListener { downloadAndInstall() }
        btnReleasePage.setOnClickListener { openReleasePage() }

        if (AppUpdatePrefs.hasUpdate(this) && AppUpdatePrefs.latestTag(this).isNotEmpty()) {
            tvAppUpdateStatus.text = getString(
                R.string.app_update_cached_found,
                AppUpdatePrefs.latestTag(this)
            )
        }
    }

    private fun checkForUpdate(manual: Boolean) {
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
        tvAppUpdateStatus.text = getString(R.string.app_update_checking)

        lifecycleScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { AppUpdate.latest(repo) }
                latestRelease = release
                val newer = AppUpdate.compareVersions(currentVersion, release.tag) > 0
                AppUpdatePrefs.cacheResult(this@SettingsActivity, release.tag, newer)
                AppUpdatePrefs.markChecked(this@SettingsActivity)

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
                if (manual) {
                    Toast.makeText(this@SettingsActivity,
                        tvAppUpdateStatus.text, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                tvAppUpdateStatus.text = getString(R.string.app_update_failed, reason)
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
            }
        }
    }

    private fun downloadAndInstall() {
        val release = latestRelease ?: return
        if (!release.hasApk()) return
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
                    AppUpdate.download(
                        release.apkUrl, dir, "hketa-${release.tag}.apk"
                    ) { done, total ->
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
                    }
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
