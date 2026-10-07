package com.hketa.app.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.hketa.app.util.AppLocale
import com.hketa.app.data.StopWithSeq
import com.hketa.app.data.UpdateChecker
import com.hketa.app.data.UpdateState
import com.hketa.app.R
import com.hketa.app.location.LocationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AppViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
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

    private val _stopResults = MutableStateFlow<List<StopDef>>(emptyList())
    val stopResults: StateFlow<List<StopDef>> = _stopResults.asStateFlow()

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

    init {
        viewModelScope.launch { ensureIndex() }
        refreshFavorites()
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
            l.stops.forEach { s -> out.add(StopDef(Operator.MTR_HR, s.id, s.name, s.lat, s.lon)) }
        }
        RailData.lightRail(ctx).forEach { l ->
            l.stops.forEach { s -> out.add(StopDef(Operator.LRT, s.id, s.name, s.lat, s.lon)) }
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
        val cached = if (force) null else store.load()
        if (cached != null && cached.routes.isNotEmpty() && cached.version >= INDEX_VERSION) {
            _index.value = cached
            _indexStatus.value = str(R.string.index_ready, cached.routes.size)
            return
        }
        buildIndex()
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            store.clear()
            _index.value = IndexData()
            buildIndex()
        }
    }

    private suspend fun buildIndex() {
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
            routes.addAll(repo.ctbRoutes())
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
            for (region in listOf("HKI", "KLN", "NT")) {
                routes.addAll(suspendCatching { repo.gmbRoutes(region) }.getOrDefault(emptyList()))
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
        store.save(data)
        _indexStatus.value = str(R.string.index_done, data.routes.size, data.stops.size)
        _busy.value = false
    }

    /** 城巴沒有全量車站接口，逐條路線補齊車站與座標 */
    fun deepIndexCtb() {
        viewModelScope.launch {
            val targets = _index.value.routes.filter { it.op == Operator.CTB }
            if (targets.isEmpty()) {
                _message.value = str(R.string.index_no_ctb)
                return@launch
            }
            _busy.value = true
            val newStops = mutableListOf<StopDef>()
            val newRouteStops = mutableListOf<RouteStopDef>()
            val known = _index.value.stops.map { "${it.op.name}|${it.id}" }.toMutableSet()
            // for + withIndex 而唔係 forEachIndexed（後者 lambda 唔係 suspend 上下文）
            for ((i, r) in targets.withIndex()) {
                _indexStatus.value = str(R.string.index_deep_ctb_progress, r.route, i + 1, targets.size)
                suspendCatching {
                    val rs = repo.ctbRouteStops(r.route, r.bound)
                    newRouteStops.addAll(rs)
                    for (s in rs) {
                        val key = "CTB|${s.stopId}"
                        if (known.add(key)) {
                            repo.ctbStop(s.stopId)?.let { newStops.add(it) }
                        }
                    }
                }
            }
            mergeAll(newStops, newRouteStops)
            _indexStatus.value = str(R.string.index_deep_ctb_done, newStops.size)
            _message.value = str(R.string.index_deep_ctb_msg)
            _busy.value = false
        }
    }

    /** 專線小巴車站座標要逐條路線抓，畀「附近車站」用（建議 Wi-Fi 下執行） */
    fun deepIndexGmb() {
        viewModelScope.launch {
            val targets = _index.value.routes.filter { it.op == Operator.GMB }
            if (targets.isEmpty()) {
                _message.value = str(R.string.index_no_gmb)
                return@launch
            }
            _busy.value = true
            val newStops = mutableListOf<StopDef>()
            val newRouteStops = mutableListOf<RouteStopDef>()
            // for + withIndex 而唔係 forEachIndexed（後者 lambda 唔係 suspend 上下文）
            for ((i, r) in targets.take(400).withIndex()) {
                _indexStatus.value = str(R.string.index_deep_gmb_progress, r.route, i + 1, targets.size)
                suspendCatching {
                    val list = repo.gmbRouteStops(r.routeId, "1")
                        .ifEmpty { repo.gmbRouteStops(r.routeId, "2") }
                    newRouteStops.addAll(
                        list.mapIndexed { idx, s ->
                            RouteStopDef(
                                op = Operator.GMB, route = r.route, routeId = r.routeId,
                                seq = idx + 1, stopId = s.id
                            )
                        }
                    )
                    newStops.addAll(list)
                }
            }
            mergeAll(newStops, newRouteStops)
            _indexStatus.value = str(R.string.index_deep_gmb_done, newStops.size)
            _message.value = str(R.string.index_deep_gmb_msg)
            _busy.value = false
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
            _stopResults.value = emptyList()
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

        // 車站搜尋：鐵路站優先，其次巴士站
        val stops = _index.value.stops
            .filter { it.name.contains(key, ignoreCase = true) || it.id.equals(upper, true) }
            .sortedWith(
                compareBy<StopDef> { it.op.ordinal }
                    .thenBy { it.name.length }
                    .thenBy { it.name }
            ).take(60)
        _stopResults.value = stops
    }

    /** 鐵路線分組（畀「鐵路」分頁用）：港鐵重鐵 + 輕鐵 */
    fun railGroups(): List<Pair<Operator, List<RailLine>>> {
        val ctx = getApplication<Application>()
        return listOf(
            Operator.MTR_HR to RailData.heavyRail(ctx),
            Operator.LRT to RailData.lightRail(ctx)
        )
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

    fun openStop(stop: StopDef, route: RouteDef? = null) {
        _selectedStop.value = stop
        _etas.value = emptyList()
        loadEta(stop, route ?: _selectedRoute.value)
    }

    fun refreshEta() {
        val stop = _selectedStop.value ?: return
        loadEta(stop, _selectedRoute.value)
    }

    private fun loadEta(stop: StopDef, route: RouteDef?) {
        viewModelScope.launch {
            _busy.value = true
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
                        val (etas, alert) = repo.mtrHeavyRailEta(line, stop.id, etaText()) { code ->
                            RailData.nameOf(ctx, Operator.MTR_HR, code)
                        }
                        alert?.let { _message.value = it }
                        etas
                    }
                    Operator.LRT -> repo.lightRailEta(stop.id, etaText())
                }
            }.onFailure { _message.value = str(R.string.eta_query_failed, it.message.orEmpty()) }
                .getOrDefault(emptyList())
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
                RailData.nameOf(getApplication(), Operator.MTR_HR, it)
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
            ensureFavorites()
            _favorites.value = favoritesStore.all()
            _homeLoading.value = true

            val fix = LocationProvider.current(context)
            if (fix == null) {
                _homeItems.value = emptyList()
                _homeLoading.value = false
                _message.value = str(R.string.home_need_location)
                return@launch
            }
            _nearbyOrigin.value = "%.5f, %.5f（${fix.source}）".format(fix.lat, fix.lon)

            val idx = _index.value
            val stops = idx.stops
                .filter { it.lat != 0.0 || it.lon != 0.0 }
                .mapNotNull { st ->
                    val d = distanceMeters(fix.lat, fix.lon, st.lat, st.lon)
                    if (d > HOME_RADIUS_M) return@mapNotNull null
                    st to d.toInt()
                }
                .sortedBy { it.second }
                .take(HOME_MAX_STOPS)

            val items = coroutineScope {
                stops.map { (st, dist) ->
                    async {
                        val routes = idx.routeStops
                            .filter { it.op == st.op && it.stopId == st.id }
                            .map { it.route }
                            .distinct()
                            .sortedWith(compareBy<String> { it.length }.thenBy { it })
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
                                        RailData.nameOf(getApplication(), Operator.MTR_HR, it)
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
            _homeLoading.value = false
            if (items.isEmpty()) _message.value = str(R.string.home_empty)
        }
    }

    // ============ 附近車站 ============

    fun loadNearby(lat: Double, lon: Double, radiusMeters: Int = 3000) {
        viewModelScope.launch {
            _busy.value = true
            _nearbyOrigin.value = "%.5f, %.5f".format(lat, lon)
            val idx = _index.value
            val list = idx.stops
                .filter { it.lat != 0.0 || it.lon != 0.0 }
                .mapNotNull { s ->
                    val d = distanceMeters(lat, lon, s.lat, s.lon)
                    if (d > radiusMeters) return@mapNotNull null
                    val routes = idx.routeStops
                        .filter { it.op == s.op && it.stopId == s.id }
                        .map { it.route }
                        .distinct()
                        .sortedWith(compareBy<String> { it.length }.thenBy { it })
                    NearbyStop(s, d.toInt(), routes)
                }
                .sortedBy { it.distanceMeters }
                .take(80)
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

    private fun mergeAll(newStops: List<StopDef>, newRs: List<RouteStopDef>) {
        if (newStops.isEmpty() && newRs.isEmpty()) return
        val cur = _index.value
        val merged = cur.copy(
            stops = (cur.stops + newStops).distinctBy { "${it.op.name}|${it.id}" },
            routeStops = (cur.routeStops + newRs)
                .distinctBy { "${it.op.name}|${it.route}|${it.bound}|${it.routeId}|${it.stopId}" }
        )
        _index.value = merged
        store.save(merged)
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
