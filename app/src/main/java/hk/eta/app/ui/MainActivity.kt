package hk.eta.app.ui

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import hk.eta.app.api.JointRoutes
import hk.eta.app.api.TimeUtils

/**
 * SEARCH_UI_2026-11-23 — 搜尋分頁對照截圖：8 個交通類別 tab、營運商 chips、
 * 綠框「路線號碼」、我的書籤／搜尋紀錄。顏色全部由既有 @color/search_*
 * 同既有 drawable 決定，**呢輪冇新增／冇改任何色值**，亦唔跟隨系統深／淺色。
 * 底層 8 種查詢模式、官方目錄預載、點列開無地圖詳情頁、站序／方向／篩選
 * 全部保留；其餘分頁（主頁／收藏／設置）維持原本跟隨系統外觀。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var vm: EtaViewModel
    private lateinit var routeEdit: EditText
    private lateinit var stopEdit: EditText
    private lateinit var etFilter: EditText
    private lateinit var companySpinner: Spinner
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var tvAutoRefreshStatus: TextView
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
    private lateinit var mainHeader: View
    private lateinit var bottomTabs: View
    private lateinit var routeInput: EditText
    private lateinit var searchResultsView: View
    private lateinit var searchHomeView: View
    private lateinit var stopSequenceView: View
    private lateinit var searchEmpty: TextView
    private lateinit var tvBookmarkEmpty: TextView
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var tvPreloadHint: View
    private lateinit var tvCategoryNotice: TextView
    private lateinit var searchChipScroll: View
    private lateinit var btnAdvanced: View
    // SEARCH_UI_KOTLIN_FIX: 呢個欄位喺舊 APP1933 區塊，改版時一併被 cut 走，
    // 但 autoLocateIfAllowed / showPermissionHint / render 仍然用緊 →
    // Kotlin 「Unresolved reference: permissionHint」令 assembleDebug 失敗。
    // 必須連 findViewById 一齊保留，唔可以只加宣告。
    private lateinit var permissionHint: TextView
    private lateinit var catTabs: List<TextView>
    private lateinit var catUnderlines: List<View>
    private lateinit var chips: List<TextView>

    private lateinit var cardAdapter: RouteCardAdapter
    private lateinit var stopAdapter: StopAdapter
    private lateinit var favoriteAdapter: FavoriteAdapter
    private lateinit var searchAdapter: SearchHistoryAdapter
    private lateinit var historyAdapter: SearchHistoryAdapter
    private lateinit var bookmarkAdapter: BookmarkAdapter

    private var currentTab = 0
    // true = 進階站序模式（保留原有 8 模式／站號／方向／篩選）
    private var inSequence = false
    // 0 港鐵 1 巴士（預設） 2 專線小巴 3 渡輪 4 電車 5 的士 6 非專線小巴 7 居民巴士服務
    private var searchCategory = 1
    // 0 所有巴士類別 1 城巴 2 九巴 3 龍運 4 嶼巴 5 愉景灣／馬灣
    private var busChip = 0
    // 自動定位只喺「已授權」後發生；未授權唔會靜默用中環假定位。
    private var locationPromptShown = false
    private var nearbyLoaded = false
    // 由設定頁改咗半徑返嚟：onResume 要重新載入附近路線
    private var lastRadius = -1

    private val locationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        if (granted) {
            // 先獲得權限 → 先自動定位（唔使撳任何掣）
            permissionHint.visibility = View.GONE
            statusText.text = getString(R.string.location_auto)
            nearbyLoaded = true
            vm.loadNearbyRoutes(selectedRadius())
        } else {
            // 拒絕：唔打 API、唔 fallback 中環，只顯示可點嘅授權提示
            nearbyLoaded = false
            vm.awaitLocationPermission()
            showPermissionHint()
            statusText.text = getString(R.string.location_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lastRadius = NearbyRadiusPrefs.radiusMeters(this)

        routeEdit = findViewById(R.id.routeEdit)
        stopEdit = findViewById(R.id.stopEdit)
        etFilter = findViewById(R.id.etFilter)
        companySpinner = findViewById(R.id.companySpinner)
        titleText = findViewById(R.id.titleText)
        statusText = findViewById(R.id.statusText)
        tvAutoRefreshStatus = findViewById(R.id.tvAutoRefreshStatus)
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
        mainHeader = findViewById(R.id.mainHeader)
        bottomTabs = findViewById(R.id.bottomTabs)
        routeInput = findViewById(R.id.routeInput)
        searchResultsView = findViewById(R.id.searchResultsView)
        searchHomeView = findViewById(R.id.searchHomeView)
        stopSequenceView = findViewById(R.id.stopSequenceView)
        searchEmpty = findViewById(R.id.searchEmpty)
        tvBookmarkEmpty = findViewById(R.id.tvBookmarkEmpty)
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty)
        tvPreloadHint = findViewById(R.id.tvPreloadHint)
        tvCategoryNotice = findViewById(R.id.tvCategoryNotice)
        searchChipScroll = findViewById(R.id.searchChipScroll)
        btnAdvanced = findViewById(R.id.btnAdvanced)
        permissionHint = findViewById(R.id.permissionHint)
        catTabs = listOf(
            R.id.catMtr, R.id.catBus, R.id.catGmb, R.id.catFerry,
            R.id.catTram, R.id.catTaxi, R.id.catNplb, R.id.catResident
        ).map { findViewById<TextView>(it) }
        catUnderlines = listOf(
            R.id.catMtrUnderline, R.id.catBusUnderline, R.id.catGmbUnderline,
            R.id.catFerryUnderline, R.id.catTramUnderline, R.id.catTaxiUnderline,
            R.id.catNplbUnderline, R.id.catResidentUnderline
        ).map { findViewById<View>(it) }
        chips = listOf(
            R.id.chipAll, R.id.chipCtb, R.id.chipKmb,
            R.id.chipLwb, R.id.chipNlb, R.id.chipDb
        ).map { findViewById<TextView>(it) }

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
        // 即時結果／搜尋紀錄／我的書籤全部重用截圖式紀錄列；
        // 顏色由 item_route_result.xml / item_bookmark.xml 固定，程式唔改色值。
        searchAdapter = SearchHistoryAdapter(
            onClick = { item -> openHistoryItem(item) },
            onRemove = { _ -> Unit }
        )
        historyAdapter = SearchHistoryAdapter(
            onClick = { item -> openHistoryItem(item) },
            onRemove = { item ->
                SearchHistoryPrefs.remove(this, item)
                refreshHistory()
            }
        )
        bookmarkAdapter = BookmarkAdapter { key ->
            vm.loadFavorite(key)
            selectTab(0)
        }

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
        findViewById<RecyclerView>(R.id.searchResultsRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = searchAdapter
        }
        findViewById<RecyclerView>(R.id.historyRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = historyAdapter
        }
        findViewById<RecyclerView>(R.id.bookmarkRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = bookmarkAdapter
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
        // 呢個唔係定位掣：只負責再問權限／去系統設定
        permissionHint.setOnClickListener { requestLocationConsent() }
        // 主頁冇「↻ 刷新」掣：只靠 SwipeRefreshLayout 下拉刷新
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
        findViewById<View>(R.id.btnBackToSearch).setOnClickListener { showSearchHome() }
        btnAdvanced.setOnClickListener { showSequence() }

        bindSearchCategories()
        bindSearchChips()
        // 系統 IME 直接輸入路線號碼；每鍵即時向官方目錄查詢（唔再係自訂鍵盤）。
        routeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                vm.searchRoutes(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

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
        // 搜尋主頁預先載入官方路線目錄：未切去搜尋分頁已經開始背景下載，
        // 撳入去即刻見到「12 往 海麗邨 ›」呢類列，唔係空白頁。
        vm.searchRoutes("")
        // 懸浮液態玻璃：外觀跟隨系統深色／高對比／減少動態效果
        NavGlass.apply(this, bottomTabs)
        // 唔使撳掣：已有權限就自動定位；未有就先彈系統權限對話框，
        // 授權結果返嚟先自動定位。未授權期間唔會靜默用中環假定位。
        autoLocateIfAllowed()
    }

    override fun onResume() {
        super.onResume()
        // 由系統設定改深／高對比／動畫，或由設定頁改「跟隨系統」返嚟：即時重算
        NavGlass.apply(this, bottomTabs)
        val radius = NearbyRadiusPrefs.radiusMeters(this)
        val radiusChanged = radius != lastRadius
        lastRadius = radius
        // 由「設定頁改半徑」或「系統設定 → 開啟定位權限」返嚟：重新自動定位。
        if (LocationHelper.hasPermission(this) && (!nearbyLoaded || radiusChanged)) {
            permissionHint.visibility = View.GONE
            nearbyLoaded = true
            vm.loadNearbyRoutes(radius)
        }
        // 由「應用程式設置」改咗 ETA 自動刷新開關／間隔返嚟：即時重啟輪詢；
        // 若已經過咗一個完整間隔，順手補一次即時 ETA。
        vm.onForeground()
        renderAutoRefreshStatus()
    }

    override fun onPause() {
        // 唔做背景靜默輪詢：離開 App 即刻停，返嚟 onResume 先重啟。
        vm.onBackground()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 系統切換深色模式時，玻璃外觀即時跟住改（唔使等返前景）
        NavGlass.apply(this, bottomTabs)
    }

    /**
     * Home nearby-route card -> APP1933 route-detail screen (NO map).
     * The search tab's own result rows still use the in-tab stop sequence.
     */
    private fun openCard(card: RouteCard) {
        startActivity(Intent(this, RouteDetailActivity::class.java).apply {
            putExtra(RouteDetailActivity.EXTRA_ROUTE, card.route)
            putExtra(RouteDetailActivity.EXTRA_COMPANY, card.company)
            putExtra(RouteDetailActivity.EXTRA_STOP_ID, card.stopId)
            putExtra(RouteDetailActivity.EXTRA_STOP_NAME, card.stopName)
            putExtra(RouteDetailActivity.EXTRA_DEST, card.dest)
        })
    }

    // -------------------------------- 截圖式分類搜尋（SEARCH_UI_2026-11-23）
    // ★ 顏色約束：下面所有 setTextColor / setBackgroundResource 都只係
    //   套用既有 @color/search_* 同既有 drawable，**冇新增、冇修改任何色值**；
    //   values-night 亦刻意唔覆寫 search_*，所以系統切深／淺色都唔會變色。

    private fun bindSearchCategories() {
        for (i in catTabs.indices) {
            catTabs[i].setOnClickListener { selectCategory(i) }
        }
    }

    private fun bindSearchChips() {
        for (i in chips.indices) {
            chips[i].setOnClickListener { selectChip(i) }
        }
    }

    /** 選中分類：綠字＋綠底線；其餘用既有 search_text。chips 只喺巴士顯示。 */
    private fun selectCategory(index: Int) {
        searchCategory = index
        for (i in catTabs.indices) {
            val on = i == index
            catTabs[i].setTextColor(
                ContextCompat.getColor(
                    this,
                    if (on) R.color.search_green else R.color.search_text
                )
            )
            catUnderlines[i].visibility = if (on) View.VISIBLE else View.GONE
        }
        val bus = index == 1
        searchChipScroll.visibility = if (bus) View.VISIBLE else View.GONE
        val note = when (index) {
            0 -> getString(R.string.search_rail_hint)
            2 -> getString(R.string.search_gmb_hint)
            1 -> ""
            else -> getString(R.string.search_no_api)
        }
        tvCategoryNotice.text = note
        tvCategoryNotice.visibility =
            if (bus || note.isEmpty()) View.GONE else View.VISIBLE
        renderSearch()
    }

    /** 選中營運商 chip：淺綠底深綠字／透明底白描邊，全部係既有 drawable。 */
    private fun selectChip(index: Int) {
        busChip = index
        for (i in chips.indices) {
            val on = i == index
            chips[i].setBackgroundResource(
                if (on) R.drawable.bg_chip_selected else R.drawable.bg_chip
            )
            chips[i].setTextColor(
                ContextCompat.getColor(
                    this,
                    if (on) R.color.search_green_text else R.color.search_text
                )
            )
        }
        val note = when (index) {
            3 -> getString(R.string.search_lwb_hint)
            5 -> getString(R.string.search_no_api)
            else -> ""
        }
        tvCategoryNotice.text = note
        tvCategoryNotice.visibility = if (note.isEmpty()) View.GONE else View.VISIBLE
        renderSearch()
    }

    /** 類別／chip 過濾只揀官方目錄已有嘅公司；冇 API 嘅類別如實留空。 */
    private fun matchesCategory(company: String): Boolean = when (searchCategory) {
        0 -> company == EtaRepository.MTR || company == EtaRepository.LRT ||
            company == EtaRepository.MTRB
        1 -> when (busChip) {
            0 -> true
            1 -> company == EtaRepository.CTB || company == JointRoutes.JOINT
            2, 3 -> company == EtaRepository.KMB || company == JointRoutes.JOINT
            4 -> company == EtaRepository.NLB
            else -> false   // 愉景灣／馬灣巴士：官方冇同類實時 ETA API
        }
        2 -> company == EtaRepository.GMB
        else -> false
    }

    /** 點任何一列：記入搜尋紀錄，再帶方向開同一個「路線詳情（無地圖）」。 */
    private fun openHistoryItem(item: SearchHistoryItem) {
        SearchHistoryPrefs.add(this, item.copy(removable = true, preloaded = false))
        refreshHistory()
        startActivity(Intent(this, RouteDetailActivity::class.java).apply {
            putExtra(RouteDetailActivity.EXTRA_ROUTE, item.route)
            putExtra(RouteDetailActivity.EXTRA_COMPANY, item.company)
            putExtra(RouteDetailActivity.EXTRA_DEST, item.dest.removePrefix("往 ").trim())
            putExtra(RouteDetailActivity.EXTRA_BOUND, item.bound)
            putExtra(RouteDetailActivity.EXTRA_STOP_ID, "")
            putExtra(RouteDetailActivity.EXTRA_STOP_NAME, "")
        })
    }

    /** 同一份 state.searchResults，按輸入／類別／chip 渲染截圖式版面。 */
    private fun renderSearch() {
        val state = vm.state.value ?: return
        val rows = state.searchResults
            .filter { matchesCategory(it.company) }
            .map { it.toHistoryItem(removable = false, preloaded = false) }
        if (!routeInput.text.isNullOrBlank()) {
            searchHomeView.visibility = View.GONE
            searchResultsView.visibility = View.VISIBLE
            searchAdapter.submit(rows)
            searchEmpty.text = state.searchMessage.ifEmpty { getString(R.string.search_empty) }
            searchEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        } else {
            searchResultsView.visibility = View.GONE
            searchHomeView.visibility = View.VISIBLE
            refreshBookmarks()
            refreshHistory(rows)
        }
    }

    private fun refreshBookmarks() {
        val favs = vm.favorites()
        bookmarkAdapter.submit(favs)
        tvBookmarkEmpty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 有歷史就顯示歷史；冇就顯示官方目錄預載（標明「目錄預載」、唔俾刪除）。 */
    private fun refreshHistory(preloaded: List<SearchHistoryItem>? = null) {
        val history = SearchHistoryPrefs.list(this)
        if (history.isNotEmpty()) {
            historyAdapter.submit(history)
            tvHistoryEmpty.visibility = View.GONE
            tvPreloadHint.visibility = View.GONE
        } else {
            val rows = preloaded ?: (vm.state.value?.searchResults ?: emptyList())
                .filter { matchesCategory(it.company) }
                .map { it.toHistoryItem(removable = false, preloaded = false) }
            historyAdapter.submit(
                rows.take(6).map { it.copy(removable = false, preloaded = true) }
            )
            tvHistoryEmpty.visibility = View.VISIBLE
            tvPreloadHint.visibility = View.VISIBLE
        }
    }

    /** 截圖式搜尋首頁：頂部直接係分類 tab，唔顯示紅色 header／底部欄。 */
    private fun showSearchHome() {
        inSequence = false
        stopSequenceView.visibility = View.GONE
        searchHomeView.visibility = View.VISIBLE
        searchResultsView.visibility = View.GONE
        hideChrome()
        renderSearch()
    }

    /** 進階：保留原有 8 模式／站序／方向／篩選。 */
    private fun showSequence() {
        inSequence = true
        searchHomeView.visibility = View.GONE
        searchResultsView.visibility = View.GONE
        stopSequenceView.visibility = View.VISIBLE
        restoreChrome()
    }

    private fun hideChrome() {
        mainHeader.visibility = View.GONE
        statusText.visibility = View.GONE
        tvAutoRefreshStatus.visibility = View.GONE
        bottomTabs.visibility = View.GONE
    }

    private fun restoreChrome() {
        mainHeader.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        tvAutoRefreshStatus.visibility = View.VISIBLE
        bottomTabs.visibility = View.VISIBLE
    }

    private fun refreshCurrent() {
        val selection = vm.state.value?.selection
        if (currentTab == 0 && selection == null) {
            // 下拉刷新都唔可以繞過「先授權、後自動定位」
            autoLocateIfAllowed()
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
            // 唔再用實色蓋住玻璃：選中係半透明磨砂 pill，未選係透明
            NavGlass.selectTab(this, tabs[i], i == index)
        }
        if (index == 2) {
            if (inSequence) showSequence() else showSearchHome()
        } else {
            restoreChrome()
        }
    }

    /**
     * AUTO-LOCATE, gated by consent. No button: on launch / refresh this either
     * locates immediately (permission already granted) or asks for permission
     * first and waits for the result. It NEVER calls the nearby API without
     * permission, so LocationHelper's Central fallback can never fake a
     * "nearby" list for a user who has not agreed to be located.
     */
    private fun autoLocateIfAllowed() {
        if (LocationHelper.hasPermission(this)) {
            permissionHint.visibility = View.GONE
            nearbyLoaded = true
            vm.loadNearbyRoutes(selectedRadius())
            return
        }
        // 未授權：清空舊卡片、顯示等待，然後先攞權限
        nearbyLoaded = false
        vm.awaitLocationPermission()
        showPermissionHint()
        if (!locationPromptShown) {
            locationPromptShown = true
            launchPermissionRequest()
        }
    }

    private fun launchPermissionRequest() {
        locationPermissions.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    /** Tapping the hint is CONSENT, not "relocate": re-ask or open Settings. */
    private fun requestLocationConsent() {
        if (LocationHelper.hasPermission(this)) {
            autoLocateIfAllowed()
            return
        }
        val canAskAgain = shouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) || shouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (canAskAgain || !locationPromptShown) {
            locationPromptShown = true
            launchPermissionRequest()
        } else {
            // 「不再詢問」：系統權限對話框唔會再彈，改去 App 設定頁
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }
    }

    private fun showPermissionHint() {
        permissionHint.visibility = View.VISIBLE
        permissionHint.text = if (locationPromptShown &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            getString(R.string.location_permission_denied)
        } else {
            getString(R.string.location_permission_hint)
        }
    }

    private fun selectedCompany(): String =
        EtaViewModel.companyAt(companySpinner.selectedItemPosition)

    /** Radius now comes from 應用設置, not a Home-page Spinner. */
    private fun selectedRadius(): Int = NearbyRadiusPrefs.radiusMeters(this)

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
        tvHomeEmpty.visibility = if (
            cards.isEmpty() && permissionHint.visibility != View.VISIBLE
        ) View.VISIBLE else View.GONE

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

        // 截圖式搜尋：同一份官方目錄結果，按類別／營運商 chip 過濾後渲染
        renderSearch()

        val favs = vm.favorites()
        favoriteAdapter.submit(favs)
        tvFavEmpty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE

        swipe.isRefreshing = state.loading
        renderAutoRefreshStatus()
    }

    /** Shared by render() and onResume() so the line never goes stale. */
    private fun renderAutoRefreshStatus() {
        tvAutoRefreshStatus.text = EtaRefreshPrefs.statusLabel(this)
    }
}
