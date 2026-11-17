package hk.eta.app.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import hk.eta.app.R
import hk.eta.app.api.TimeUtils

class MainActivity : AppCompatActivity() {

    private lateinit var vm: EtaViewModel
    private lateinit var routeEdit: EditText
    private lateinit var stopEdit: EditText
    private lateinit var companySpinner: Spinner
    private lateinit var radiusSpinner: Spinner
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var stopsHeader: TextView
    private lateinit var swipe: SwipeRefreshLayout

    private val stopAdapter = StopAdapter { vm.selectStop(it) }
    private val etaAdapter = EtaAdapter()

    private val locationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Even when denied, LocationHelper falls back to Central and explains it.
        vm.loadNearby(selectedRadius())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        routeEdit = findViewById(R.id.routeEdit)
        stopEdit = findViewById(R.id.stopEdit)
        companySpinner = findViewById(R.id.companySpinner)
        radiusSpinner = findViewById(R.id.radiusSpinner)
        titleText = findViewById(R.id.titleText)
        statusText = findViewById(R.id.statusText)
        stopsHeader = findViewById(R.id.stopsHeader)
        swipe = findViewById(R.id.swipe)

        companySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.company_labels)
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val modeHints = resources.getStringArray(R.array.mode_hints)
        companySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                routeEdit.hint = modeHints[position]
                stopEdit.hint = getString(R.string.hint_stop)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        radiusSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.radius_labels)
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        radiusSpinner.setSelection(1)

        findViewById<RecyclerView>(R.id.stopsRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = stopAdapter
        }
        findViewById<RecyclerView>(R.id.etasRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = etaAdapter
        }

        swipe.setColorSchemeResources(R.color.brand, R.color.accent)
        swipe.setOnRefreshListener { vm.refresh() }

        vm = ViewModelProvider(this)[EtaViewModel::class.java]
        vm.state.observe(this) { render(it) }

        routeEdit.setText("40")

        findViewById<View>(R.id.btnLoadStops).setOnClickListener {
            vm.loadStops(routeEdit.text.toString(), selectedCompany())
        }
        findViewById<View>(R.id.btnQuery).setOnClickListener {
            vm.queryDirect(routeEdit.text.toString(), stopEdit.text.toString(), selectedCompany())
        }
        findViewById<View>(R.id.btnNearby).setOnClickListener { requestNearby() }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnFavAdd).setOnClickListener {
            val key = vm.addCurrentFavorite()
            Toast.makeText(
                this,
                if (key == null) "請先點選一個車站" else "已加入收藏",
                Toast.LENGTH_SHORT
            ).show()
        }
        findViewById<View>(R.id.btnFavList).setOnClickListener { showFavorites() }
    }

    private fun requestNearby() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            vm.loadNearby(selectedRadius())
        } else {
            locationPermissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun selectedCompany(): String =
        EtaViewModel.companyAt(companySpinner.selectedItemPosition)

    private fun selectedRadius(): Int = when (radiusSpinner.selectedItemPosition) {
        0 -> 300
        1 -> 500
        2 -> 1000
        else -> 2000
    }

    private fun render(state: UiState) {
        titleText.text = state.selection?.let {
            "HK ETA · ${state.route} → ${it.stopName.ifEmpty { it.stopId }}"
        } ?: getString(R.string.app_title)

        val badge = if (AppUpdatePrefs.hasUpdate(this) &&
            AppUpdatePrefs.latestTag(this).isNotEmpty()
        ) {
            getString(
                R.string.app_update_new_badge,
                AppUpdatePrefs.latestTag(this)
            ) + " · "
        } else ""
        statusText.text = badge + if (state.updatedAt > 0L) {
            "${state.message} · ${TimeUtils.hhmm(state.updatedAt)} 更新"
        } else {
            state.message
        }
        stopsHeader.text = "車站／月台（${state.stops.size}）"
        stopAdapter.submit(state.stops)
        etaAdapter.submit(state.etas)
        swipe.isRefreshing = state.loading
    }

    private fun showFavorites() {
        val items = vm.favorites()
        if (items.isEmpty()) {
            Toast.makeText(this, "尚未收藏任何車站", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = items.map { it.replace("|", " · ") }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("我的收藏（點選查詢）")
            .setItems(labels) { _, which -> vm.loadFavorite(items[which]) }
            .setNegativeButton("關閉", null)
            .show()
    }
}
