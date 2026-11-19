package hk.eta.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import hk.eta.app.R
import hk.eta.app.api.EtaModels.EtaItem
import hk.eta.app.api.EtaModels.StopOption
import hk.eta.app.api.AppUpdate
import hk.eta.app.api.CircularRoutes
import hk.eta.app.api.EtaRepository
import hk.eta.app.api.JointRoutes
import hk.eta.app.api.RouteDataStore
import hk.eta.app.api.RouteSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class UiState(
    val route: String = "40",
    val company: String = EtaRepository.AUTO,
    val stops: List<StopOption> = emptyList(),
    val etas: List<EtaItem> = emptyList(),
    // APP1933 home cards. When empty, MainActivity derives cards from `etas`.
    val cards: List<RouteCard> = emptyList(),
    val activeBound: String = "",     // "" = all directions
    val searchQuery: String = "",     // stop-list filter
    // APP1933 route-search screen (red bar + route/destination rows + keypad)
    val searchResults: List<RouteSearch.Result> = emptyList(),
    val searchMessage: String = "",
    val selection: StopOption? = null,
    val loading: Boolean = false,
    val message: String = "輸入路線號，載入沿途車站後點選車站即可看到實時到站時間。",
    val updatedAt: Long = 0L,
    val stopAudit: String = ""   // CIRCULAR_NAME_CHECK summary for the detail page
)

/** Kotlin layer: concurrency, 30-second polling and UI state. */
class EtaViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = EtaRepository()
    private val favorites = Favorites(app)
    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private var refreshJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        maybeAutoUpdateCatalogue()
        maybeAutoCheckAppUpdate()
    }

    /** Silent GitHub Release check; only caches a badge, never installs anything. */
    private fun maybeAutoCheckAppUpdate() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                if (!AppUpdatePrefs.isDue(app)) return@launch
                val repo = AppUpdatePrefs.repo(app)
                if (repo.isBlank()) return@launch   // keep "due" until the user fills it in
                val release = withContext(Dispatchers.IO) { AppUpdate.latest(repo) }
                val newer = AppUpdate.compareVersions(
                    AppUpdatePrefs.installedVersion(app), release.tag
                ) > 0
                AppUpdatePrefs.cacheResult(app, release.tag, newer)
                AppUpdatePrefs.markChecked(app)
            } catch (e: Exception) {
                // Silent: a failed version check must never disturb ETA queries.
            }
        }
    }

    /** Silent catalogue refresh according to the Settings screen preferences. */
    private fun maybeAutoUpdateCatalogue() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                if (RouteDataPrefs.isDue(app) && RouteDataPrefs.updateAllowed(app)) {
                    val report = withContext(Dispatchers.IO) { RouteDataStore.refresh(false) }
                    if (!report.busy && !report.skipped && report.okCount > 0) {
                        RouteDataPrefs.markChecked(app)
                    }
                }
            } catch (e: Exception) {
                // Silent: a failed background update must never disturb ETA queries.
            }
        }
    }

    companion object {
        /** Spinner positions must match R.array.company_labels. */
        fun companyAt(position: Int): String = when (position) {
            1 -> EtaRepository.KMB
            2 -> EtaRepository.CTB
            3 -> EtaRepository.NLB
            4 -> EtaRepository.GMB
            5 -> EtaRepository.MTR
            6 -> EtaRepository.LRT
            7 -> EtaRepository.MTRB
            else -> EtaRepository.AUTO
        }
    }

    /**
     * Nearby routes MUST NOT auto-locate before the user grants location
     * permission. Both the Activity and this ViewModel gate on the same
     * LocationHelper.hasPermission() check; this shows a clean waiting state
     * instead of silently falling back to Central.
     */
    fun awaitLocationPermission() {
        val app = getApplication<Application>()
        refreshJob?.cancel()
        patch {
            copy(
                loading = false,
                cards = emptyList(),
                stops = emptyList(),
                etas = emptyList(),
                selection = null,
                message = app.getString(R.string.location_waiting)
            )
        }
    }

    private fun locationGranted(): Boolean =
        LocationHelper.hasPermission(getApplication<Application>())

    fun favorites(): List<String> = favorites.all()

    fun addCurrentFavorite(): String? {
        val key = _state.value?.selection?.key() ?: return null
        favorites.add(key)
        return key
    }

    fun removeFavorite(key: String) {
        favorites.remove(key)
    }

    fun loadFavorite(key: String) {
        val parts = key.split("|")
        if (parts.size < 4) {
            patch { copy(message = "收藏資料格式不相容，請重新加入。") }
            return
        }
        val bound = if (parts.size >= 5) parts[4] else ""
        val selection = StopOption(parts[0], parts[1], bound, 0,
            parts[2], "", 1, parts[3], "")
        patch {
            copy(
                route = parts[1],
                company = parts[0],
                selection = selection,
                stops = emptyList(),
                etas = emptyList(),
                cards = emptyList(),
                activeBound = ""
            )
        }
        refresh()
        startAutoRefresh()
    }

    /**
     * APP1933 route search: every keystroke re-queries the official route
     * catalogues. Debounced so "1A" does not fire four separate downloads.
     */
    fun searchRoutes(input: String) {
        val q = input.trim()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(180L)
            // Empty input is NOT a blank screen: the search home PRELOADS
            // official catalogue rows so "12 往 海麗邨 ›" is visible on entry.
            val list = withContext(Dispatchers.IO) {
                if (q.isEmpty()) RouteSearch.preload() else RouteSearch.search(q)
            }
            patch {
                copy(
                    searchResults = list,
                    searchMessage = when {
                        list.isEmpty() && q.isEmpty() ->
                            getApplication<Application>().getString(R.string.search_preload_empty)
                        list.isEmpty() ->
                            getApplication<Application>().getString(R.string.search_no_result, q)
                        else -> ""
                    }
                )
            }
        }
    }

    /**
     * Load stops for one operator, or for all of them in AUTO mode.
     * onlyBound comes from a tapped search result ("往 尖沙咀碼頭" = "O").
     */
    fun loadStops(routeInput: String, company: String, onlyBound: String = "") {
        val route = routeInput.trim().uppercase(Locale.getDefault())
        if (route.isEmpty() && company != EtaRepository.LRT) {
            patch { copy(message = "請先輸入路線號；港鐵請填線路代碼（如 TML），輕鐵可留空。") }
            return
        }
        val effectiveRoute = if (company == EtaRepository.LRT && route.isEmpty()) {
            "ALL"
        } else {
            route
        }
        patch {
            copy(
                route = effectiveRoute,
                company = company,
                loading = true,
                message = "正在載入 $effectiveRoute 的車站／月台…",
                stops = emptyList(),
                etas = emptyList(),
                cards = emptyList(),
                activeBound = if (onlyBound == EtaRepository.NEARBY) "" else onlyBound,
                searchQuery = "",
                selection = null
            )
        }
        refreshJob?.cancel()

        viewModelScope.launch {
            try {
                val targets = when {
                    company == EtaRepository.AUTO -> EtaRepository.OPERATORS.toList()
                    // JOINT_KMB_CTB: a 城巴/九巴 route queries BOTH bus
                    // operators, but never drags in the other 5 modes.
                    JointRoutes.isJointCompany(company) ->
                        listOf(EtaRepository.KMB, EtaRepository.CTB)
                    else -> listOf(company)
                }
                val raw = withContext(Dispatchers.IO) {
                    val jobs = targets.map { c ->
                        safeStops { repo.stops(effectiveRoute, c) }
                    }
                    jobs.awaitAll().flatten().sortedWith(
                        compareBy<StopOption>({ it.company }, { it.bound }, { it.seq })
                    )
                }
                // CIRCULAR_ONLY: ONLY a circular line keeps EACH operator's
                // OWN stop sequence / wording. A Citybus circular line is
                // shown the Citybus way, a KMB circular line the App1933 way;
                // they are never merged into one KMB-named joint sequence.
                // Normal (non-circular) joint routes keep the previous rule.
                val circular = CircularRoutes.isCircularSequence(raw)
                        || CircularRoutes.anyCircularNote(raw)
                val result = if (!circular && JointRoutes.isJointCompany(company)) {
                    JointRoutes.preferKmbStops(raw)
                } else {
                    raw
                }
                // CIRCULAR_NAME_CHECK: verify displayed line stop names against
                // the local official name table (best-effort, never invented).
                val auditCompany = if (CircularRoutes.supports(company)) company
                        else if (result.isNotEmpty()) result[0].company else company
                val audit = if (circular) {
                    withContext(Dispatchers.IO) {
                        CircularRoutes.audit(
                            result, auditCompany,
                            CircularRoutes.officialNames(auditCompany)
                        ).summary()
                    }
                } else {
                    ""
                }
                patch {
                    copy(
                        loading = false,
                        stops = result,
                        message = if (result.isEmpty()) {
                            "找不到資料。巴士請填路線號；港鐵重鐵填線路代碼（TML／ISL…）；輕鐵可留空顯示全部站；港鐵巴士填 K12／506 等。"
                        } else {
                            "找到 ${result.size} 個車站／月台，點選查看實時到站時間。"
                        },
                        stopAudit = audit
                    )
                }
            } catch (e: Exception) {
                patch { copy(loading = false, message = "載入失敗：${e.message}") }
            }
        }
    }

    /** Locate the device, then list stops within the chosen radius. */
    fun loadNearby(radiusMeters: Int) {
        if (!locationGranted()) {
            awaitLocationPermission()
            return
        }
        patch {
            copy(
                loading = true,
                message = "正在定位並整理附近車站座標（首次需下載官方站點清單）…",
                stops = emptyList(),
                etas = emptyList(),
                cards = emptyList(),
                selection = null
            )
        }
        startAutoRefresh()
        viewModelScope.launch {
            try {
                val fix = LocationHelper.current(getApplication<Application>())
                val list = withContext(Dispatchers.IO) {
                    repo.nearbyStops(fix.lat, fix.lng, radiusMeters, 40)
                }
                patch {
                    copy(
                        loading = false,
                        stops = list,
                        message = if (list.isEmpty()) {
                            "${fix.source} · ${radiusMeters} m 內沒有可用的官方站點座標，可加大半徑或用路線模式。"
                        } else {
                            "${fix.source} · ${radiusMeters} m 內 ${list.size} 個站，點選查看該站全部路線 ETA。"
                        }
                    )
                }
            } catch (e: Exception) {
                patch { copy(loading = false, message = "附近車站載入失敗：${e.message}") }
            }
        }
    }

    /**
     * APP1933 home page: locate, take the nearest few stops, query their
     * ALL-ROUTE ETA in parallel and shape the result into route cards showing
     * the next three arrivals each. The Java data layer is untouched.
     *
     * HARD GATE: auto-location only happens AFTER location permission is
     * granted. Without consent we show a waiting state and make NO API call.
     */
    fun loadNearbyRoutes(radiusMeters: Int) {
        if (!locationGranted()) {
            awaitLocationPermission()
            return
        }
        patch {
            copy(
                loading = true,
                message = "正在定位並整理附近路線（會同時向官方 API 查到站時間）…",
                stops = emptyList(),
                etas = emptyList(),
                cards = emptyList(),
                selection = null
            )
        }
        startAutoRefresh()
        viewModelScope.launch {
            try {
                val fix = LocationHelper.current(getApplication<Application>())
                val stops = withContext(Dispatchers.IO) {
                    repo.nearbyStops(fix.lat, fix.lng, radiusMeters, 6)
                }
                val cards = fetchCards(stops)
                patch {
                    copy(
                        loading = false,
                        stops = stops,
                        cards = cards.sortedBy { it.rank },
                        updatedAt = System.currentTimeMillis(),
                        message = if (cards.isEmpty()) {
                            "${fix.source} · ${radiusMeters} m 內沒有可用班次，可加大半徑或到「搜尋」查路線。"
                        } else {
                            "${fix.source} · ${radiusMeters} m 內 ${cards.size} 條路線 · 每條顯示未來三班次"
                        }
                    )
                }
                EtaRefreshPrefs.markAutoRefreshed(getApplication())
            } catch (e: Exception) {
                patch { copy(loading = false, message = "附近路線載入失敗：${e.message}") }
            }
        }
    }

    /** Direct lookup when the user already knows the stop / station ID. */
    fun queryDirect(routeInput: String, stopInput: String, company: String) {
        val route = routeInput.trim().uppercase(Locale.getDefault())
        val stopId = stopInput.trim()
        if (stopId.isEmpty()) {
            patch { copy(message = "請輸入站號／車站代碼。") }
            return
        }
        viewModelScope.launch {
            try {
                val selection = withContext(Dispatchers.IO) {
                    repo.directSelection(route, stopId, company)
                }
                patch {
                    copy(
                        route = route,
                        company = company,
                        selection = selection,
                        stops = emptyList(),
                        etas = emptyList(),
                        cards = emptyList(),
                        activeBound = ""
                    )
                }
                refresh()
                startAutoRefresh()
            } catch (e: Exception) {
                patch { copy(message = "查站失敗：${e.message}") }
            }
        }
    }

    fun selectStop(stop: StopOption) {
        patch { copy(selection = stop, etas = emptyList(), cards = emptyList()) }
        refresh()
        startAutoRefresh()
    }

    /** APP1933 "轉換路線方向": cycle through the bounds present in the stop list. */
    fun toggleDirection() {
        val stops = _state.value?.stops ?: return
        val bounds = stops.map { it.bound }
            .filter { it.isNotEmpty() && it != EtaRepository.NEARBY }
            .distinct()
        if (bounds.size < 2) {
            patch { copy(message = "此結果只有單一方向，無需切換。") }
            return
        }
        val current = _state.value?.activeBound ?: ""
        val index = bounds.indexOf(current)
        val next = bounds[(index + 1 + bounds.size) % bounds.size]
        patch { copy(activeBound = next, message = "已切換方向：$next") }
    }

    /** Live filter of the route stop sequence (no API call). */
    fun filterStops(query: String) {
        patch { copy(searchQuery = query) }
    }

    fun refresh() {
        val selection = _state.value?.selection
        if (selection == null) {
            patch { copy(message = "請先載入並點選車站，或輸入站號後按「查 ETA」。") }
            return
        }
        viewModelScope.launch {
            patch { copy(loading = true, message = "正在更新到站時間…") }
            try {
                val list = withContext(Dispatchers.IO) { repo.etaFor(selection) }.sortedBy { it.etaMillis }
                val label = selection.stopName.ifEmpty { selection.stopId }
                patch {
                    copy(
                        loading = false,
                        etas = list,
                        updatedAt = System.currentTimeMillis(),
                        message = if (list.isEmpty()) {
                            "$label 暫無到站班次（可能非服務時間、臨時改道或 API 暫停）。"
                        } else {
                            "${selection.route} → $label · 已更新 ${list.size} 班"
                        }
                    )
                }
                EtaRefreshPrefs.markAutoRefreshed(getApplication())
            } catch (e: Exception) {
                patch { copy(loading = false, message = "更新失敗：${e.message}") }
            }
        }
    }

    /**
     * AUTO_REFRESH_ETA — one foreground loop for EVERY visible ETA surface:
     *   - a selected stop / favourite / route-detail boarding stop -> refresh()
     *   - Home nearby-route cards -> re-query the CACHED stop list's ETA
     *     (no re-location, no re-download of the stop catalogue)
     *   - nothing loaded or location not granted -> no API call at all
     * The interval and on/off switch come from 應用程式設置, never hard-coded.
     */
    private fun startAutoRefresh() {
        refreshJob?.cancel()
        val interval = EtaRefreshPrefs.intervalMs(getApplication())
        if (interval <= 0L) return
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(interval)
                refreshVisibleEta()
            }
        }
    }

    private fun refreshVisibleEta() {
        val state = _state.value ?: return
        when {
            state.selection != null -> refresh()
            state.cards.isNotEmpty() && state.stops.isNotEmpty() ->
                refreshNearbyCards(state.stops)
            else -> return   // 未授權／未載入：唔打 API
        }
    }

    /** Re-query ETA for the SAME cached stops; does NOT call LocationHelper. */
    private fun refreshNearbyCards(stops: List<StopOption>) {
        viewModelScope.launch {
            try {
                val cards = fetchCards(stops)
                val app = getApplication<Application>()
                patch {
                    copy(
                        cards = cards.sortedBy { it.rank },
                        updatedAt = System.currentTimeMillis(),
                        message = (if (cards.isEmpty()) {
                            "暫無可用班次"
                        } else {
                            "${cards.size} 條路線 · 每條顯示未來三班次"
                        }) + app.getString(R.string.eta_refresh_nearby_tag)
                    )
                }
                EtaRefreshPrefs.markAutoRefreshed(app)
            } catch (e: Exception) {
                patch { copy(message = "ETA 自動更新失敗：${e.message}") }
            }
        }
    }

    /** Called from Activity.onResume(): re-read settings and restart the loop. */
    fun onForeground() {
        startAutoRefresh()
        if (EtaRefreshPrefs.dueForForegroundRefresh(getApplication())) {
            refreshVisibleEta()
        }
    }

    /** Called from Activity.onPause(): no background polling, ever. */
    fun onBackground() {
        refreshJob?.cancel()
    }

    /** Shared by the initial nearby load and every automatic ETA refresh. */
    private suspend fun fetchCards(stops: List<StopOption>): List<RouteCard> =
        coroutineScope {
            val pairs = stops.take(6).mapIndexed { index, s ->
                safeEta(s, index) { repo.etaFor(s) }
            }.awaitAll()
            // JOINT_KMB_CTB_MERGE: GeoStops 對同一個實體附近站會各出一個
            // KMB / CTB StopOption（站號命名空間唔同）。先按中文站名併埋
            // 兩份 ETA 再一次過交畀 cardsFrom，主頁卡片先至會顯示
            // 「城巴/九巴」並逐班標「該班次：城巴／九巴」。
            // 配唔到名就各自成組，原有行為完全唔變。
            val buckets = LinkedHashMap<String, MutableList<Int>>()
            val circularFlags = LinkedHashMap<String, Boolean>()
            for (i in pairs.indices) {
                val name = pairs[i].first.stopName.trim().ifEmpty {
                    pairs[i].first.stopId
                }
                // CIRCULAR_ONLY: a circular route keeps its OWN operator's
                // card, so KMB and Citybus circular rows never share a bucket.
                val circular = pairs[i].second.any {
                    CircularRoutes.isCircularDest(it.destTc)
                }
                val key = (if (circular) pairs[i].first.company + "|" else "") + name
                buckets.getOrPut(key) { ArrayList() }.add(i)
                circularFlags[key] = circular
            }
            val cards = ArrayList<RouteCard>()
            for ((bucketKey, indices) in buckets) {
                // JOINT_KMB_NAME: keep the KMB StopOption as the fallback
                // (distance note / rank). Per-operator labels are resolved
                // below by CTB_STOP_NAME so Citybus cards use Citybus names.
                val preferred = indices.firstOrNull {
                    pairs[it].first.company == EtaRepository.KMB
                } ?: indices.first()
                val first = pairs[preferred]
                val s = first.first
                val merged = indices.flatMap { pairs[it].second }
                // CTB_STOP_NAME: one merged bucket can contain BOTH a KMB and
                // a CTB StopOption for the same physical stop. Remember each
                // operator's OWN official name / stop id, so a pure Citybus
                // card shows 城巴站名 + 城巴站號 instead of inheriting the
                // KMB wording. JOINT keeps the KMB label (previous request).
                val displays = LinkedHashMap<String, StopDisplay>()
                for (i in indices) {
                    val own = pairs[i].first
                    displays[own.company] = StopDisplay(
                        own.stopName, own.stopId
                    )
                }
                val kmbDisplay = displays[EtaRepository.KMB]
                if (kmbDisplay != null && !displays.containsKey(JointRoutes.JOINT)) {
                    displays[JointRoutes.JOINT] = kmbDisplay
                }
                cards.addAll(
                    App1933Format.cardsFrom(
                        merged, s.stopName, s.stopId, s.note, first.third,
                        displays, circularFlags[bucketKey] == true
                    )
                )
            }
            cards
        }

    private fun patch(block: UiState.() -> UiState) {
        val current = _state.value ?: UiState()
        _state.value = current.block()
    }

    override fun onCleared() {
        onBackground()
        searchJob?.cancel()
        super.onCleared()
    }
}

private fun CoroutineScope.safeStops(
    block: () -> List<StopOption>
): Deferred<List<StopOption>> = async(Dispatchers.IO) {
    try {
        block()
    } catch (e: Exception) {
        emptyList()
    }
}

/** One nearby stop -> (stop, all-route ETA, distance rank). */
private fun CoroutineScope.safeEta(
    stop: StopOption,
    rank: Int,
    block: () -> List<EtaItem>
): Deferred<Triple<StopOption, List<EtaItem>, Int>> = async(Dispatchers.IO) {
    try {
        Triple(stop, block(), rank)
    } catch (e: Exception) {
        Triple(stop, emptyList<EtaItem>(), rank)
    }
}
