package com.hketa.app.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hketa.app.data.EtaEntry
import com.hketa.app.data.EtaText
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
import com.hketa.app.data.StopWithSeq
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
    }

    /** 取本地化字串（語言切換後 Application 資源會跟住變） */
    private fun str(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    /** 資料層拼備註（月台／延誤／經馬場／卡數）要用嘅本地化模板 */
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

    init {
        viewModelScope.launch { ensureIndex() }
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
            runCatching { block() }
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
                        runCatching { repo.nlbStops(r.routeId) }.getOrDefault(emptyList())
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
            listOf("HKI", "KLN", "NT").forEach { region ->
                routes.addAll(runCatching { repo.gmbRoutes(region) }.getOrDefault(emptyList()))
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
            targets.forEachIndexed { i, r ->
                _indexStatus.value = str(R.string.index_deep_ctb_progress, r.route, i + 1, targets.size)
                runCatching {
                    val rs = repo.ctbRouteStops(r.route, r.bound)
                    newRouteStops.addAll(rs)
                    rs.forEach { s ->
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
            targets.take(400).forEachIndexed { i, r ->
                _indexStatus.value = str(R.string.index_deep_gmb_progress, r.route, i + 1, targets.size)
                runCatching {
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
        route = line.name,
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
            val list: List<StopWithSeq> = runCatching {
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
            rs = repo.ctbRouteStops(rd.route, rd.bound)
            mergeAll(emptyList(), rs)
            val missing = rs.filter { s -> idx.stops.none { it.op == Operator.CTB && it.id == s.stopId } }
            val newStops = missing.mapNotNull { runCatching { repo.ctbStop(it.stopId) }.getOrNull() }
            mergeAll(newStops, emptyList())
        }
        val cur = _index.value
        return rs.sortedBy { it.seq }.mapNotNull { s ->
            cur.stops.firstOrNull { it.op == Operator.CTB && it.id == s.stopId }?.let { StopWithSeq(it, s.seq) }
        }
    }

    private suspend fun stopsForGmb(rd: RouteDef): List<StopWithSeq> {
        val list = runCatching { repo.gmbRouteStops(rd.routeId, "1") }.getOrDefault(emptyList())
            .ifEmpty { runCatching { repo.gmbRouteStops(rd.routeId, "2") }.getOrDefault(emptyList()) }
        val withName = list.map { s ->
            if (s.name.isNotBlank()) s
            else runCatching { repo.gmbStop(s.id) }.getOrNull() ?: s
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
            val list = runCatching {
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
                            ?: return@runCatching emptyList()
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
