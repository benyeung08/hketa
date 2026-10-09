package com.hketa.app.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hketa.app.data.StopHit
import com.hketa.app.data.suspendCatching
import com.hketa.app.data.EtaEntry
import com.hketa.app.data.EtaText
import com.hketa.app.data.FavoriteStop
import com.hketa.app.data.FavoritesStore
import com.hketa.app.data.HomeItem
import com.hketa.app.data.EtaRepository
import com.hketa.app.data.IndexData
import com.hketa.app.data.IndexStat
import com.hketa.app.data.IndexStore
import com.hketa.app.data.NearbyStop
import com.hketa.app.data.Operator
import com.hketa.app.data.RailData
import com.hketa.app.data.RailLine
import com.hketa.app.data.RouteDef
import com.hketa.app.data.RouteStopDef
import com.hketa.app.data.StopDef
import com.hketa.app.data.GitHubRelease
import com.hketa.app.data.LocatePhase
import com.hketa.app.data.PresetLocation
import com.hketa.app.data.SpecialRouteInfo
import com.hketa.app.data.SpecialRoutesStore
import com.hketa.app.util.AppLocale
import com.hketa.app.data.StopWithSeq
import com.hketa.app.data.UpdateChecker
import com.hketa.app.data.UpdateState
import com.hketa.app.R
import com.hketa.app.location.LocationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AppViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** 到站預報自動刷新間隔（秒）—— 同底部狀態欄嘅倒數一致 */
        const val ETA_AUTO_REFRESH_SEC = 20

        /** 主頁自動重新定位／重整間隔（秒） */
        const val HOME_AUTO_LOCATE_SEC = 60

        /** 索引超過呢個時間就當過期、開 App 自動重建（14 日） */
        val INDEX_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000

        /**
         * 深度修復嘅並發數。
         * 城巴／小巴要逐條路線抓，串行嘅話幾百條路線要幾十分鐘；
         * 改成分批並發之後時間大幅縮短，亦因為係增量（跳過已抓過嘅），
         * 實際發出嘅請求數會少好多 —— 唔再一定需要 Wi-Fi。
         */
        const val DEEP_INDEX_CONCURRENCY = 8

        private const val INDEX_VERSION = 2
        private const val UPDATE_THROTTLE_MS = 30 * 60 * 1000L
        private const val HOME_RADIUS_M = 800.0
        private const val HOME_MAX_STOPS = 12
    }

    /** 取本地化字串（語言切換後 Application 資源會跟住變） */
    private fun str(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    /** 資料層拼備註（月台／延誤／經馬場／卡數）要用嘅本地化模板 */
    /** 而家係咪英文介面 */
    private val enUi: Boolean get() = AppLocale.isEnglish()

    private fun etaText(): EtaText = EtaText(
        platform = str(R.string.eta_platform),
        delayed = str(R.string.eta_delayed),
        viaRacecourse = str(R.string.eta_via_racecourse),
        arriving = str(R.string.eta_arriving),
        departing = str(R.string.eta_departing),
        special = str(R.string.eta_special),
        cars = str(R.string.eta_cars),
        sep = str(R.string.eta_sep)
    )

    private val repo = EtaRepository()
    private val store = IndexStore(app)

    private val _index = MutableStateFlow(IndexData())
    val index: StateFlow<IndexData> = _index.asStateFlow()

    private val _indexStatus = MutableStateFlow("")
    val indexStatus: StateFlow<String> = _indexStatus.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<RouteDef>>(emptyList())
    val results: StateFlow<List<RouteDef>> = _results.asStateFlow()

    // ---- 索引診斷：記錄各營辦商喺建立索引時遇到嘅狀況 ----
    private val _indexErrors = MutableStateFlow<List<Pair<Operator, String>>>(emptyList())
    val indexErrors: StateFlow<List<Pair<Operator, String>>> = _indexErrors.asStateFlow()

    // ---- 自動定位 ----
    private val _autoLocate = MutableStateFlow(true)
    val autoLocate: StateFlow<Boolean> = _autoLocate.asStateFlow()

    /** 自動定位目前進行到邊一步（畀 UI 顯示「定位中／搵到 N 個站」） */
    private val _locatePhase = MutableStateFlow(LocatePhase.IDLE)
    val locatePhase: StateFlow<LocatePhase> = _locatePhase.asStateFlow()

    /** 上次成功定位嘅時間（epoch millis） */
    private val _lastLocateAt = MutableStateFlow(0L)
    val lastLocateAt: StateFlow<Long> = _lastLocateAt.asStateFlow()

    /**
     * 手動選點：用戶唔想授權定位／定位失敗嗰陣，自己揀一個地點。
     * 設定咗之後，主頁會用呢個座標去搵附近車站，
     * 唔會淨係顯示「未授權定位」就冇嘢睇。
     */
    private val _manualLocation = MutableStateFlow<PresetLocation?>(null)
    val manualLocation: StateFlow<PresetLocation?> = _manualLocation.asStateFlow()

    /** 設定手動選點；傳 null 即清除（改返用真實定位） */
    fun setManualLocation(loc: PresetLocation?) {
        _manualLocation.value = loc
    }

    private var locateJob: Job? = null

    fun setAutoLocate(on: Boolean) {
        _autoLocate.value = on
        if (!on) {
            locateJob?.cancel()
            locateJob = null
            _locatePhase.value = LocatePhase.IDLE
        }
    }

    /**
     * 啟動自動定位：每隔 HOME_AUTO_LOCATE_SEC 秒重新定位一次並重整主頁。
     * 淨係喺「自動定位」開咗、且已經授權嘅情況先會真正發出定位請求。
     */
    fun startAutoLocate(context: android.content.Context, granted: () -> Boolean) {
        locateJob?.cancel()
        locateJob = viewModelScope.launch {
            while (_autoLocate.value) {
                // 已授權、或者已經揀咗手動選點，都可以繼續搵附近車站
                if (granted() || _manualLocation.value != null) {
                    _locatePhase.value = LocatePhase.LOCATING
                    val ok = suspendCatching { loadHomeInternal(context) }.getOrDefault(false)
                    _locatePhase.value = if (ok) LocatePhase.OK else LocatePhase.FAILED
                    if (ok) _lastLocateAt.value = System.currentTimeMillis()
                } else {
                    _locatePhase.value = LocatePhase.NO_PERMISSION
                }
                delay(HOME_AUTO_LOCATE_SEC * 1000L)
            }
        }
    }

    /** 原有嘅 loadHome 保留（手動／首次用），內部邏輯抽咗出嚟共用 */
    private suspend fun loadHomeInternal(context: android.content.Context): Boolean {
        ensureFavorites()
        _favorites.value = favoritesStore.all()
        // 真實定位優先；冇（未授權／失敗）就退返手動選點
        val fix = LocationProvider.current(context)
            ?: _manualLocation.value?.let { LocationProvider.Fix(it.lat, it.lon, "Manual") }
        if (fix == null) {
            _homeItems.value = emptyList()
            return false
        }
        return suspendCatching { buildHomeItems(fix) }.getOrDefault(false)
    }

    // ---- 版本歷史 ----
    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    private val _historyReleases =
        MutableStateFlow<List<com.hketa.app.data.GitHubRelease>>(emptyList())
    val historyReleases: StateFlow<List<com.hketa.app.data.GitHubRelease>> =
        _historyReleases.asStateFlow()

    /** 目前安裝版本名（畀版本歷史對話框用） */
    private val _currentVersion = MutableStateFlow("")
    val currentVersion: StateFlow<String> = _currentVersion.asStateFlow()

    /** 開啟版本歷史：即刻顯示本地 changelog，同時背景去 GitHub 拉 Release */
    fun loadVersionHistory() {
        _currentVersion.value = UpdateChecker.currentVersion(getApplication())
        if (_historyReleases.value.isNotEmpty()) return   // 已經拉過就唔重複
        viewModelScope.launch {
            _historyLoading.value = true
            val list = suspendCatching { UpdateChecker.fetchHistory() }.getOrDefault(emptyList())
            _historyReleases.value = list
            _historyLoading.value = false
        }
    }

    // ---- 底部狀態欄 ----
    /** 上次成功更新資料嘅時間（epoch millis），0 = 未更新過 */
    private val _lastUpdateAt = MutableStateFlow(0L)
    val lastUpdateAt: StateFlow<Long> = _lastUpdateAt.asStateFlow()

    /** 自動刷新倒數（秒）；0 = 已關閉自動刷新 */
    private val _refreshCountdown = MutableStateFlow(0)
    val refreshCountdown: StateFlow<Int> = _refreshCountdown.asStateFlow()

    /** 自動刷新開關（預設開，對應到站預報每 20 秒刷新） */
    private val _autoRefresh = MutableStateFlow(true)
    val autoRefresh: StateFlow<Boolean> = _autoRefresh.asStateFlow()

    private var countdownJob: Job? = null

    fun toggleAutoRefresh() {
        _autoRefresh.value = !_autoRefresh.value
        if (!_autoRefresh.value) {
            countdownJob?.cancel()
            countdownJob = null
            _refreshCountdown.value = 0
        } else {
            restartCountdown()
        }
    }

    /** 重新整理：依目前所在頁面重拉資料；順便重置自動刷新倒數 */
    fun refreshNow() {
        viewModelScope.launch {
            _busy.value = true
            suspendCatching {
                // 重新拉目前車站嘅到站預報；冇揀車站就刷新主頁／收藏
                val st = _selectedStop.value
                if (st != null) {
                    loadEta(st, _selectedRoute.value)
                } else {
                    loadFavoriteEtas()
                }
            }
            _lastUpdateAt.value = System.currentTimeMillis()
            _busy.value = false
            restartCountdown()
        }
    }

    private fun restartCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var left = ETA_AUTO_REFRESH_SEC
            while (_autoRefresh.value && left > 0) {
                _refreshCountdown.value = left
                delay(1000)
                left--
            }
            _refreshCountdown.value = 0
            if (_autoRefresh.value) {
                val st = _selectedStop.value
                if (st != null) loadEta(st, _selectedRoute.value) else loadFavoriteEtas()
                _lastUpdateAt.value = System.currentTimeMillis()
                restartCountdown()
            }
        }
    }

    init {
        restartCountdown()
    }

    // ---- 獨立嘅「搜尋車站」----
    private val _stopQuery = MutableStateFlow("")
    val stopQuery: StateFlow<String> = _stopQuery.asStateFlow()

    private val _stopResults = MutableStateFlow<List<StopHit>>(emptyList())
    val stopResults: StateFlow<List<StopHit>> = _stopResults.asStateFlow()


    private val _selectedRoute = MutableStateFlow<RouteDef?>(null)
    val selectedRoute: StateFlow<RouteDef?> = _selectedRoute.asStateFlow()

    private val _routeStops = MutableStateFlow<List<StopWithSeq>>(emptyList())
    val routeStops: StateFlow<List<StopWithSeq>> = _routeStops.asStateFlow()

    private val _selectedStop = MutableStateFlow<StopDef?>(null)
    val selectedStop: StateFlow<StopDef?> = _selectedStop.asStateFlow()

    private val _etas = MutableStateFlow<List<EtaEntry>>(emptyList())
    val etas: StateFlow<List<EtaEntry>> = _etas.asStateFlow()

    private val _nearby = MutableStateFlow<List<NearbyStop>>(emptyList())
    val nearby: StateFlow<List<NearbyStop>> = _nearby.asStateFlow()

    private val _nearbyOrigin = MutableStateFlow("")
    val nearbyOrigin: StateFlow<String> = _nearbyOrigin.asStateFlow()

    // ============ 主頁（App1933 樣式：附近車站 + 即時預報） ============

    private val _homeItems = MutableStateFlow<List<HomeItem>>(emptyList())
    val homeItems: StateFlow<List<HomeItem>> = _homeItems.asStateFlow()

    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()

    private val _favorites = MutableStateFlow<List<FavoriteStop>>(emptyList())
    val favorites: StateFlow<List<FavoriteStop>> = _favorites.asStateFlow()

    /** 收藏頁用：key（FavoriteStop.key）→ 該站嘅到站預報 */
    private val _favoriteEtas = MutableStateFlow<Map<String, List<EtaEntry>>>(emptyMap())
    val favoriteEtas: StateFlow<Map<String, List<EtaEntry>>> = _favoriteEtas.asStateFlow()

    private val _favoritesLoading = MutableStateFlow(false)
    val favoritesLoading: StateFlow<Boolean> = _favoritesLoading.asStateFlow()

    private lateinit var timetableStore: com.hketa.app.data.TimetableStore
    private lateinit var favoritesStore: FavoritesStore

    // ============ 版本更新 ============

    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _updateInfo = MutableStateFlow<GitHubRelease?>(null)
    val updateInfo: StateFlow<GitHubRelease?> = _updateInfo.asStateFlow()

    private val _updateError = MutableStateFlow("")
    val updateError: StateFlow<String> = _updateError.asStateFlow()

    /** 節流用：30 分鐘內唔重複打 GitHub API（手動按掣可以 force 無視） */
    private var lastUpdateCheckAt = 0L

    /** 索引建立嘅 Job —— bootstrap() 要等佢跑完先做體檢，費事同建索引撞車 */
    private var indexJob: Job? = null

    init {
        // runCatching：索引出問題（例如快取損壞）唔好令成個 App 崩，
        // 淨係當「冇索引」，之後 autoRepairIfNeeded() 會自動重建
        indexJob = viewModelScope.launch { runCatching { ensureIndex() } }
        runCatching { refreshFavorites() }
    }

    // ============ 開 App 全自動流程 ============

    private val _bootstrapped = MutableStateFlow(false)
    val bootstrapped: StateFlow<Boolean> = _bootstrapped.asStateFlow()

    /**
     * 開 App 之後嘅全自動流程（**淨係行一次**）：
     *
     *   ① 索引（ETA 資料）：冇就建、版本落後就重建
     *   ② ETA 資料修復：自動體檢，唔健康就重建；健康就補城巴／小巴車站
     *   ③ 定位搵附近路線：自動請求定位權限，然後啟動自動定位循環
     *
     * 以前呢三樣都要用戶自己撳（設定頁「ETA 資料修復」、主頁「用定位搵附近路線」），
     * 而家開 App 就全部自己做，用戶一入到主頁已經有嘢睇。
     */
    fun bootstrap(
        context: android.content.Context,
        requestLocation: ((Boolean) -> Unit) -> Unit
    ) {
        if (_bootstrapped.value) return
        _bootstrapped.value = true

        viewModelScope.launch {
            // 等 init 嗰個 ensureIndex 跑完，唔同佢爭
            runCatching { indexJob?.join() }
            // ① + ②：索引 + 自動體檢／修復
            runCatching { autoRepairIfNeeded() }
        }

        // ③ 定位：自動請求權限，之後交畀循環（冇授權都照樣跑，會退返手動選點）
        //
        // runCatching 係必要嘅：requestLocation 會觸發系統權限對話框，
        // 喺少數裝置／極早期嘅 composition 階段 call ActivityResultLauncher
        // 會丟 IllegalStateException。以前係直接崩，而家 fail 咗都照樣
        // 啟動循環（退返手動選點），唔會影響用戶揾車。
        runCatching {
            requestLocation { granted ->
                _locatePhase.value =
                    if (granted) LocatePhase.LOCATING else LocatePhase.NO_PERMISSION
                startAutoLocate(context) { LocationProvider.hasPermission(context) }
            }
        }.onFailure {
            // 權限對話框開唔到 → 照樣啟動循環，用上次位置／手動選點兜底
            _locatePhase.value = LocatePhase.NO_PERMISSION
            startAutoLocate(context) { LocationProvider.hasPermission(context) }
        }
    }

    /**
     * 索引體檢 —— 「ETA 資料修復」嘅全自動版。
     *
     * 判定唔健康（要重建）嘅情況：
     *   - 冇任何路線／車站／路線-車站對應
     *   - 索引太舊（超過 [INDEX_MAX_AGE_MS]）
     *
     * 健康嘅話就交畀 [maybeAutoRepair] 補城巴／小巴車站。
     */
    private suspend fun autoRepairIfNeeded() {
        val idx = _index.value
        val unhealthy =
            idx.routes.isEmpty() ||
                idx.stops.isEmpty() ||
                idx.routeStops.isEmpty() ||
                (idx.builtAt > 0 && System.currentTimeMillis() - idx.builtAt > INDEX_MAX_AGE_MS)

        if (unhealthy) {
            _indexStatus.value = str(R.string.index_auto_repair)
            // buildIndex() 尾部已經會叫 maybeAutoRepair()，唔使再叫多次
            buildIndex()
            return
        }
        maybeAutoRepair()
    }

    // ============ 版本更新 ============

    /**
     * 檢查更新。進入「設定」頁會自動檢查一次（30 分鐘節流），
     * 撳「檢查更新」掣則 force = true 立即查。
     */
    fun checkUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (_updateState.value == UpdateState.CHECKING) return
        if (!force && now - lastUpdateCheckAt < UPDATE_THROTTLE_MS) return

        viewModelScope.launch {
            lastUpdateCheckAt = now
            _updateState.value = UpdateState.CHECKING
            _updateError.value = ""

            val current = UpdateChecker.currentVersion(getApplication())
            suspendCatching {
                val release = UpdateChecker.fetchLatest()
                when {
                    release == null -> {
                        _updateInfo.value = null
                        _updateState.value = UpdateState.NO_RELEASE
                    }
                    UpdateChecker.isNewer(release.tag_name, current) -> {
                        _updateInfo.value = release
                        _updateState.value = UpdateState.AVAILABLE
                    }
                    else -> {
                        _updateInfo.value = release
                        _updateState.value = UpdateState.UP_TO_DATE
                    }
                }
            }.onFailure {
                _updateError.value = it.message.orEmpty()
                _updateState.value = UpdateState.ERROR
            }
        }
    }

    // ============ 索引 ============

    /** 鐵路站／線由 App 內置官方資料（assets/rail.json）提供，唔使網絡 */
    private fun railRoutes(): List<RouteDef> {
        val ctx = getApplication<Application>()
        return RailData.heavyRail(ctx).map { l ->
            RouteDef(Operator.MTR_HR, route = l.name, routeId = l.id, orig = l.orig, dest = l.dest)
        } + RailData.lightRail(ctx).map { l ->
            RouteDef(Operator.LRT, route = l.name, routeId = l.id, orig = l.orig, dest = l.dest)
        }
    }

    private fun railStops(): List<StopDef> {
        val ctx = getApplication<Application>()
        val out = mutableListOf<StopDef>()
        RailData.heavyRail(ctx).forEach { l ->
            l.stops.forEach { s -> out.add(StopDef(op = Operator.MTR_HR, id = s.id, name = s.name, lat = s.lat, lon = s.lon)) }
        }
        RailData.lightRail(ctx).forEach { l ->
            l.stops.forEach { s -> out.add(StopDef(op = Operator.LRT, id = s.id, name = s.name, lat = s.lat, lon = s.lon)) }
        }
        return out.distinctBy { "${it.op.name}|${it.id}" }
    }

    private fun railRouteStops(): List<RouteStopDef> {
        val ctx = getApplication<Application>()
        val out = mutableListOf<RouteStopDef>()
        RailData.heavyRail(ctx).forEach { l ->
            l.stops.forEachIndexed { i, s ->
                out.add(
                    RouteStopDef(
                        op = Operator.MTR_HR, route = l.name, routeId = l.id,
                        seq = i + 1, stopId = s.id
                    )
                )
            }
        }
        RailData.lightRail(ctx).forEach { l ->
            l.stops.forEachIndexed { i, s ->
                out.add(
                    RouteStopDef(
                        op = Operator.LRT, route = l.name, routeId = l.id,
                        seq = i + 1, stopId = s.id
                    )
                )
            }
        }
        return out
    }

    suspend fun ensureIndex(force: Boolean = false) {
        // 版本落後（例如 1.0.0 建立、未含鐵路站）就自動重建
        val cached = if (force) null else store.loadSuspend()
        if (cached != null && cached.routes.isNotEmpty() && cached.version >= INDEX_VERSION) {
            _index.value = cached
            _indexStatus.value = str(R.string.index_ready, cached.routes.size)
            // 快取命中都要補城巴／小巴車站 —— 以前淨係得「重建索引」嗰條路會叫
            // maybeAutoRepair()，所以第二次開 App 起就永遠唔會補，
            // 主頁嗰句「背景補充緊…」亦會一直掛住唔消失。
            maybeAutoRepair()
            return
        }
        buildIndex()
    }

    /**
     * 淨係清除快取，唔即刻重建 —— 交由下次啟動嘅 ensureIndex() 自動重新下載。
     * 畀「ETA 資料修復」入面「淨係清除快取」掣用。
     */
    fun clearEtaCache() {
        viewModelScope.launch {
            _busy.value = true
            store.clear()
            _index.value = IndexData()
            _indexStatus.value = ""
            _favorites.value = favoritesStore.all().takeIf { ::favoritesStore.isInitialized }.orEmpty()
            _busy.value = false
            _message.value = str(R.string.eta_repair_cleared)
        }
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            store.clear()
            _index.value = IndexData()
            buildIndex()
        }
    }

    // ============ 逐個營辦商修復 ============

    /**
     * 港鐵巴士：官方冇全量路線接口（得 getSchedule 按路線名查），
     * 所以用內置已知路線清單 + 索引入面已經有嘅路線去逐條補車站。
     * 查唔到嘅會自動跳過，唔會當錯誤。
     */
    private val mtrBusKnown = listOf(
        "K12", "K14", "K17", "K18",
        "K51", "K51A", "K52", "K53", "K58",
        "K65", "K66", "K68", "K73", "K74", "K75", "K76"
    )

    /**
     * 修復單一營辦商嘅資料 —— 清走佢嘅舊資料，重新抓一次再合併返入索引。
     *
     * 以前「ETA 資料修復」得「全部重建」同「修復城巴／修復小巴」兩個掣，
     * 九巴／嶼巴／港鐵巴士／港鐵／輕鐵出問題就冇得單獨搞，
     * 要成個索引重建（幾分鐘、又要重新下載全部資料）。
     */
    fun repairOperator(op: Operator) {
        viewModelScope.launch { repairOperatorInternal(op) }
    }

    private suspend fun repairOperatorInternal(op: Operator) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        _indexStatus.value = str(R.string.eta_repair_op_progress, op.label)

        runCatching {
            when (op) {
                Operator.KMB -> {
                    // 九巴：三個接口（路線 / 路線-車站 / 車站）一次過攞晒
                    val routes = repo.kmbRoutes()
                    _indexStatus.value = str(R.string.eta_repair_op_progress, op.label)
                    val rs = repo.kmbRouteStops()
                    val stops = repo.kmbStops()
                    replaceOperator(op, routes, stops, rs)
                }

                Operator.CTB -> {
                    // 城巴：官方冇全量車站接口 → 先補路線，再逐條路線抓車站
                    val routes = suspendCatching { repo.ctbRoutes() }.getOrDefault(emptyList())
                    replaceOperator(op, routes, emptyList(), emptyList())
                    if (routes.isNotEmpty()) deepIndexCtbInternal(incremental = false, silent = true)
                }

                Operator.NLB -> {
                    val routes = repo.nlbRoutes()
                    val stops = mutableListOf<StopDef>()
                    val rs = mutableListOf<RouteStopDef>()
                    // 分批並發，每批自己返一組結果（唔並發寫同一個 list）
                    for (batch in routes.chunked(DEEP_INDEX_CONCURRENCY)) {
                        coroutineScope {
                            batch.map { r ->
                                async {
                                    val ss = suspendCatching { repo.nlbStops(r.routeId) }
                                        .getOrDefault(emptyList())
                                    val rss = ss.mapIndexed { i, st ->
                                        RouteStopDef(
                                            op = Operator.NLB, route = r.route,
                                            routeId = r.routeId, seq = i + 1, stopId = st.id
                                        )
                                    }
                                    ss to rss
                                }
                            }.awaitAll().forEach { (ss, rss) ->
                                stops.addAll(ss); rs.addAll(rss)
                            }
                        }
                    }
                    replaceOperator(op, routes, stops, rs)
                }

                Operator.GMB -> {
                    // 專線小巴：三個 region + 官方全量路徑（冇 region）
                    val routes = mutableListOf<RouteDef>()
                    var got = 0
                    for (region in listOf("HKI", "KLN", "NT")) {
                        val list = suspendCatching { repo.gmbRoutes(region) }.getOrDefault(emptyList())
                        routes.addAll(list); got += list.size
                    }
                    if (got == 0) {
                        val all = suspendCatching { repo.gmbRoutes("") }.getOrDefault(emptyList())
                        routes.addAll(all); got += all.size
                    }
                    replaceOperator(op, routes, emptyList(), emptyList())
                    if (got > 0) deepIndexGmbInternal(incremental = false, silent = true)
                }

                Operator.MTR_BUS -> {
                    // 港鐵巴士：已知清單 + 索引入面已有嘅路線
                    val known = (mtrBusKnown + _index.value.routes
                        .filter { it.op == Operator.MTR_BUS }.map { it.route })
                        .distinct()
                    val routes = mutableListOf<RouteDef>()
                    val stops = mutableListOf<StopDef>()
                    val rs = mutableListOf<RouteStopDef>()
                    for (batch in known.chunked(DEEP_INDEX_CONCURRENCY)) {
                        coroutineScope {
                            batch.map { name ->
                                async {
                                    val ss = suspendCatching { repo.mtrBusStops(name) }
                                        .getOrDefault(emptyList())
                                    val rss = ss.mapIndexed { i, st ->
                                        RouteStopDef(
                                            op = Operator.MTR_BUS, route = name,
                                            seq = i + 1, stopId = st.id
                                        )
                                    }
                                    Triple(name, ss, rss)
                                }
                            }.awaitAll().forEach { (name, ss, rss) ->
                                if (ss.isNotEmpty()) {
                                    routes.add(RouteDef(op = Operator.MTR_BUS, route = name))
                                    stops.addAll(ss); rs.addAll(rss)
                                }
                            }
                        }
                    }
                    replaceOperator(op, routes, stops, rs)
                }

                Operator.MTR_HR, Operator.LRT -> {
                    // 港鐵／輕鐵：離線內置（rail.json），修復 = 由 assets 重新載入
                    val ctx = getApplication<Application>()
                    val lines = if (op == Operator.MTR_HR) RailData.heavyRail(ctx) else RailData.lightRail(ctx)
                    replaceOperator(
                        op,
                        lines.map { RouteDef(op, route = it.name, routeId = it.id, orig = it.orig, dest = it.dest) },
                        lines.flatMap { l -> l.stops.map { s -> StopDef(op = op, id = s.id, name = s.name, lat = s.lat, lon = s.lon) } },
                        lines.flatMap { l ->
                            l.stops.mapIndexed { i, st ->
                                RouteStopDef(op = op, route = l.name, routeId = l.id, seq = i + 1, stopId = st.id)
                            }
                        }
                    )
                }
            }
        }.onFailure {
            _message.value = str(R.string.eta_repair_op_failed, op.label, it.message.orEmpty())
        }

        val now = _index.value
        val rc = now.routes.count { it.op == op }
        val sc = now.stops.count { it.op == op }
        _indexStatus.value = str(R.string.eta_repair_op_done, op.label, rc, sc)
        if (_message.value == null) {
            _message.value = str(R.string.eta_repair_op_done, op.label, rc, sc)
        }
        _busy.value = false
    }

    /** 用新資料成個替換某個營辦商嘅 routes / stops / routeStops */
    private suspend fun replaceOperator(
        op: Operator,
        newRoutes: List<RouteDef>,
        newStops: List<StopDef>,
        newRs: List<RouteStopDef>
    ) {
        val cur = _index.value
        val merged = cur.copy(
            routes = (cur.routes.filter { it.op != op } + newRoutes).distinctBy { it.key },
            stops = (cur.stops.filter { it.op != op } + newStops)
                .distinctBy { "${it.op.name}|${it.id}" },
            routeStops = (cur.routeStops.filter { it.op != op } + newRs)
                .distinctBy { "${it.op.name}|${it.route}|${it.bound}|${it.routeId}|${it.stopId}" },
            builtAt = System.currentTimeMillis()
        )
        _index.value = merged
        store.saveSuspend(merged)
    }

    private suspend fun buildIndex() {
        _indexErrors.value = emptyList()
        _busy.value = true
        _message.value = null

        val routes = mutableListOf<RouteDef>().apply { addAll(railRoutes()) }
        val stops = mutableListOf<StopDef>().apply { addAll(railStops()) }
        val routeStops = mutableListOf<RouteStopDef>().apply { addAll(railRouteStops()) }

        suspend fun step(label: String, block: suspend () -> Unit) {
            _indexStatus.value = label
            // block 本身係 suspend lambda，標準 runCatching 嘅非 suspend lambda 包唔住
            suspendCatching { block() }
                .onFailure { _message.value = str(R.string.index_step_failed, label, it.message.orEmpty()) }
        }

        step(str(R.string.index_step_kmb)) {
            routes.addAll(repo.kmbRoutes())
            _indexStatus.value = str(R.string.index_step_kmb_rs)
            routeStops.addAll(repo.kmbRouteStops())
            _indexStatus.value = str(R.string.index_step_kmb_stops)
            stops.addAll(repo.kmbStops())
        }

        step(str(R.string.index_step_ctb)) {
            val ctb = suspendCatching { repo.ctbRoutes() }.getOrDefault(emptyList())
            routes.addAll(ctb)
            // 城巴官方冇全量車站接口，索引階段一定係 0 個站。
            // 喺統計度標明「需要修復」，等用戶知道要去撳「修復城巴」，
            // 而唔係以為資料出錯。
            if (ctb.isNotEmpty()) {
                _indexErrors.value = _indexErrors.value +
                    (Operator.CTB to str(R.string.index_err_ctb_stops))
            }
        }

        step(str(R.string.index_step_nlb)) {
            val nlbRoutes = repo.nlbRoutes()
            routes.addAll(nlbRoutes)
            coroutineScope {
                nlbRoutes.map { r ->
                    async {
                        suspendCatching { repo.nlbStops(r.routeId) }.getOrDefault(emptyList())
                            .onEachIndexed { i, s ->
                                routeStops.add(
                                    RouteStopDef(
                                        op = Operator.NLB,
                                        route = r.route,
                                        routeId = r.routeId,
                                        seq = i + 1,
                                        stopId = s.id
                                    )
                                )
                            }
                    }
                }.awaitAll().forEach { stops.addAll(it) }
            }
        }

        step(str(R.string.index_step_gmb)) {
            // 用 for 而唔係 forEach —— forEach 嘅 lambda 唔係 suspend 上下文，
            // 入面叫 suspendCatching 會編譯失敗
            var got = 0
            for (region in listOf("HKI", "KLN", "NT")) {
                val list = suspendCatching { repo.gmbRoutes(region) }.getOrDefault(emptyList())
                routes.addAll(list)
                got += list.size
            }
            // 三個 region 都冇結果 → 試官方嘅全量路徑 /route（唔帶 region）
            if (got == 0) {
                val all = suspendCatching { repo.gmbRoutes("") }.getOrDefault(emptyList())
                routes.addAll(all)
                got += all.size
            }
            if (got == 0) {
                _indexErrors.value = _indexErrors.value +
                    (Operator.GMB to str(R.string.index_err_gmb))
            }
        }

        val data = IndexData(
            version = INDEX_VERSION,
            builtAt = System.currentTimeMillis(),
            routes = routes.distinctBy { it.key },
            stops = stops.distinctBy { "${it.op.name}|${it.id}" },
            routeStops = routeStops.distinctBy { "${it.op.name}|${it.route}|${it.bound}|${it.routeId}|${it.stopId}" }
        )
        _index.value = data
        store.saveSuspend(data)
        _indexStatus.value = str(R.string.index_done, data.routes.size, data.stops.size)
        _busy.value = false

        // 索引建好就順手補城巴／小巴車站（增量 + 並發，背景做，唔彈訊息）。
        // 以前要用戶自己撳「修復城巴／修復小巴」，唔撳附近車站就永遠淨係得九巴。
        maybeAutoRepair()
    }

    /** 城巴沒有全量車站接口，逐條路線補齊車站與座標 */
    /**
     * 城巴深度修復：逐條路線抓車站。
     * @param incremental true = 增量，跳過已經有車站資料嘅路線（預設）
     */
    fun deepIndexCtb(incremental: Boolean = true) {
        viewModelScope.launch { deepIndexCtbInternal(incremental) }
    }

    /** 內部版：可以俾 [maybeAutoRepair] 喺背景直接叫，唔使再包一層 launch */
    private suspend fun deepIndexCtbInternal(
        incremental: Boolean = true,
        silent: Boolean = false
    ) {
        coroutineScope {
            val all = _index.value.routes.filter { it.op == Operator.CTB }
            val done = _index.value.routeStops
                .filter { it.op == Operator.CTB }.map { it.route }.toSet()
            val targets = if (incremental) all.filter { it.route !in done } else all

            if (targets.isEmpty()) {
                _message.value = str(R.string.index_deep_already_done)
                return@coroutineScope
            }
            _busy.value = true
            val newStops = mutableListOf<StopDef>()
            val newRouteStops = mutableListOf<RouteStopDef>()

            // 分批並發（唔係逐條串行）—— 每批 8 條，時間大幅縮短
            var finished = 0
            for (batch in targets.chunked(DEEP_INDEX_CONCURRENCY)) {
                _indexStatus.value = str(
                    R.string.index_deep_ctb_progress,
                    batch.first().route, finished + 1, targets.size
                )
                // 每條路線自己返一組結果，避免並發寫同一個 mutableList
                val results = coroutineScope {
                    batch.map { r ->
                        async {
                            suspendCatching {
                                val rs = repo.ctbRouteStops(r.route, r.bound)
                                val stops = rs.mapNotNull { repo.ctbStop(it.stopId) }
                                rs to stops
                            }.getOrNull()
                        }
                    }.awaitAll()
                }
                for ((rs, stops) in results.filterNotNull()) {
                    newRouteStops.addAll(rs)
                    newStops.addAll(stops)
                }
                finished += batch.size
                _indexStatus.value = str(
                    R.string.index_deep_ctb_progress,
                    batch.last().route, finished, targets.size
                )
            }

            // 唔同路線會重複出現同一個站 → 去重先合併
            mergeAll(
                newStops.distinctBy { "${it.op.name}|${it.id}" },
                newRouteStops.distinctBy { "${it.op.name}|${it.route}|${it.stopId}" }
            )
            _indexStatus.value = str(R.string.index_deep_ctb_done, newStops.size)
            // silent = 背景自動修復：唔彈訊息、唔掝 busy（費事蓋住用戶自己開嘅動作）
            if (!silent) {
                _message.value = str(R.string.index_deep_ctb_msg)
                _busy.value = false
            }
        }
    }

    /** 專線小巴車站座標要逐條路線抓，畀「附近車站」用（建議 Wi-Fi 下執行） */
    /**
     * 專線小巴深度修復：車站座標要逐條路線抓。
     * @param incremental true = 增量，跳過已經有車站資料嘅路線（預設）
     */
    fun deepIndexGmb(incremental: Boolean = true) {
        viewModelScope.launch { deepIndexGmbInternal(incremental) }
    }

    /** 內部版：可以俾 [maybeAutoRepair] 喺背景直接叫，唔使再包一層 launch */
    private suspend fun deepIndexGmbInternal(
        incremental: Boolean = true,
        silent: Boolean = false
    ) {
        coroutineScope {
            val all = _index.value.routes.filter { it.op == Operator.GMB }
            val done = _index.value.routeStops
                .filter { it.op == Operator.GMB }.map { it.routeId }.toSet()
            val targets = if (incremental) all.filter { it.routeId !in done } else all

            if (targets.isEmpty()) {
                _message.value = str(R.string.index_deep_already_done)
                return@coroutineScope
            }
            _busy.value = true
            val newStops = mutableListOf<StopDef>()
            val newRouteStops = mutableListOf<RouteStopDef>()

            var finished = 0
            for (batch in targets.chunked(DEEP_INDEX_CONCURRENCY)) {
                _indexStatus.value = str(
                    R.string.index_deep_gmb_progress,
                    batch.first().route, finished + 1, targets.size
                )
                val results = coroutineScope {
                    batch.map { r ->
                        async {
                            suspendCatching {
                                val list = repo.gmbRouteStops(r.routeId, "1")
                                    .ifEmpty { repo.gmbRouteStops(r.routeId, "2") }
                                val rs = list.mapIndexed { idx, st ->
                                    RouteStopDef(
                                        op = Operator.GMB, route = r.route, routeId = r.routeId,
                                        seq = idx + 1, stopId = st.id
                                    )
                                }
                                rs to list
                            }.getOrNull()
                        }
                    }.awaitAll()
                }
                for ((rs, list) in results.filterNotNull()) {
                    newRouteStops.addAll(rs)
                    newStops.addAll(list)
                }
                finished += batch.size
                _indexStatus.value = str(
                    R.string.index_deep_gmb_progress,
                    batch.last().route, finished, targets.size
                )
            }

            mergeAll(
                newStops.distinctBy { "${it.op.name}|${it.id}" },
                newRouteStops.distinctBy { "${it.op.name}|${it.route}|${it.stopId}" }
            )
            _indexStatus.value = str(R.string.index_deep_gmb_done, newStops.size)
            if (!silent) {
                _message.value = str(R.string.index_deep_gmb_msg)
                _busy.value = false
            }
        }
    }

    // ---- 輕鐵特別班次（9xx 等）----

    private val _specialRoutes = MutableStateFlow<List<SpecialRouteInfo>>(emptyList())
    val specialRoutes: StateFlow<List<SpecialRouteInfo>> = _specialRoutes.asStateFlow()

    private val _scanningSpecial = MutableStateFlow(false)
    val scanningSpecial: StateFlow<Boolean> = _scanningSpecial.asStateFlow()

    private var specialStore: SpecialRoutesStore? = null

    /** 掃描結果有效期：過咗就自動重掃（官方會加減特別班次） */
    private val SPECIAL_SCAN_TTL_MS = 7L * 24 * 60 * 60 * 1000

    private val _specialAutoScanned = MutableStateFlow(false)
    val specialAutoScanned: StateFlow<Boolean> = _specialAutoScanned.asStateFlow()

    /**
     * 鐵路頁開嗰陣載入上次嘅掃描結果，**並且喺需要時自動重掃**。
     *
     * 以前要用戶自己撳「掃描特別班次」，唔撳就永遠冇。而家：
     *   - 有快取且未過期（7 日）→ 即刻顯示，唔發請求
     *   - 冇快取／已過期       → 自動喺背景掃一次（唔彈訊息、唔阻住用）
     * 用戶仍然可以撳掣手動即時重掃。
     */
    fun loadSpecialRoutes(context: android.content.Context, autoScan: Boolean = true) {
        if (specialStore == null) specialStore = SpecialRoutesStore(context)
        val store = specialStore ?: return
        val cached = store.load()
        if (_specialRoutes.value.isEmpty() && cached.isNotEmpty()) {
            _specialRoutes.value = cached
        }
        if (!autoScan) return
        if (_scanningSpecial.value) return
        if (_specialAutoScanned.value) return

        val age = System.currentTimeMillis() - store.lastScanAt()
        val stale = cached.isEmpty() || store.lastScanAt() <= 0L || age > SPECIAL_SCAN_TTL_MS
        if (stale) {
            _specialAutoScanned.value = true
            scanLrSpecialRoutes()
        }
    }

    /**
     * 掃描輕鐵特別班次（如 9xx）。
     *
     * 官方冇提供「特別班次路線清單」，但預報接口會返呢啲班次，
     * 所以逐個站查預報、收集路線號，再減走常規 11 條線，
     * 剩低嘅就係特別班次 —— 唔使硬編碼 9xx 清單，官方加減班次都跟到。
     */
    fun scanLrSpecialRoutes() {
        viewModelScope.launch {
            _scanningSpecial.value = true
            _specialRoutes.value = emptyList()
            // 要寫明類型：AndroidViewModel.getApplication() 係泛型方法 <T: Application>，
            // 直接指派畀無類型嘅 val 會推斷唔到 T
            val ctx: android.app.Application = getApplication()
            val lines = RailData.lightRail(ctx)
            val regular = lines.map { it.id }.toSet()
            val stops = lines.flatMap { it.stops }.distinctBy { it.id }

            // route → 有呢個班次嘅站
            val found = linkedMapOf<String, MutableList<String>>()

            for (batch in stops.chunked(DEEP_INDEX_CONCURRENCY)) {
                val results = coroutineScope {
                    batch.map { st ->
                        async {
                            val routes = suspendCatching {
                                repo.lightRailEta(st.id, etaText())
                            }.getOrDefault(emptyList()).map { it.route }.distinct()
                            st.id to routes
                        }
                    }.awaitAll()
                }
                for ((stopId, routes) in results) {
                    for (r in routes) {
                        if (r.isBlank() || r in regular) continue
                        found.getOrPut(r) { mutableListOf() }.add(stopId)
                    }
                }
            }

            _specialRoutes.value = found.entries
                .map { (route, ids) ->
                    val uniq = ids.distinct()
                    SpecialRouteInfo(
                        route = route,
                        stopIds = uniq,
                        stopNames = uniq.map { RailData.nameOf(ctx, Operator.LRT, it, AppLocale.isEnglish()) }
                    )
                }
                .sortedWith(compareBy<SpecialRouteInfo> { it.route.length }.thenBy { it.route })

            // 存落本地，下次開鐵路頁即刻顯示（唔使每次都掃）
            runCatching {
                val ctx: android.app.Application = getApplication()
                val store = specialStore
                    ?: SpecialRoutesStore(ctx).also { specialStore = it }
                store.save(_specialRoutes.value)
            }
            _scanningSpecial.value = false
        }
    }

    // ============ 自動背景修復 ============

    private val _autoRepairDone = MutableStateFlow(false)
    val autoRepairDone: StateFlow<Boolean> = _autoRepairDone.asStateFlow()

    /** 係咪「而家」喺背景補緊 —— 主頁嗰句提示淨係呢段時間先顯示 */
    private val _autoRepairing = MutableStateFlow(false)
    val autoRepairing: StateFlow<Boolean> = _autoRepairing.asStateFlow()

    /**
     * 索引建立完之後，自動喺背景補城巴／小巴嘅車站。
     *
     * 以前要用戶自己撳「修復城巴／修復小巴」，唔撳就永遠係 0 個站、
     * 附近車站淨係得九巴。而家索引一建好就自動補（增量 + 並發，好快），
     * 做過一次就記低，唔會每次開 App 都重做。
     */
    fun maybeAutoRepair() {
        if (_autoRepairDone.value || _autoRepairing.value || _busy.value) return
        val idx = _index.value
        val ctbMissing = idx.routes.any { it.op == Operator.CTB } &&
            idx.stops.none { it.op == Operator.CTB }
        val gmbMissing = idx.routes.any { it.op == Operator.GMB } &&
            idx.stops.none { it.op == Operator.GMB }
        if (!ctbMissing && !gmbMissing) {
            // 冇嘢要補（例如已經補過，或者根本冇呢啲路線）→ 直接當完成
            _autoRepairDone.value = true
            return
        }
        _autoRepairing.value = true
        viewModelScope.launch {
            try {
                // 唔設 busy、唔彈訊息 —— 靜靜地喺背景做，唔阻住用戶揾車
                if (ctbMissing) deepIndexCtbInternal(silent = true)
                if (gmbMissing) deepIndexGmbInternal(silent = true)
            } finally {
                // 無論成功定失敗都要收尾：否則主頁提示會永遠掛住
                _autoRepairing.value = false
                _autoRepairDone.value = true
            }
        }
    }

    fun indexStats(): List<IndexStat> {
        val idx = _index.value
        return Operator.values().map { op ->
            IndexStat(
                op = op,
                routes = idx.routes.count { it.op == op },
                stops = idx.stops.count { it.op == op }
            )
        }
    }

    // ============ 搜尋 ============

    fun setQuery(q: String) {
        _query.value = q
        val key = q.trim()
        if (key.isBlank()) {
            _results.value = emptyList()
            return
        }
        val upper = key.uppercase()

        val fromIndex = _index.value.routes.filter {
            it.route.uppercase().contains(upper) ||
                it.routeId.uppercase().contains(upper) ||
                it.orig.contains(upper, true) ||
                it.dest.contains(upper, true)
        }.sortedWith(
            compareBy<RouteDef> { it.op.ordinal }
                .thenBy { it.route.length }
                .thenBy { it.route }
        ).take(150)

        val withMtrBus = if (upper.matches(Regex("^K\\d+[A-Z]?$")) &&
            fromIndex.none { it.op == Operator.MTR_BUS }
        ) {
            fromIndex + RouteDef(op = Operator.MTR_BUS, route = upper, dest = str(R.string.index_mtrbus_hint))
        } else fromIndex

        _results.value = withMtrBus
    }

    /**
     * 獨立嘅車站搜尋：淨係搵車站，唔撈路線。
     * 同時計出該站停靠嘅路線號，撳入去可以直接睇到站預報。
     */
    fun setStopQuery(q: String) {
        _stopQuery.value = q
        val key = q.trim()
        if (key.isBlank()) {
            _stopResults.value = emptyList()
            return
        }
        val upper = key.uppercase()
        val idx = _index.value

        // 路線號 → 車站 ID 嘅反向表，用嚟計每站停靠嘅路線
        val routesByStop = idx.routeStops
            .groupBy { "${it.op.name}|${it.stopId}" }
            .mapValues { e -> e.value.map { it.route }.distinct() }

        val hits = idx.stops
            .asSequence()
            .filter {
                it.name.contains(key, true) ||
                    it.nameEn.contains(upper, true) ||
                    it.id.uppercase().contains(upper)
            }
            // 站名開頭命中排最前，其次站名包含，最後淨係站號命中
            .map { st ->
                val rank = when {
                    st.name.startsWith(key, true) -> 0
                    st.name.contains(key, true) -> 1
                    st.nameEn.startsWith(upper, true) -> 2
                    st.nameEn.contains(upper, true) -> 3
                    else -> 4
                }
                rank to st
            }
            .sortedWith(compareBy<Pair<Int, StopDef>> { it.first }
                .thenBy { it.second.name.length }
                .thenBy { it.second.name })
            .take(80)
            .map { (_, st) ->
                StopHit(
                    stop = st,
                    routes = routesByStop["${st.op.name}|${st.id}"]
                        .orEmpty()
                        .sortedWith(compareBy<String> { it.length }.thenBy { it })
                )
            }
            .toList()

        _stopResults.value = hits
    }

    /** 鐵路線分組（畀「鐵路」分頁用）：港鐵重鐵 + 輕鐵 */
    fun railGroups(): List<Pair<Operator, List<RailLine>>> {
        val ctx = getApplication<Application>()
        return listOf(
            Operator.MTR_HR to RailData.heavyRail(ctx),
            Operator.LRT to RailData.lightRail(ctx)
        )
    }

    /**
     * 由一條到站預報（[EtaEntry]）反查對應嘅 [RouteDef]。
     *
     * 主頁改成「一條班次一張卡」之後，撳卡要跳去嗰條路線嘅沿途車站，
     * 所以要由「路線號」反查索引入面完整嘅 RouteDef（bound / serviceType / routeId）。
     * 同一個路線號可能有多個方向，優先揀總站名同預報目的地對得上嗰個。
     */
    fun routeOfEta(e: EtaEntry): RouteDef {
        val idx = _index.value
        val same = idx.routes.filter { it.op == e.op && it.route == e.route }
        if (same.isEmpty()) {
            // 索引冇（例如鐵路）→ 兜底，淨靠路線號同目的地開一條
            return RouteDef(op = e.op, route = e.route, dest = e.dest)
        }
        // 優先用總站名匹配預報嘅目的地
        return same.firstOrNull { it.dest == e.dest }
            ?: same.firstOrNull { e.dest.isNotBlank() && (it.dest.contains(e.dest) || e.dest.contains(it.dest)) }
            ?: same.first()
    }

    fun routeOf(op: Operator, line: RailLine): RouteDef = RouteDef(
        op = op,
        route = if (enUi && line.nameEn.isNotBlank()) line.nameEn else line.name,
        routeId = line.id,
        orig = line.orig,
        dest = line.dest
    )

    // ============ 路線 → 車站 ============

    fun openRoute(rd: RouteDef) {
        _selectedRoute.value = rd
        _routeStops.value = emptyList()
        _message.value = null
        viewModelScope.launch {
            _busy.value = true
            val list: List<StopWithSeq> = suspendCatching {
                when (rd.op) {
                    Operator.KMB, Operator.NLB, Operator.MTR_HR, Operator.LRT -> stopsFromIndex(rd)
                    Operator.CTB -> stopsForCtb(rd)
                    Operator.GMB -> stopsForGmb(rd)
                    Operator.MTR_BUS -> stopsForMtr(rd)
                }
            }.onFailure { _message.value = str(R.string.stops_failed, it.message.orEmpty()) }
                .getOrDefault(emptyList())
            _routeStops.value = list
            _busy.value = false
            if (list.isEmpty() && _message.value == null) _message.value = str(R.string.stops_none_msg)
        }
    }

    private suspend fun stopsFromIndex(rd: RouteDef): List<StopWithSeq> {
        val idx = _index.value
        return idx.routeStops
            .filter {
                it.op == rd.op && it.route == rd.route && it.bound == rd.bound &&
                    it.serviceType == rd.serviceType && it.routeId == rd.routeId
            }
            .sortedBy { it.seq }
            .mapNotNull { rs ->
                idx.stops.firstOrNull { it.op == rd.op && it.id == rs.stopId }
                    ?.let { StopWithSeq(it, rs.seq) }
            }
    }

    private suspend fun stopsForCtb(rd: RouteDef): List<StopWithSeq> {
        val idx = _index.value
        var rs = idx.routeStops.filter { it.op == Operator.CTB && it.route == rd.route && it.bound == rd.bound }

        if (rs.isEmpty()) {
            // 官方 bound 標記（I/O）偶有出入：先試原本方向，搵唔到再試相反方向
            rs = suspendCatching { repo.ctbRouteStops(rd.route, rd.bound) }.getOrDefault(emptyList())
            if (rs.isEmpty()) {
                val alt = if (rd.bound.equals("I", true)) "O" else "I"
                rs = suspendCatching { repo.ctbRouteStops(rd.route, alt) }.getOrDefault(emptyList())
            }
            if (rs.isEmpty()) return emptyList()

            mergeAll(emptyList(), rs)

            // 車站名／座標要逐個請求，串行會拖到幾十秒、容易中途失敗 —— 改成分批並發
            val known = idx.stops.map { "${it.op.name}|${it.id}" }.toMutableSet()
            val toFetch = rs.map { it.stopId }.distinct().filter { known.add("CTB|$it") }
            val fetched = coroutineScope {
                toFetch.chunked(8).flatMap { batch ->
                    batch.map { id -> async { suspendCatching { repo.ctbStop(id) }.getOrNull() } }
                        .awaitAll()
                        .filterNotNull()
                }
            }
            if (fetched.isNotEmpty()) mergeAll(fetched, emptyList())
        }

        val cur = _index.value
        // 關鍵：搵唔到站名都要用 stopId 兜底顯示，否則成個列表會被過濾成空、變成「暫時冇車站資料」
        return rs.sortedBy { it.seq }.map { s ->
            val stop = cur.stops.firstOrNull { it.op == Operator.CTB && it.id == s.stopId }
                ?: StopDef(op = Operator.CTB, id = s.stopId, name = s.stopId)
            StopWithSeq(stop, s.seq)
        }
    }

    private suspend fun stopsForGmb(rd: RouteDef): List<StopWithSeq> {
        val list = suspendCatching { repo.gmbRouteStops(rd.routeId, "1") }.getOrDefault(emptyList())
            .ifEmpty { suspendCatching { repo.gmbRouteStops(rd.routeId, "2") }.getOrDefault(emptyList()) }
        if (list.isEmpty()) return emptyList()
        // 冇站名就用站號兜底，避免整條路線顯示成「暫時冇車站資料」
        val withName = coroutineScope {
            list.chunked(8).flatMap { batch ->
                batch.map { s ->
                    async {
                        if (s.name.isNotBlank()) s
                        else suspendCatching { repo.gmbStop(s.id) }.getOrNull()
                            ?: s.copy(name = s.id)
                    }
                }.awaitAll()
            }
        }
        mergeAll(withName, emptyList())
        return withName.mapIndexed { i, s -> StopWithSeq(s, i + 1) }
    }

    private suspend fun stopsForMtr(rd: RouteDef): List<StopWithSeq> {
        val (stops, _) = repo.mtrBus(rd.route)
        mergeAll(stops, emptyList())
        return stops.mapIndexed { i, s -> StopWithSeq(s, i + 1) }
    }

    // ============ 到站預報 ============

    /**
     * 站號正規化：若傳入嘅 stop.id 明顯唔係站號（例如誤填咗 routeId），
     * 就改用索引入面同名同營辦商嘅站（攞佢嘅站號同座標），
     * 否則攞去查 ETA 一定失敗、畫面只會顯示「暫時冇班次資料」。
     */
    private fun normalizeStop(stop: StopDef): StopDef {
        if (stop.id.isBlank() || stop.id.length <= 12) return stop
        val hit = _index.value.stops.firstOrNull {
            it.op == stop.op && it.name == stop.name && it.id.length <= 12
        }
        return hit ?: stop
    }

    fun openStop(stop: StopDef, route: RouteDef? = null) {
        val fixed = normalizeStop(stop)
        _selectedStop.value = fixed
        _etas.value = emptyList()
        loadEta(fixed, route ?: _selectedRoute.value)
    }

    fun refreshEta() {
        val stop = _selectedStop.value ?: return
        loadEta(stop, _selectedRoute.value)
    }

    /**
     * 抽出來共用嘅「抓某個站、某條路線嘅 ETA」。
     * [loadEta]（單站頁）同 [loadRouteTimetable]（全線班次表）都用佢，
     * 免得兩邊邏輯分叉。
     */
    private suspend fun etasFor(stop: StopDef, route: RouteDef?): Pair<List<EtaEntry>, String?> {
        var alert: String? = null
        val list = suspendCatching {
            when (stop.op) {
                Operator.KMB -> repo.kmbEta(stop.id).let { all ->
                    if (route == null || route.op != Operator.KMB) all
                    else all.filter { it.route == route.route }
                }
                Operator.CTB -> repo.ctbEta(stop.id, route?.route ?: "")
                Operator.NLB -> repo.nlbEta(route?.routeId.orEmpty(), stop.id)
                Operator.GMB -> repo.gmbEta(stop.id, route?.routeId.orEmpty())
                Operator.MTR_BUS -> repo.mtrBus(route?.route ?: "").second
                Operator.MTR_HR -> {
                    val ctx = getApplication<Application>()
                    val line = route?.routeId
                        ?: RailData.linesOf(ctx, Operator.MTR_HR, stop.id).firstOrNull()?.id
                        ?: return@suspendCatching emptyList()
                    val (etas, a) = repo.mtrHeavyRailEta(line, stop.id, etaText()) { code ->
                        RailData.nameOf(ctx, Operator.MTR_HR, code, AppLocale.isEnglish())
                    }
                    alert = a      // 港鐵官方服務公告（如有）
                    etas
                }
                Operator.LRT -> repo.lightRailEta(stop.id, etaText())
            }
        }.getOrDefault(emptyList())
        return list to alert
    }

    // ============ APK 下載 + 安裝（移植自 code-to-app ApkUpdateInstaller）============

    private val _downloadState =
        MutableStateFlow<com.hketa.app.data.ApkInstaller.State>(
            com.hketa.app.data.ApkInstaller.State.Idle
        )
    val downloadState: StateFlow<com.hketa.app.data.ApkInstaller.State> =
        _downloadState.asStateFlow()

    /**
     * App 內下載 APK 並自動開安裝器。
     *
     * @param sha256 由 CI 生成嘅 `<asset>.sha256`；有就校驗，冇就淨係下載
     */
    fun downloadAndInstall(release: com.hketa.app.data.GitHubRelease) {
        val asset = com.hketa.app.data.UpdateChecker.apkAsset(release) ?: run {
            _message.value = str(R.string.update_no_asset)
            return
        }
        viewModelScope.launch {
            // Android 8+ 要用戶先授權「允許安裝未知來源」
            if (!com.hketa.app.data.ApkInstaller.canInstall(getApplication())) {
                com.hketa.app.data.ApkInstaller.openInstallPermission(getApplication())
                _message.value = str(R.string.update_need_install_permission)
                return@launch
            }
            val sha = suspendCatching { fetchSha256(asset.browser_download_url) }
                .getOrNull()
            com.hketa.app.data.ApkInstaller.download(
                context = getApplication(),
                url = asset.browser_download_url,
                fileName = asset.name.ifBlank { "HKATE-update.apk" },
                expectedSha256 = sha
            ) { _downloadState.value = it }
        }
    }

    /** 校驗檔：同 APK 同名加 `.sha256`（CI 生成）。搵唔到就唔校驗，唔會失敗。 */
    private suspend fun fetchSha256(apkUrl: String): String? =
        withContext(Dispatchers.IO) {
            val txt = runCatching {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                client.newCall(
                    okhttp3.Request.Builder().url("$apkUrl.sha256").build()
                ).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    r.body?.string()
                }
            }.getOrNull() ?: return@withContext null
            // 常見格式：「<hash>  <檔名>」或者淨係 hash
            txt.trim().lineSequence().firstOrNull()
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.firstOrNull()
                ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
        }

    fun cancelDownload() {
        com.hketa.app.data.ApkInstaller.cancel()
        _downloadState.value = com.hketa.app.data.ApkInstaller.State.Idle
    }

    fun resetDownload() {
        _downloadState.value = com.hketa.app.data.ApkInstaller.State.Idle
    }

    fun installDownloaded(file: java.io.File) {
        com.hketa.app.data.ApkInstaller.install(getApplication(), file)
    }

    // ============ 班次時間表 + 公告 ============

    private val _routeTimetable =
        MutableStateFlow<com.hketa.app.data.RouteTimetable?>(null)
    val routeTimetable: StateFlow<com.hketa.app.data.RouteTimetable?> =
        _routeTimetable.asStateFlow()

    private val _routeNotices =
        MutableStateFlow<List<com.hketa.app.data.Notice>>(emptyList())
    val routeNotices: StateFlow<List<com.hketa.app.data.Notice>> =
        _routeNotices.asStateFlow()

    private val _routeNoticesBusy = MutableStateFlow(false)
    val routeNoticesBusy: StateFlow<Boolean> = _routeNoticesBusy.asStateFlow()

    private fun ensureTimetableStore() {
        if (!::timetableStore.isInitialized) {
            timetableStore = com.hketa.app.data.TimetableStore(getApplication())
        }
    }

    /**
     * 構建呢條路線嘅班次表。
     *
     * 做法：並發抓沿線每個站嘅 ETA，第 N 班車 = 每個站 ETA 清單嘅第 N 項
     * （官方 ETA 本身已按到站先後排好）。呢個係真實資料，唔係估算。
     *
     * 班次間隔 = 同一個站相鄰兩班嘅分鐘差。
     * 頭／尾班車官方冇接口，所以用 [TimetableStore] 觀察累積。
     */
    fun loadRouteTimetable(rd: RouteDef) {
        viewModelScope.launch {
            _routeTimetable.value = null
            val stops = _routeStops.value
            if (stops.isEmpty()) return@launch

            // 太多站會打爆官方接口：淨係取樣最多 12 個（頭、尾、中間均勻）
            val sampled = if (stops.size <= 12) stops else {
                val step = (stops.size - 1) / 11.0
                (0..11).map { stops[(it * step).toInt().coerceIn(0, stops.size - 1)] }
                    .distinctBy { it.stop.id }
            }

            _busy.value = true
            val results = coroutineScope {
                sampled.map { sw ->
                    async {
                        val (list, _) = etasFor(sw.stop, rd)
                        sw.stop to list.sortedWith(compareBy(nullsLast()) { e: EtaEntry -> e.minutes })
                    }
                }.awaitAll()
            }
            _busy.value = false

            // 站名連站號（跟班次表版面：「海麗邨巴士總站 (SS667)」）
            val names = results.map { (st, _) ->
                val base = if (com.hketa.app.util.AppLocale.isEnglish() && st.nameEn.isNotBlank())
                    st.nameEn else st.name.ifBlank { st.id }
                val code = st.id.trim()
                if (code.isNotBlank() && code.length <= 12) "$base ($code)" else base
            }
            // 以「最多班次嗰個站」決定有幾多班
            val tripCount = results.maxOfOrNull { it.second.size } ?: 0
            if (tripCount == 0) {
                _routeTimetable.value = com.hketa.app.data.RouteTimetable(
                    stopNames = names,
                    state = com.hketa.app.data.ServiceState.ENDED.name
                )
                return@launch
            }

            val trips = (0 until tripCount.coerceAtMost(6)).map { i ->
                com.hketa.app.data.Trip(
                    seq = i + 1,
                    cells = results.map { (_, list) ->
                        val e = list.getOrNull(i)
                        com.hketa.app.data.TripCell(
                            stopName = "",
                            minutes = e?.minutes,
                            clock = e?.clock.orEmpty()
                        )
                    }
                )
            }

            // 班次間隔：用第一個站相鄰兩班嘅分鐘差
            val first = results.firstOrNull()?.second.orEmpty()
            val headway = if (first.size >= 2) {
                val a = first[0].minutes
                val b = first[1].minutes
                if (a != null && b != null) (b - a).takeIf { it in 1..180 } else null
            } else null

            // 觀察累積（頭／尾班車）
            ensureTimetableStore()
            val allClocks = results.flatMap { it.second.map { e -> e.clock } }
            val rec = timetableStore.observe(
                "${rd.op.name}|${rd.route}|${rd.bound}|${rd.serviceType}",
                allClocks
            )

            _routeTimetable.value = com.hketa.app.data.RouteTimetable(
                trips = trips,
                headwayMin = headway,
                firstObserved = rec.firstSeen.ifBlank { null },
                lastObserved = rec.lastSeen.ifBlank { null },
                state = com.hketa.app.data.ServiceState.RUNNING.name,
                stopNames = names
            )
        }
    }

    /**
     * 公告：官方服務公告 + App 公告。
     *
     * 官方：港鐵嘅 getSchedule 會一併返 service alert（改道／延誤／暫停），
     *       係真實官方公告；其他營辦商嘅 ETA 接口冇呢個欄位。
     * App：由 GitHub releases 拉（同「版本更新」同一個來源）。
     */
    /**
     * 載入**路線公告**（唔係 App 更新公告）。
     *
     * 老實講清楚：香港各巴士營辦商嘅 ETA 接口**冇公告欄位**，
     * 得港鐵系（`getSchedule`）會一併返 service alert。所以：
     *
     *   ① 港鐵系 → 真·官方服務公告（改道／延誤／暫停）
     *   ② 全部營辦商 → 由官方 ETA 即時數據**推導**嘅路線狀況
     *      （全線冇班次／部分站冇班次／班次間隔異常／班次極疏）
     *
     * ② 嗰啲會標明「由官方數據推導」，絕對唔會冒充官方公告。
     */
    fun loadRouteNotices(rd: RouteDef) {
        viewModelScope.launch {
            _routeNoticesBusy.value = true
            val out = mutableListOf<com.hketa.app.data.Notice>()

            val stops = _routeStops.value

            // ① 官方服務公告（淨係港鐵系有）
            if (rd.op == Operator.MTR_HR || rd.op == Operator.LRT) {
                val probe = stops.firstOrNull()?.stop
                if (probe != null) {
                    val (_, alert) = etasFor(probe, rd)
                    if (!alert.isNullOrBlank()) {
                        out.add(
                            com.hketa.app.data.Notice(
                                title = str(R.string.notice_official_title),
                                body = alert,
                                time = str(R.string.notice_just_now),
                                source = str(R.string.notice_source_official)
                            )
                        )
                    }
                }
            }

            // ② 由官方 ETA 數據推導嘅路線狀況
            out.addAll(buildRouteConditionNotices(rd, stops))

            _routeNotices.value = out
            _routeNoticesBusy.value = false
        }
    }

    /**
     * 由官方 ETA 即時數據**推導**路線狀況。
     *
     * 呢啲唔係官方公告，係我哋撳官方數據計出嚟嘅觀察值 ——
     * 所以每條都會標「由官方數據推導」，唔會冒充官方。
     *
     * 會搵出四種狀況：
     *   A. 全線冇班次      → 可能暫停服務／已過尾班車
     *   B. 部分站冇班次    → 列出邊幾個站冇資料
     *   C. 班次間隔異常大  → 可能延誤／改道
     *   D. 班次極疏        → 間隔大過 30 分鐘
     */
    private suspend fun buildRouteConditionNotices(
        rd: RouteDef,
        stops: List<com.hketa.app.data.StopWithSeq>
    ): List<com.hketa.app.data.Notice> {
        val out = mutableListOf<com.hketa.app.data.Notice>()
        if (stops.isEmpty()) return out

        // 取樣：頭、尾、中間，最多 8 個站（唔好打爆官方接口）
        val sample = if (stops.size <= 8) stops else listOf(
            stops.first(),
            stops[stops.size / 4],
            stops[stops.size / 2],
            stops[stops.size * 3 / 4],
            stops.last(),
            stops[1],
            stops[stops.size - 2],
            stops[stops.size / 3]
        ).distinctBy { it.stop.id }

        // 並發抓各站 ETA（8 個站一齊發，唔係逐個等）
        val fetched = coroutineScope {
            sample.map { ws ->
                async {
                    val list = runCatching { etasFor(ws.stop, rd).first }
                        .getOrDefault(emptyList())
                    @Suppress("RemoveExplicitTypeArguments")
                    Pair<com.hketa.app.data.StopDef, List<com.hketa.app.data.EtaEntry>>(
                        ws.stop, list
                    )
                }
            }.awaitAll()
        }

        val noData = fetched.filter { it.second.isEmpty() }
        val all = fetched.flatMap { it.second }

        // A. 全線冇班次
        if (noData.size == fetched.size && fetched.isNotEmpty()) {
            out.add(
                com.hketa.app.data.Notice(
                    title = str(R.string.notice_line_no_service_title),
                    body = str(R.string.notice_line_no_service_body),
                    time = str(R.string.notice_just_now),
                    source = str(R.string.notice_source_derived)
                )
            )
            return out
        }

        // B. 部分站冇班次
        if (noData.isNotEmpty()) {
            val names = noData.take(3).joinToString("、") {
                it.first.name.ifBlank { it.first.id }
            }
            val more = if (noData.size > 3) str(R.string.notice_and_more, noData.size - 3) else ""
            out.add(
                com.hketa.app.data.Notice(
                    title = str(R.string.notice_partial_no_data_title),
                    body = str(R.string.notice_partial_no_data_body, names, more),
                    time = str(R.string.notice_just_now),
                    source = str(R.string.notice_source_derived)
                )
            )
        }

        // C / D. 班次間隔
        val mins = all.mapNotNull { it.minutes }.filter { it in 0..180 }.sorted()
        if (mins.size >= 2) {
            val gaps = mins.zipWithNext().map { (a, b) -> b - a }.filter { it > 0 }
            val maxGap = gaps.maxOrNull()
            if (maxGap != null && maxGap >= 30) {
                out.add(
                    com.hketa.app.data.Notice(
                        title = str(R.string.notice_sparse_title),
                        body = str(
                            if (maxGap >= 60) R.string.notice_very_sparse_body
                            else R.string.notice_sparse_body,
                            maxGap
                        ),
                        time = str(R.string.notice_just_now),
                        source = str(R.string.notice_source_derived)
                    )
                )
            }
        }

        // 一切正常 → 明確講「冇異常」，等用戶知道真係查過
        if (out.isEmpty()) {
            out.add(
                com.hketa.app.data.Notice(
                    title = str(R.string.notice_line_normal_title),
                    body = str(R.string.notice_line_normal_body),
                    time = str(R.string.notice_just_now),
                    source = str(R.string.notice_source_derived)
                )
            )
        }
        return out
    }

    private fun loadEta(stop: StopDef, route: RouteDef?) {
        viewModelScope.launch {
            _busy.value = true
            val (raw, alert) = etasFor(stop, route)
            val list = raw
            alert?.let { _message.value = it }
            run { if (list.isNotEmpty()) _lastUpdateAt.value = System.currentTimeMillis() }
            if (list.isEmpty() && alert == null) {
                // 交畀下面統一提示
            }
            _etas.value = list.sortedWith(compareBy(nullsLast()) { e: EtaEntry -> e.minutes })
            _busy.value = false
            if (list.isEmpty() && _message.value == null) {
                _message.value = str(R.string.eta_empty)
            }
        }
    }

    // ============ 主頁 ============

    private fun ensureFavorites() {
        if (!::favoritesStore.isInitialized) {
            favoritesStore = FavoritesStore(getApplication())
        }
    }

    fun refreshFavorites() {
        ensureFavorites()
        _favorites.value = favoritesStore.all()
    }

    /** 收藏頁：一次過拉晒所有收藏車站嘅到站預報 */
    fun loadFavoriteEtas() {
        viewModelScope.launch {
            ensureFavorites()
            refreshFavorites()
            val list = favoritesStore.all()
            if (list.isEmpty()) {
                _favoriteEtas.value = emptyMap()
                return@launch
            }
            _favoritesLoading.value = true
            val map = coroutineScope {
                list.map { fav ->
                    async {
                        val etas = suspendCatching { etasForFavorite(fav) }.getOrDefault(emptyList())
                        fav.key to etas.sortedWith(compareBy(nullsLast()) { e: EtaEntry -> e.minutes })
                    }
                }.awaitAll().toMap()
            }
            _favoriteEtas.value = map
            _favoritesLoading.value = false
        }
    }

    private suspend fun etasForFavorite(fav: FavoriteStop): List<EtaEntry> = when (fav.op) {
        Operator.KMB -> repo.kmbEta(fav.id).let { all ->
            if (fav.route.isBlank()) all.take(6)
            else all.filter { it.route == fav.route }
        }
        Operator.LRT -> repo.lightRailEta(fav.id, etaText())
        Operator.MTR_HR -> {
            val line = RailData.linesOf(getApplication(), Operator.MTR_HR, fav.id).firstOrNull()?.id
            if (line == null) emptyList()
            else repo.mtrHeavyRailEta(line, fav.id, etaText()) {
                RailData.nameOf(getApplication(), Operator.MTR_HR, it, AppLocale.isEnglish())
            }.first
        }
        Operator.CTB -> if (fav.route.isBlank()) emptyList() else repo.ctbEta(fav.id, fav.route)
        Operator.NLB -> if (fav.routeId.isBlank()) emptyList() else repo.nlbEta(fav.routeId, fav.id)
        Operator.GMB -> if (fav.routeId.isBlank()) emptyList() else repo.gmbEta(fav.id, fav.routeId)
        Operator.MTR_BUS -> repo.mtrBus(fav.route).second
    }

    fun isFavorite(stop: StopDef, route: RouteDef? = null): Boolean {
        ensureFavorites()
        return favoritesStore.contains(FavoritesStore.from(stop, route).key)
    }

    /** 收藏／取消收藏；回傳收藏後嘅狀態（true = 已收藏） */
    fun toggleFavorite(stop: StopDef, route: RouteDef? = null): Boolean {
        ensureFavorites()
        val f = FavoritesStore.from(stop, route)
        val was = favoritesStore.contains(f.key)
        favoritesStore.toggle(f)
        refreshFavorites()
        _message.value = str(if (was) R.string.fav_removed else R.string.fav_added)
        return !was
    }

    fun removeFavorite(key: String) {
        ensureFavorites()
        favoritesStore.remove(key)
        refreshFavorites()
    }

    /**
     * 載入主頁：定位 → 搵附近車站 → 逐個站拉到站預報。
     * 九巴一個請求就攞到嗰個站全部路線嘅 ETA，所以主頁以九巴站最快最齊。
     */
    fun loadHome(context: android.content.Context) {
        viewModelScope.launch {
            _homeLoading.value = true
            val ok = suspendCatching { loadHomeInternal(context) }.getOrDefault(false)
            _homeLoading.value = false
            if (!ok) _message.value = str(R.string.home_need_location)
            else if (_homeItems.value.isEmpty()) _message.value = str(R.string.home_empty)
        }
    }

    /** 核心：定位 → 搵附近車站 → 逐個站拉到站預報；成功返 true */
    private suspend fun buildHomeItems(fix: LocationProvider.Fix): Boolean {
        _nearbyOrigin.value = "%.5f, %.5f（${fix.source}）".format(fix.lat, fix.lon)

            val idx = _index.value
            // bbox 預篩：7250 個站淨係對方框內嘅候選做三角函數
            val stops = withContext(Dispatchers.Default) {
                stopsWithin(idx.stops, fix.lat, fix.lon, HOME_RADIUS_M.toDouble())
                    .take(HOME_MAX_STOPS)
            }

            val items = coroutineScope {
                stops.map { (st, dist) ->
                    async {
                        // O(1) 查表（以前係逐個站掃 63150 條 routeStops）
                        val routes = routesOfStop(idx, st)
                        val etas = suspendCatching {
                            when (st.op) {
                                // 九巴：一個請求攞晒全站路線預報
                                Operator.KMB -> repo.kmbEta(st.id)
                                Operator.LRT -> repo.lightRailEta(st.id, etaText())
                                Operator.MTR_HR -> {
                                    val line = RailData.linesOf(
                                        getApplication(), Operator.MTR_HR, st.id
                                    ).firstOrNull()?.id
                                    if (line == null) emptyList()
                                    else repo.mtrHeavyRailEta(line, st.id, etaText()) {
                                        RailData.nameOf(getApplication(), Operator.MTR_HR, it, AppLocale.isEnglish())
                                    }.first
                                }
                                // 城巴／嶼巴／小巴要逐條路線查 —— 淨係查頭一條，慳請求
                                Operator.CTB -> {
                                    val r = routes.firstOrNull()
                                    if (r == null) emptyList() else repo.ctbEta(st.id, r)
                                }
                                Operator.NLB -> {
                                    val rid = idx.routes
                                        .firstOrNull { it.op == Operator.NLB && it.route == routes.firstOrNull() }
                                        ?.routeId
                                    if (rid.isNullOrBlank()) emptyList() else repo.nlbEta(rid, st.id)
                                }
                                Operator.GMB -> {
                                    val rid = idx.routes
                                        .firstOrNull { it.op == Operator.GMB && it.route == routes.firstOrNull() }
                                        ?.routeId
                                    if (rid.isNullOrBlank()) emptyList() else repo.gmbEta(st.id, rid)
                                }
                                Operator.MTR_BUS -> {
                                    val r = routes.firstOrNull()
                                    if (r == null) emptyList() else repo.mtrBus(r).second
                                }
                            }
                        }.getOrDefault(emptyList())
                            .sortedWith(compareBy(nullsLast()) { e: EtaEntry -> e.minutes })
                            .take(6)
                        HomeItem(st, routes, etas, dist)
                    }
                }.awaitAll()
            }

        _homeItems.value = items
        return items.isNotEmpty()
    }

    // ============ 附近車站 ============

    fun loadNearby(lat: Double, lon: Double, radiusMeters: Int = 3000) {
        // Dispatchers.IO：以前呢段喺主線程做（viewModelScope 預設 Main），
        // 要掃 7250 個站 × 63150 條對應，會明顯卡住 UI。
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            _nearbyOrigin.value = "%.5f, %.5f".format(lat, lon)
            val idx = _index.value
            // bbox 預篩 + 車站→路線索引（O(1) 查表，唔使逐個站掃 63150 條）
            val list = stopsWithin(idx.stops, lat, lon, radiusMeters.toDouble())
                .take(80)
                .map { (s, d) -> NearbyStop(s, d, routesOfStop(idx, s)) }
            _nearby.value = list
            _busy.value = false
            if (list.isEmpty()) {
                _message.value = str(R.string.nearby_empty)
            }
        }
    }

    suspend fun requestNearby(context: android.content.Context): Boolean {
        val fix = LocationProvider.current(context) ?: return false
        loadNearby(fix.lat, fix.lon)
        _nearbyOrigin.value = "%.5f, %.5f（${fix.source}）".format(fix.lat, fix.lon)
        return true
    }

    // ============ 工具 ============

    private suspend fun mergeAll(newStops: List<StopDef>, newRs: List<RouteStopDef>) {
        if (newStops.isEmpty() && newRs.isEmpty()) return
        val cur = _index.value
        val merged = cur.copy(
            stops = (cur.stops + newStops).distinctBy { "${it.op.name}|${it.id}" },
            routeStops = (cur.routeStops + newRs)
                .distinctBy { "${it.op.name}|${it.route}|${it.bound}|${it.routeId}|${it.stopId}" }
        )
        _index.value = merged
        store.saveSuspend(merged)
    }

    // ==================== 效能：車站→路線索引 + 距離預篩 ====================

    /**
     * ## 點解要呢個快取
     *
     * 以前 `loadNearby()` / `buildHomeItems()` 入面係咁寫嘅：
     *
     * ```kotlin
     * val routes = idx.routeStops                 // 63150 條
     *     .filter { it.op == st.op && it.stopId == st.id }
     * ```
     *
     * 即係**每一個站都要掃晒全部 routeStops**。近處 50 個站
     * = 50 × 63150 ≈ **315 萬次遍歷**，而且全部喺主線程 ——
     * 呢個係開 App 同每次自動定位重整（每 60 秒）卡住嘅主因之一。
     *
     * 而家建一次 HashMap（O(m)），之後每個站 O(1) 查表：
     * **315 萬次 → 63150 次**（快約 50 倍）。
     *
     * 快取以 `IndexData` 嘅**物件身份**做 key —— 索引一換（新 copy）
     * 就自動失效，唔使手動清。
     */
    private var routesByStopMap: Map<String, List<String>>? = null
    private var routesByStopFor: IndexData? = null

    private fun routesByStop(idx: IndexData): Map<String, List<String>> {
        val cached = routesByStopMap
        if (cached != null && routesByStopFor === idx) return cached

        val tmp = HashMap<String, LinkedHashSet<String>>(
            (idx.stops.size * 2).coerceAtLeast(1024)
        )
        for (rs in idx.routeStops) {
            tmp.getOrPut("${rs.op.name}|${rs.stopId}") { LinkedHashSet() }.add(rs.route)
        }
        // 排序一次，之後每次查都直接攞到排好嘅結果
        val built = HashMap<String, List<String>>(tmp.size)
        for ((k, v) in tmp) {
            built[k] = v.sortedWith(compareBy<String> { it.length }.thenBy { it })
        }
        routesByStopMap = built
        routesByStopFor = idx
        return built
    }

    /** 查某個站有邊啲路線 —— 建好索引後係 O(1)。 */
    private fun routesOfStop(idx: IndexData, s: StopDef): List<String> =
        routesByStop(idx)["${s.op.name}|${s.id}"] ?: emptyList()

    /**
     * 搵半徑內嘅車站，按距離排好。
     *
     * ## bounding box 預篩
     *
     * Haversine（下面 `distanceMeters`）有 sin/cos/atan2/sqrt，
     * 對 7250 個站逐個計會好貴。
     *
     * 所以先用**加減法**嘅經緯度方框濾走絕大部分站（純比較，極快），
     * 淨係對方框內嘅候選先做精確計算。3 km 半徑嘅方框喺香港
     * 大概淨係剩低幾十個站 —— 即係 7250 次三角函數 → 幾十次。
     */
    private fun stopsWithin(
        stops: List<StopDef>,
        lat: Double,
        lon: Double,
        radiusM: Double
    ): List<Pair<StopDef, Int>> {
        // 緯度 1° ≈ 111 320 m；經度 1° ≈ 111 320 × cos(lat) m
        val dLat = radiusM / 111_320.0
        val cosLat = java.lang.Math.cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        val dLon = radiusM / (111_320.0 * cosLat)
        val minLat = lat - dLat
        val maxLat = lat + dLat
        val minLon = lon - dLon
        val maxLon = lon + dLon

        val out = ArrayList<Pair<StopDef, Int>>(64)
        for (s in stops) {
            // 冇座標嘅站（例如未補到經緯度嘅）直接跳過
            if (s.lat == 0.0 && s.lon == 0.0) continue
            // 粗篩：純比較，唔使三角函數
            if (s.lat < minLat || s.lat > maxLat) continue
            if (s.lon < minLon || s.lon > maxLon) continue
            // 精算：淨係剩低嘅候選先做
            val d = distanceMeters(lat, lon, s.lat, s.lon)
            if (d <= radiusM) out.add(s to d.toInt())
        }
        out.sortBy { it.second }
        return out
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
