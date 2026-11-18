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
import android.view.ViewGroup
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
import hk.eta.app.api.RouteSearch
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
    private lateinit var mainHeader: View
    private lateinit var bottomTabs: View
    private lateinit var searchBar: View
    private lateinit var routeInput: TextView
    private lateinit var searchResultsView: View
    private lateinit var stopSequenceView: View
    private lateinit var keypadRoot: View
    private lateinit var searchEmpty: TextView
    private lateinit var permissionHint: TextView
    private lateinit var letterKeys: List<TextView>

    private lateinit var cardAdapter: RouteCardAdapter
    private lateinit var stopAdapter: StopAdapter
    private lateinit var favoriteAdapter: FavoriteAdapter
    private lateinit var searchAdapter: RouteResultAdapter

    private var currentTab = 0
    // true = 站序模式（保留原有控制項）；false = APP1933 全螢幕路線結果 + 鍵盤
    private var inSequence = false
    private val inputBuffer = StringBuilder()
    private var letterPage = 0
    private val letterPages = listOf(
        listOf("A", "B", "C", "E"),
        listOf("K", "M", "P", "S"),
        listOf("X", "R", "T", "N")
    )
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
        searchBar = findViewById(R.id.searchBar)
        routeInput = findViewById(R.id.routeInput)
        searchResultsView = findViewById(R.id.searchResultsView)
        stopSequenceView = findViewById(R.id.stopSequenceView)
        keypadRoot = findViewById(R.id.keypadRoot)
        searchEmpty = findViewById(R.id.searchEmpty)
        permissionHint = findViewById(R.id.permissionHint)
        letterKeys = listOf(
            findViewById<TextView>(R.id.keyLetter0),
            findViewById<TextView>(R.id.keyLetter1),
            findViewById<TextView>(R.id.keyLetter2),
            findViewById<TextView>(R.id.keyLetter3)
        )

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
        searchAdapter = RouteResultAdapter { result -> openResult(result) }

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
        findViewById<View>(R.id.btnBackToSearch).setOnClickListener { showResults() }
        findViewById<View>(R.id.searchHome).setOnClickListener { selectTab(0) }

        bindKeypad(keypadRoot)
        updateLetterKeys()
        updateRouteInput()

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
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 系統切換深色模式時，玻璃外觀即時跟住改（唔使等返前景）
        NavGlass.apply(this, bottomTabs)
    }

    private fun openCard(card: RouteCard) {
        inputBuffer.clear()
        inputBuffer.append(card.route)
        updateRouteInput()
        routeEdit.setText(card.route)
        companySpinner.setSelection(companyPosition(card.company))
        vm.loadStops(card.route, card.company)
        selectTab(2)
        showSequence()
    }

    /** Tapping "1 往 尖沙咀碼頭" opens only that direction's stop sequence. */
    private fun openResult(result: RouteSearch.Result) {
        routeEdit.setText(result.route)
        companySpinner.setSelection(companyPosition(result.company))
        vm.loadStops(result.route, result.company, result.bound)
        showSequence()
    }

    // ------------------------------------------------------- APP1933 搜尋鍵盤

    /** One listener for every tagged key; no system soft keyboard is used. */
    private fun bindKeypad(root: View) {
        val tag = root.tag
        if (tag is String && tag.isNotEmpty()) {
            root.setOnClickListener { onKey(tag) }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                bindKeypad(root.getChildAt(i))
            }
        }
    }

    private fun onKey(tag: String) {
        when (tag) {
            "DEL" -> if (inputBuffer.isNotEmpty()) {
                inputBuffer.deleteCharAt(inputBuffer.length - 1)
            }
            "ENTER" -> Unit   // 結果已逐鍵更新；↵ 只作確認，由尾部統一再查一次
            "PREV" -> {
                letterPage = (letterPage - 1 + letterPages.size) % letterPages.size
                updateLetterKeys()
            }
            "NEXT" -> {
                letterPage = (letterPage + 1) % letterPages.size
                updateLetterKeys()
            }
            "L0", "L1", "L2", "L3" -> {
                val index = tag.substring(1).toInt()
                inputBuffer.append(letterPages[letterPage][index])
            }
            else -> if (tag.length == 1 && tag[0].isDigit()) {
                inputBuffer.append(tag)
            }
        }
        updateRouteInput()
        vm.searchRoutes(inputBuffer.toString())
    }

    private fun updateLetterKeys() {
        val page = letterPages[letterPage]
        for (i in letterKeys.indices) {
            letterKeys[i].text = page[i]
        }
    }

    private fun updateRouteInput() {
        val empty = inputBuffer.isEmpty()
        routeInput.text = if (empty) {
            getString(R.string.search_input_hint)
        } else {
            inputBuffer.toString()
        }
        routeInput.setTextColor(
            ContextCompat.getColor(
                this,
                if (empty) R.color.search_result_hint else R.color.text
            )
        )
    }

    /** Full-screen result mode: red 搜尋 bar, input, result rows, keypad. */
    private fun showResults() {
        inSequence = false
        searchResultsView.visibility = View.VISIBLE
        stopSequenceView.visibility = View.GONE
        keypadRoot.visibility = View.VISIBLE
        mainHeader.visibility = View.GONE
        statusText.visibility = View.GONE
        bottomTabs.visibility = View.GONE
        updateRouteInput()
        vm.searchRoutes(inputBuffer.toString())
    }

    /** Stop-sequence mode keeps every original control and the bottom tabs. */
    private fun showSequence() {
        inSequence = true
        searchResultsView.visibility = View.GONE
        stopSequenceView.visibility = View.VISIBLE
        keypadRoot.visibility = View.GONE
        restoreChrome()
    }

    private fun restoreChrome() {
        mainHeader.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
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
            if (inSequence) showSequence() else showResults()
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

        searchAdapter.submit(state.searchResults)
        searchEmpty.text = state.searchMessage.ifEmpty { getString(R.string.search_empty) }
        searchEmpty.visibility = if (state.searchResults.isEmpty()) View.VISIBLE else View.GONE

        val favs = vm.favorites()
        favoriteAdapter.submit(favs)
        tvFavEmpty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE

        swipe.isRefreshing = state.loading
    }
}
