package com.hketa.app.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.hketa.app.R
import com.hketa.app.data.model.DataVersion
import com.hketa.app.data.model.UpdateResult
import java.text.SimpleDateFormat
import java.util.*

/**
 * Settings screen — lets the user update route / fare / timetable data for
 * each transit mode (BUS, MINIBUS, MTR, LRT) individually or all at once.
 *
 * Compatible with Android 7.0 (API 24): uses `SimpleDateFormat` and manual
 * ISO-8601 parsing instead of java.time.* APIs (which require API 26+).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var viewModel: RouteViewModel

    private lateinit var listUpdates: ListView
    private lateinit var btnUpdateAll: Button
    private lateinit var progress: ProgressBar
    private lateinit var textLastUpdated: TextView

    /**
     * Pre-Android 8 (API 26) does not support 'X' (ISO-8601 zone offset)
     * in SimpleDateFormat. We register two patterns and fall back between
     * them so parsing works on API 24+.
     */
    private val isoFormats = arrayOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
    )

    private val displayFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        viewModel = ViewModelProvider(this)[RouteViewModel::class.java]

        listUpdates = findViewById(R.id.listUpdates)
        btnUpdateAll = findViewById(R.id.btnUpdateAll)
        progress = findViewById(R.id.progressSettings)
        textLastUpdated = findViewById(R.id.textLastUpdated)

        btnUpdateAll.setOnClickListener { viewModel.updateAll() }

        listUpdates.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, pos, _ ->
                val item = listUpdates.adapter.getItem(pos) as? DataVersion
                item?.let { viewModel.updateMode(it.mode) }
            }

        viewModel.manifest.observe(this) { manifest ->
            if (manifest == null) return@observe
            listUpdates.adapter = UpdateAdapter(this, manifest.versions)
            textLastUpdated.text = "Manifest 更新時間: ${formatIso(manifest.generatedAt)}"
        }

        viewModel.lastResult.observe(this) { result ->
            if (result != null) showUpdateResult(result)
        }

        viewModel.updateSummary.observe(this) { summary ->
            if (summary != null) {
                val msg = summary.results.joinToString("\n") { r ->
                    "${r.mode}: ${statusLabel(r.status)} (v${r.fromVersion} → v${r.toVersion})"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loading.observe(this) { progress.visibility = if (it) View.VISIBLE else View.GONE }
        viewModel.error.observe(this) {
            if (!it.isNullOrBlank()) Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }

        viewModel.loadManifest()
    }

    private fun showUpdateResult(r: UpdateResult) {
        val msg = when (r.status) {
            "updated" -> "${r.mode} 資料已更新 v${r.fromVersion} → v${r.toVersion}"
            "up-to-date" -> "${r.mode} 已是最新版本 v${r.toVersion}"
            else -> "${r.mode} 更新失敗：${r.message ?: "unknown error"}"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun statusLabel(status: String): String = when (status) {
        "updated" -> "已更新"
        "up-to-date" -> "已最新"
        else -> "失敗"
    }

    private fun formatIso(iso: String): String {
        if (iso.isEmpty()) return iso
        // Normalise trailing 'Z' (UTC) so the fixed formatters can read it.
        val normalised = iso.replace("Z", "")
        for (fmt in isoFormats) {
            try {
                val date = fmt.parse(normalised)
                if (date != null) return displayFmt.format(date)
            } catch (_: Throwable) {
                // try the next pattern
            }
        }
        // Last resort: strip time portion and return the date only.
        return iso.substringBefore('T')
    }
}
