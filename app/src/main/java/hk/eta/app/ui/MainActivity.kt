package hk.eta.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import hk.eta.app.R
import hk.eta.app.api.EtaRepository
import hk.eta.app.api.TimeUtils

/**
 * APP1933-style shell over the unchanged 8-operator data layer.
 * Four bottom tabs: 主頁 (nearby route cards) / 收藏 / 搜尋 (route stop
 * sequence + direction switch) / 更多 (Settings entry + disclaimer).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var vm: EtaViewModel
    private lateinit var routeEdit: EditText
    private lateinit var stopEdit: EditText
    private lateinit var etFilter: EditText
    private lateinit var companySpinner: Spinner
    private lateinit var radiusSpinner: Spinner
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var stopsHeader: TextView
    private lateinit var tvHomeEmpty: View
    private lateinit var tvFavEmpty: View
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var tabHome: TextView
    private lateinit var tabFav: TextView
    private lateinit var tabSearch: TextView
    private lateinit var tabMore: TextView
    private lateinit var pageHome: View
    private lateinit var pageFav: View
    private lateinit var pageSearch: View
    private lateinit var pageMore: View

    private lateinit var cardAdapter: RouteCardAdapter
    private lateinit var stopAdapter: StopAdapter
    private lateinit var favoriteAdapter: FavoriteAdapter

    private var currentTab = 0

    private val locationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Even when denied, LocationHelper falls back to Central and says so.
        vm.loadNearbyRoutes(selectedRadius())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        routeEdit = findViewById(R.id.routeEdit)
        stopEdit = findViewById(R.id.stopEdit)
        etFilter = findViewById(R.id.etFilter)
        companySpinner = findViewById(R.id.companySpinner)
        radiusSpinner = findViewById(R.id.radiusSpinner)
        titleText = findViewById(R.id.titleText)
        statusText = findViewById(R.id.statusText)
        stopsHeader = findViewById(R.id.stopsHeader)
        tvHomeEmpty = findViewById(R.id.tvHomeEmpty)
        tvFavEmpty = findViewById(R.id.tvFavEmpty)
        swipe = findViewById(R.id.swipe)
        tabHome = findViewById(R.id.tabHome)
        tabFav = findViewById(R.id.tabFav)
        tabSearch = findViewById(R.id.tabSearch)
        tabMore = findViewById(R.id.tabMore)
        pageHome = findViewById(R.id.pageHome)
        pageFav = findViewById(R.id.pageFav)
        pageSearch = findViewById(R.id.pageSearch)
        pageMore = findViewById(R.id.pageMore)

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

        vm = ViewModelProvider(this)[EtaViewModel::class.java]

        cardAdapter = RouteCardAdapter { card -> openCard(card) }
        stopAdapter = StopAdapter { stop ->
            vm.selectStop(stop)
            selectTab(0)
        }
        favoriteAdapter = FavoriteAdapter(
            onClick = { key ->
                vm.loadFavorite(key)
                selectTab(0)
            },
            onRemove = { key ->
                vm.removeFavorite(key)
                favoriteAdapter.submit(vm.favorites())
                Toast.makeText(this, "已移除收藏", Toast.LENGTH_SHORT).show()
            }
        )

        findViewById<RecyclerView>(R.id.homeRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = cardAdapter
        }
        findViewById<RecyclerView>(R.id.stopsRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = stopAdapter
        }
        findViewById<RecyclerView>(R.id.favRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = favoriteAdapter
        }

        swipe.setColorSchemeResources(R.color.brand, R.color.accent)
        swipe.setOnRefreshListener { refreshCurrent() }

        vm.state.observe(this) { render(it) }

        findViewById<View>(R.id.btnLoadStops).setOnClickListener {
            vm.loadStops(routeEdit.text.toString(), selectedCompany())
        }
        findViewById<View>(R.id.btnQuery).setOnClickListener {
            vm.queryDirect(routeEdit.text.toString(), stopEdit.text.toString(), selectedCompany())
            selectTab(0)
        }
        findViewById<View>(R.id.btnDirection).setOnClickListener { vm.toggleDirection() }
        findViewById<View>(R.id.btnNearby).setOnClickListener { requestNearby() }
        findViewById<View>(R.id.btnRefresh).setOnClickListener { refreshCurrent() }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnFavAdd).setOnClickListener {
            val key = vm.addCurrentFavorite()
            Toast.makeText(
                this,
                if (key == null) "請先點選車站或路線卡片" else "已加入收藏",
                Toast.LENGTH_SHORT
            ).show()
            favoriteAdapter.submit(vm.favorites())
        }
        findViewById<View>(R.id.btnFavList).setOnClickListener { selectTab(1) }

        etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                vm.filterStops(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        tabHome.setOnClickListener { selectTab(0) }
        tabFav.setOnClickListener { selectTab(1) }
        tabSearch.setOnClickListener { selectTab(2) }
        tabMore.setOnClickListener { selectTab(3) }

        selectTab(0)
        // APP1933 opens on nearby routes. LocationHelper falls back to Central
        // when permission / provider is unavailable, so this never blocks.
        vm.loadNearbyRoutes(selectedRadius())
    }

    private fun openCard(card: RouteCard) {
        routeEdit.setText(card.route)
        companySpinner.setSelection(companyPosition(card.company))
        vm.loadStops(card.route, card.company)
        selectTab(2)
    }

    private fun refreshCurrent() {
        val selection = vm.state.value?.selection
        if (currentTab == 0 && selection == null) {
            vm.loadNearbyRoutes(selectedRadius())
        } else {
            vm.refresh()
        }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        val tabs = listOf(tabHome, tabFav, tabSearch, tabMore)
        val pages = listOf(pageHome, pageFav, pageSearch, pageMore)
        for (i in tabs.indices) {
            pages[i].visibility = if (i == index) View.VISIBLE else View.GONE
            tabs[i].setTextColor(
                ContextCompat.getColor(
                    this,
                    if (i == index) R.color.brand else R.color.muted
                )
            )
            tabs[i].setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    if (i == index) R.color.route_badge else R.color.card
                )
            )
        }
    }

    private fun requestNearby() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED ||
            coarse == PackageManager.PERMISSION_GRANTED
        ) {
            vm.loadNearbyRoutes(selectedRadius())
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

    private fun companyPosition(company: String): Int = when (company) {
        EtaRepository.KMB -> 1
        EtaRepository.CTB -> 2
        EtaRepository.NLB -> 3
        EtaRepository.GMB -> 4
        EtaRepository.MTR -> 5
        EtaRepository.LRT -> 6
        EtaRepository.MTRB -> 7
        else -> 0
    }

    private fun render(state: UiState) {
        titleText.text = state.selection?.let {
            "HK ETA · ${state.route} → ${App1933Format.stopLabel(it.stopName, it.stopId)}"
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

        val cards = if (state.cards.isNotEmpty()) {
            state.cards
        } else {
            state.selection?.let {
                App1933Format.cardsFrom(state.etas, it.stopName, it.stopId, it.note)
            } ?: emptyList<RouteCard>()
        }
        cardAdapter.submit(cards)
        tvHomeEmpty.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE

        val query = state.searchQuery.trim()
        val visibleStops = state.stops.filter { stop ->
            val boundOk = state.activeBound.isEmpty() ||
                stop.bound == state.activeBound ||
                stop.bound == EtaRepository.NEARBY
            val queryOk = query.isEmpty() ||
                stop.stopName.contains(query, ignoreCase = true) ||
                stop.stopId.contains(query, ignoreCase = true)
            boundOk && queryOk
        }
        stopAdapter.submit(visibleStops)
        val dirLabel = if (state.activeBound.isEmpty()) "全部方向" else state.activeBound
        stopsHeader.text = "車站／月台（${visibleStops.size}） · $dirLabel"

        val favs = vm.favorites()
        favoriteAdapter.submit(favs)
        tvFavEmpty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE

        swipe.isRefreshing = state.loading
    }
}
