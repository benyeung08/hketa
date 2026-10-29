package com.hketa.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

object Urls {
    private const val KMB = "https://data.etabus.gov.hk/v1/transport/kmb"
    private const val CTB = "https://rt.data.gov.hk/v2/transport/citybus"
    private const val NLB = "https://rt.data.gov.hk/v2/transport/nlb"
    private const val GMB = "https://data.etagmb.gov.hk"
    private const val MTR = "https://rt.data.gov.hk/v1/transport/mtr/bus"

    fun kmbRoutes() = "$KMB/route/"
    fun kmbRouteStops() = "$KMB/route-stop/"
    fun kmbStops() = "$KMB/stop/"
    fun kmbEta(stopId: String) = "$KMB/stop-eta/${enc(stopId)}"

    fun ctbRoutes() = "$CTB/route/CTB"
    fun ctbRouteStops(route: String, bound: String) =
        "$CTB/route-stop/CTB/${enc(route)}/${if (bound == "I") "inbound" else "outbound"}"

    fun ctbStop(stopId: String) = "$CTB/stop/${enc(stopId)}"
    fun ctbEta(stopId: String, route: String) = "$CTB/eta/CTB/${enc(stopId)}/${enc(route)}"

    fun nlbRoutes() = "$NLB/route.php?action=list"
    fun nlbStops(routeId: String) = "$NLB/stop.php?action=list&routeId=${enc(routeId)}"
    fun nlbEta(routeId: String, stopId: String) =
        "$NLB/stop.php?action=estimatedArrivals&routeId=${enc(routeId)}&stopId=${enc(stopId)}"

    fun gmbRoutes(region: String) = "$GMB/route/${enc(region)}"
    /**
     * 路線詳情（**攞 route_id 嘅唯一途徑**）。
     *
     * Route Listing API `GET /route/{region}` 按官方規格淨係返
     * `data.routes: string[]`（一串路線代碼），**冇 route_id**；
     * 而要查站序（`/route-stop/{route_id}/{seq}`）同到站預報就一定要 route_id。
     * data.gov.hk 官方資源頁寫明呢個就係「專線小巴路線資料」嘅端點。
     */
    fun gmbRouteDetail(region: String, routeCode: String) =
        "$GMB/route/${enc(region)}/${enc(routeCode)}"
    fun gmbRouteStops(routeId: String, seq: String) = "$GMB/route-stop/${enc(routeId)}/${enc(seq)}"
    fun gmbStop(stopId: String) = "$GMB/stop/${enc(stopId)}"
    fun gmbEta(stopId: String, routeId: String) =
        "$GMB/stop-eta/${enc(stopId)}" + if (routeId.isBlank()) "" else "/${enc(routeId)}"

    fun mtrBusSchedule() = "$MTR/getSchedule"

    /** 港鐵重鐵：line = AEL/TML/TKL… ，sta = 三字母站碼 */
    fun mtrHeavyRail(line: String, sta: String) =
        "https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line=${enc(line)}&sta=${enc(sta)}&lang=TC"

    /**
     * 輕鐵：station_id 為數字。
     *
     * 官方**淨得 v1 路徑**，v2 並唔存在 —— 以前會兩個都試，
     * 深夜冇車嗰陣最多白打 3 個請求。而家只行 v1。
     */
    fun lightRailV1(stationId: String) =
        "https://rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule?station_id=${enc(stationId)}&with_special=1"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

class EtaRepository {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * 英文名：官方 → 內置對照（嶼巴）→ **GitHub 聚合檔**。
     *
     * 九巴／城巴／小巴官方接口本身有 `name_en`，直接用；
     * 嶼巴官方**冇**英文欄位 → 先用內置嘅 154 條對照，
     * 對唔到再落 [HkbusIndex]（hkbus 聚合檔每個站都有 `name.en`）。
     *
     * 三層都冇就返 `""` —— 顯示嗰層會維持中文，**唔會**亂砌英文名。
     */
    private fun enOf(id: String, zh: String, official: String, op: Operator): String {
        if (official.isNotBlank()) return official
        if (op == Operator.NLB) NlbStopNames.englishOf(zh)?.let { return it }
        return HkbusIndex.enOf(zh, id)
    }

    // ================= 九巴 KMB =================

    suspend fun kmbRoutes(): List<RouteDef> =
        parseList<KmbRoute>(Http.get(Urls.kmbRoutes())).map {
            RouteDef(
                op = Operator.KMB,
                route = it.route,
                bound = it.bound,
                serviceType = it.service_type,
                orig = it.orig_tc,
                dest = it.dest_tc
            )
        }

    suspend fun kmbRouteStops(): List<RouteStopDef> =
        parseList<KmbRouteStop>(Http.get(Urls.kmbRouteStops())).map {
            RouteStopDef(
                op = Operator.KMB,
                route = it.route,
                bound = it.bound,
                serviceType = it.service_type,
                seq = it.seq.toIntOrNull() ?: 0,
                stopId = it.stop
            )
        }

    suspend fun kmbStops(): List<StopDef> =
        parseList<KmbStop>(Http.get(Urls.kmbStops())).map {
            StopDef(
                op = Operator.KMB,
                id = it.stop,
                name = it.name_tc,
                nameEn = enOf(it.stop, it.name_tc, it.name_en, Operator.KMB),
                lat = it.lat.toDoubleOrNull() ?: 0.0,
                lon = it.long.toDoubleOrNull() ?: 0.0
            )
        }

    suspend fun kmbEta(stopId: String): List<EtaEntry> =
        parseList<KmbEta>(Http.get(Urls.kmbEta(stopId))).map {
            EtaEntry(
                op = Operator.KMB,
                route = it.route,
                dest = it.dest_tc,
                minutes = TimeUtil.etaMinutes(it.eta),
                clock = TimeUtil.etaClock(it.eta),
                remark = it.rmk_tc
            )
        }

    // ================= 城巴 CTB =================

    suspend fun ctbRoutes(): List<RouteDef> =
        parseList<CtbRoute>(Http.get(Urls.ctbRoutes())).map {
            RouteDef(
                op = Operator.CTB,
                route = it.route,
                bound = it.bound,
                orig = it.orig_tc,
                dest = it.dest_tc
            )
        }

    suspend fun ctbRouteStops(route: String, bound: String): List<RouteStopDef> =
        parseList<CtbRouteStop>(Http.get(Urls.ctbRouteStops(route, bound))).map {
            RouteStopDef(
                op = Operator.CTB,
                route = route,
                bound = bound,
                seq = it.seq.toIntOrNull() ?: 0,
                stopId = it.stop
            )
        }

    suspend fun ctbStop(stopId: String): StopDef? =
        parseList<CtbStop>(Http.get(Urls.ctbStop(stopId))).firstOrNull()?.let {
            StopDef(
                op = Operator.CTB,
                id = it.stop,
                name = it.name_tc,
                nameEn = enOf(it.stop, it.name_tc, it.name_en, Operator.CTB),
                lat = it.lat.toDoubleOrNull() ?: 0.0,
                lon = it.long.toDoubleOrNull() ?: 0.0
            )
        }

    suspend fun ctbEta(stopId: String, route: String): List<EtaEntry> =
        parseList<CtbEta>(Http.get(Urls.ctbEta(stopId, route))).map {
            EtaEntry(
                op = Operator.CTB,
                route = it.route,
                dest = it.dest_tc,
                minutes = TimeUtil.etaMinutes(it.eta),
                clock = TimeUtil.etaClock(it.eta),
                remark = it.rmk_tc
            )
        }

    // ================= 新大嶼山巴士 NLB（寬鬆手動解析） =================

    suspend fun nlbRoutes(): List<RouteDef> {
        val root = rootObj(Http.get(Urls.nlbRoutes()))
        val arr = firstArray(root, "routes", "data")
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = pick(o, "routeId", "route_id", "id")
            val no = pick(o, "routeNo", "route_no", "routeCode", "route_code")
            if (id.isBlank() || no.isBlank()) return@mapNotNull null
            RouteDef(
                op = Operator.NLB,
                route = no,
                orig = pick(o, "routeName_cn", "routeName", "description_tc"),
                dest = pick(o, "destination_cn", "destination", "dest_tc"),
                routeId = id
            )
        }
    }

    suspend fun nlbStops(routeId: String): List<StopDef> {
        val root = rootObj(Http.get(Urls.nlbStops(routeId)))
        val arr = firstArray(root, "stops", "stopList", "data")
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = pick(o, "stopId", "stop_id", "id")
            if (id.isBlank()) return@mapNotNull null
            val zhName = pick(o, "stopName_cn", "stopName", "name_tc", "name")
            StopDef(
                op = Operator.NLB,
                id = id,
                name = zhName,
                // 官方 NLB 接口冇 name_en，所以喺內置對照表度查。
                // 查唔到會返 null → 維持中文原名（唔會亂砌英文名）。
                nameEn = enOf(id, zhName, "", Operator.NLB),
                lat = num(pick(o, "latitude", "lat")),
                lon = num(pick(o, "longitude", "long", "lon", "lng"))
            )
        }
    }

    suspend fun nlbEta(routeId: String, stopId: String): List<EtaEntry> {
        val root = rootObj(Http.get(Urls.nlbEta(routeId, stopId)))
        val arr = firstArray(root, "estimatedArrivals", "arrivals", "eta", "data")
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val raw = pick(o, "eta", "etaTime", "estimatedArrivalTime", "time")
            if (raw.isBlank()) return@mapNotNull null
            EtaEntry(
                op = Operator.NLB,
                route = pick(o, "routeNo", "route_no", "route"),
                dest = pick(o, "destination_cn", "destination", "dest_tc", "routeName_cn"),
                minutes = TimeUtil.etaMinutes(raw),
                clock = TimeUtil.etaClock(raw),
                remark = pick(o, "remark_cn", "remark", "rmk_tc")
            )
        }
    }

    // ================= 專線小巴 GMB（寬鬆手動解析） =================

    /**
     * 專線小巴路線列表。
     *
     * ★ 以前呢度**永遠返 0 條**（所以設定頁「專線小巴」一路顯示 0 條路線／0 個車站）。
     *   原因：官方規格寫明 `GET /route/{region}` 嘅 `data.routes` 係
     *   **`string[]`（一串路線代碼）**，例如 `["1", "10", "101M", …]`；
     *   而舊寫法淨係認 `JsonObject`（`e as? JsonObject ?: return null`），
     *   字串全部被丟棄 → 結果一條都冇。
     *
     *   而家兩樣都食：物件陣列（拎埋 route_id）同字串陣列（淨係有路線代碼，
     *   route_id 之後由 [gmbRouteDetail] 補）。
     */
    suspend fun gmbRoutes(region: String): List<RouteDef> {
        val root = rootObj(Http.get(Urls.gmbRoutes(region)))
        val data = root["data"] as? JsonObject ?: root
        return gmbRouteListOf(data, region)
    }

    /** 由 `data` 入面搵路線列表，兼容 `string[]` 同物件陣列兩種寫法 */
    private fun gmbRouteListOf(data: JsonObject, region: String): List<RouteDef> {
        // 同一條路線代碼可能出現幾次 → 用 code 做 key 去重，後者覆蓋前者
        val out = LinkedHashMap<String, RouteDef>()

        fun take(arr: List<JsonElement>) {
            arr.forEach { e ->
                val o = e as? JsonObject
                if (o != null) {
                    val id = pick(o, "route_id", "routeId", "id")
                    val code = pick(o, "route_code", "routeCode", "route_no", "routeNo")
                    if (code.isBlank()) return@forEach
                    out[code] = RouteDef(
                        op = Operator.GMB,
                        route = code,
                        orig = pick(o, "description_tc", "description", "orig_tc"),
                        dest = pick(o, "destination_tc", "dest_tc", "destination"),
                        routeId = id
                    )
                } else {
                    // 官方規格嘅寫法：淨係一個路線代碼字串
                    val code = txt(e).trim()
                    if (code.isBlank() || code == "null") return@forEach
                    // routeId 留空 —— 之後由 gmbRouteDetail 逐條補
                    out.getOrPut(code) { RouteDef(op = Operator.GMB, route = code) }
                }
            }
        }

        when (val routes = data["routes"]) {
            is kotlinx.serialization.json.JsonArray -> take(routes.jsonArray)
            is JsonObject -> {
                // 唔帶 region 嗰陣會係 { HKI: [...], KLN: [...], NT: [...] }
                if (region.isNotBlank()) {
                    (routes.jsonObject[region] as? kotlinx.serialization.json.JsonArray)
                        ?.let { take(it.jsonArray) }
                }
                if (out.isEmpty()) {
                    routes.jsonObject.values.forEach { v ->
                        (v as? kotlinx.serialization.json.JsonArray)?.let { take(it.jsonArray) }
                    }
                }
            }
            else -> Unit
        }
        // 兜底：data 本身可能就係陣列，或者塞喺 data.data
        if (out.isEmpty()) {
            (data["data"] as? kotlinx.serialization.json.JsonArray)?.let { take(it.jsonArray) }
        }
        if (out.isEmpty()) {
            (data["route_list"] as? kotlinx.serialization.json.JsonArray)?.let { take(it.jsonArray) }
        }
        return out.values.toList()
    }

    /**
     * 補一條小巴路線嘅 route_id（同描述）。
     *
     * 官方 `GET /route/{region}/{route_code}` 返嘅 `data` 係**陣列**
     * （同一條路線代碼可以有幾個 service type／特別班次），
     * 第一個就係正常班次；搵唔到就返 null（唔會當錯誤）。
     */
    suspend fun gmbRouteDetail(region: String, routeCode: String): RouteDef? {
        if (region.isBlank() || routeCode.isBlank()) return null
        val root = rootObj(Http.get(Urls.gmbRouteDetail(region, routeCode)))
        val data = root["data"]
        val arr = when (data) {
            is kotlinx.serialization.json.JsonArray -> data.jsonArray
            is JsonObject -> (data.jsonObject["routes"] as? kotlinx.serialization.json.JsonArray)
                ?.jsonArray
                ?: firstArray(data.jsonObject, "routes", "route_list", "data")
            else -> emptyList()
        }
        val o = arr.firstOrNull() as? JsonObject ?: (data as? JsonObject) ?: return null
        val id = pick(o, "route_id", "routeId", "id")
        val code = pick(o, "route_code", "routeCode", "route_no", "routeNo").ifBlank { routeCode }
        if (id.isBlank() || code.isBlank()) return null
        return RouteDef(
            op = Operator.GMB,
            route = code,
            orig = pick(o, "description_tc", "description", "orig_tc"),
            dest = pick(o, "destination_tc", "dest_tc", "destination"),
            routeId = id
        )
    }

    suspend fun gmbRouteStops(routeId: String, seq: String): List<StopDef> {
        val root = rootObj(Http.get(Urls.gmbRouteStops(routeId, seq)))
        val data = root["data"] as? JsonObject ?: root
        val arr = firstArray(data, "route_stops", "routeStops", "stops", "data")
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = pick(o, "stop_id", "stopId", "id")
            if (id.isBlank()) return@mapNotNull null
            StopDef(
                op = Operator.GMB,
                id = id,
                name = pick(o, "name_tc", "stopName", "name", "description_tc"),
                nameEn = enOf(
                    id,
                    pick(o, "name_tc", "stopName", "name", "description_tc"),
                    pick(o, "name_en", "stopNameEn", "description_en"),
                    Operator.GMB
                ),
                lat = num(pick(o, "latitude", "lat")),
                lon = num(pick(o, "longitude", "long", "lon", "lng"))
            )
        }
    }

    suspend fun gmbStop(stopId: String): StopDef? {
        val root = rootObj(Http.get(Urls.gmbStop(stopId)))
        val data = root["data"] as? JsonObject ?: root
        val arr = firstArray(data, "stops", "stop_list", "data")
        val o = arr.firstOrNull() as? JsonObject ?: return null
        return StopDef(
            op = Operator.GMB,
            id = stopId,
            name = pick(o, "name_tc", "stopName", "name", "description_tc"),
            nameEn = enOf(
                stopId,
                pick(o, "name_tc", "stopName", "name", "description_tc"),
                pick(o, "name_en", "stopNameEn", "description_en"),
                Operator.GMB
            ),
            lat = num(pick(o, "latitude", "lat")),
            lon = num(pick(o, "longitude", "long", "lon", "lng"))
        )
    }

    suspend fun gmbEta(stopId: String, routeId: String, routeNo: String = ""): List<EtaEntry> {
        val primary = gmbEtaOnce(stopId, routeId)
        if (primary.isNotEmpty() || routeId.isBlank()) return primary

        // ★ GitHub 聚合檔嘅 route_id 同源唔同官方（官方要 etagmb 自己嘅 id），
        //   所以帶住 route_id 查會查唔到。呢度**唔帶** route_id 再撈一次，
        //   撈返晒呢個站所有線，再本地按路線號篩 —— 唔使知官方 id 係乜。
        val all = gmbEtaOnce(stopId, "")
        if (all.isEmpty() || routeNo.isBlank()) return emptyList()
        val hit = all.filter { it.route.equals(routeNo, ignoreCase = true) }
        return hit.ifEmpty { emptyList() }
    }

    private suspend fun gmbEtaOnce(stopId: String, routeId: String): List<EtaEntry> {
        val root = rootObj(Http.get(Urls.gmbEta(stopId, routeId)))
        val data = root["data"] as? JsonObject ?: root
        val arr = firstArray(data, "stop_etas", "stopEta", "etas", "eta", "data")
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val raw = pick(o, "eta", "eta_time", "estimatedArrivalTime", "arrivalTime", "time")
            if (raw.isBlank()) return@mapNotNull null
            EtaEntry(
                op = Operator.GMB,
                route = pick(o, "route_code", "routeCode", "route_no", "routeId"),
                dest = pick(o, "dest_tc", "destination_tc", "description_tc", "destination"),
                minutes = TimeUtil.etaMinutes(raw),
                clock = TimeUtil.etaClock(raw),
                remark = pick(o, "remark_tc", "remark", "rmk_tc")
            )
        }
    }

    // ================= 港鐵巴士 MTR Bus =================

    suspend fun mtrBusStops(routeName: String): List<StopDef> =
        mtrBus(routeName).first

    /**
     * 港鐵巴士（K51／K54 呢啲）班次。
     *
     * 官方接口有四個地方同我哋原先寫嘅**完全唔夾**，所以以前永遠係 0 個站 0 班車：
     *
     * 1. **method 係 POST，body 係 JSON**（`Content-Type: application/json`），
     *    唔係 form-urlencoded —— 官方規格嘅 example request 係
     *    `{"language":"zh","routeName":"K12"}`
     * 2. **`language` 淨係接受 `en` / `zh`** —— 官方寫明「Valid result will be
     *    returned only if both parameter 1(language) and parameter 2(routeName) is
     *    correct」，寫 `zh-Hant` 係無效值
     * 3. 頂層字段叫 **`busStop`**（單數），**冇 `data` 包住** ——
     *    而唔係 `busStops` / `stops`
     * 4. 站 ID 叫 **`busStopId`**；到站時間喺 **每班車 `bus[]` 入面**
     *    （`departureTimeInSecond` / `departureTimeText`），**唔喺站嗰層**
     *
     * 另一個官方限制：**響應入面冇車站名**（試過官方 data dictionary，
     * `busStop` 物件得 `busStopId` / `busStopRemark` / `isSuspended`）。
     * 所以站名要由 [HkbusIndex] 補；補唔到就用返 `busStopId` 頂住 ——
     * **唔可以**因為冇名就成個站掉咗（以前嗰句 `if (name.isNotBlank())` 就係咁樣
     * 令到全部站消失）。
     */
    suspend fun mtrBus(routeName: String): Pair<List<StopDef>, List<EtaEntry>> {
        val text = Http.postJson(
            Urls.mtrBusSchedule(),
            listOf("language" to "zh", "routeName" to routeName)
        )
        val root = rootObj(text)
        val arr = firstArray(root, "busStop", "busStops", "bus_stop", "bus_stops", "stops")
        val stops = mutableListOf<StopDef>()
        val etas = mutableListOf<EtaEntry>()
        arr.forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            val id = pick(o, "busStopId", "stopId", "stop_id", "id", "code")
            if (id.isBlank()) return@forEach
            // 官方冇站名 → 試吓聚合檔；再冇就用站 ID 頂住
            val name = pick(o, "stopNameTc", "stopName", "nameTc", "name_tc", "name")
                .ifBlank { HkbusIndex.zhOf(id) }
                .ifBlank { id }
            stops.add(
                StopDef(
                    op = Operator.MTR_BUS,
                    id = id,
                    name = name,
                    lat = num(pick(o, "latitude", "lat")),
                    lon = num(pick(o, "longitude", "long", "lon", "lng"))
                )
            )
            // 到站時間喺 bus[] 入面，一站可以有多班車
            val buses = firstArray(o, "bus", "buses", "busList")
            buses.forEach { b ->
                val bo = b as? JsonObject ?: return@forEach
                val (minutes, clock) = mtrBusEta(
                    pick(bo, "departureTimeInSecond", "arrivalTimeInSecond"),
                    pick(bo, "departureTimeText", "arrivalTimeText")
                )
                if (minutes == null) return@forEach
                etas.add(
                    EtaEntry(
                        op = Operator.MTR_BUS,
                        route = routeName,
                        dest = pick(o, "destTc", "destinationTc", "destination_tc", "dest_tc"),
                        minutes = minutes,
                        clock = clock,
                        remark = pick(bo, "busRemark", "remark", "remarkTc")
                            .ifBlank { pick(o, "busStopRemark", "remarkTc") }
                    )
                )
            }
        }
        return stops to etas
    }

    /**
     * 港鐵巴士嘅「秒數 + 文字」→ 分鐘。
     *
     * 兩個坑：
     *
     * - `arrivalTimeInSecond` 喺官方 sample 係 `"108000"`（30 個鐘）——
     *   呢個係「冇預報」嘅哨兵值，直接當分鐘數會出一個荒謬嘅數字。
     *   所以**淨睇 `departureTimeInSecond`**，而且要喺合理範圍內。
     * - 文字 `"Departing / Departed"` / `"即將開出"` 代表**就快開出**，
     *   唔係「仲有 N 分鐘」，要當 0 分鐘而唔係當解析失敗掉咗佢。
     */
    private fun mtrBusEta(secRaw: String, textRaw: String): Pair<Int?, String> {
        val sec = secRaw.trim().toLongOrNull()
        if (sec != null && sec in 0..MAX_MTR_BUS_SEC) {
            val minutes = (sec / 60L).toInt()
            val clock = runCatching {
                java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Hong_Kong"))
                    .plusSeconds(sec)
                    .toLocalTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            }.getOrDefault("")
            return minutes to clock
        }
        val t = textRaw.trim()
        if (t.isBlank()) return null to ""
        // 「即將開出 / 已離開」→ 0 分鐘
        if (t.contains("Depart", true) || t.contains("Arriving", true) ||
            t.contains("即將", true) || t.contains("已離開", true)
        ) return 0 to ""
        val m = Regex("(\\d+)").find(t)?.value?.toIntOrNull() ?: return null to ""
        return m to ""
    }

    /** 港鐵巴士預報秒數嘅合理上限；超過就當係「冇預報」嘅哨兵值 */
    private val MAX_MTR_BUS_SEC = 3 * 60 * 60L

    // ================= 港鐵重鐵 MTR Heavy Rail =================

    /** 回傳：到站列表 + 可能的服務狀態訊息（null 代表正常） */
    suspend fun mtrHeavyRailEta(
        line: String,
        sta: String,
        t: EtaText = EtaText(),
        nameOf: (String) -> String = { it }
    ): Pair<List<EtaEntry>, String?> {
        val text = Http.get(Urls.mtrHeavyRail(line, sta))
        val root = rootObj(text)

        val alert = runCatching {
            val s = txt(root["status"])
            val m = pick(root, "message", "messageTc", "message_tc")
            if (s == "0" && m.isNotBlank()) m else null
        }.getOrNull()
        val delayed = pick(root, "Isdelay", "isdelay") == "Y"

        val data = root["data"] as? JsonObject ?: return emptyList<EtaEntry>() to alert
        val key = data.keys.firstOrNull { it.equals("$line-$sta", true) } ?: data.keys.firstOrNull()
            ?: return emptyList<EtaEntry>() to alert
        val node = data[key] as? JsonObject ?: return emptyList<EtaEntry>() to alert

        val out = mutableListOf<EtaEntry>()
        for (dir in listOf("UP", "DOWN")) {
            val arr = runCatching { (node[dir] as? kotlinx.serialization.json.JsonArray)?.jsonArray }
                .getOrNull() ?: continue
            arr.forEach { e ->
                val o = e as? JsonObject ?: return@forEach
                if (pick(o, "valid") == "N") return@forEach
                val ttnt = pick(o, "ttnt").toIntOrNull()
                val minutes = ttnt ?: TimeUtil.etaMinutes(pick(o, "time"))
                val destCode = pick(o, "dest")
                val plat = pick(o, "plat")
                val remark = t.join(
                    buildList {
                        add(t.platformText(plat))
                        if (delayed) add(t.delayed)
                        if (pick(o, "route").equals("RAC", true)) add(t.viaRacecourse)
                        val tt = pick(o, "timetype")
                        if (tt.equals("A", true)) add(t.arriving)
                        else if (tt.equals("D", true)) add(t.departing)
                    }
                )
                out.add(
                    EtaEntry(
                        op = Operator.MTR_HR,
                        route = line,
                        dest = if (destCode.isBlank()) "" else nameOf(destCode),
                        minutes = minutes,
                        clock = TimeUtil.etaClock(pick(o, "time")),
                        remark = remark
                    )
                )
            }
        }
        return out to alert
    }

    // ================= 輕鐵 Light Rail =================

    suspend fun lightRailEta(stationId: String, t: EtaText = EtaText()): List<EtaEntry> {
        val padded = stationId.padStart(3, '0')
        // 官方規格（Light Rail Next Train API v1.1）淨得 v1 路徑，
        // v2 係唔存在嘅 —— 以前試埋 v2 會令每個站白打一倍請求
        val candidates = listOf(stationId, padded).distinct().map { Urls.lightRailV1(it) }

        for (url in candidates) {
            val text = suspendCatching { Http.get(url) }.getOrNull() ?: continue
            val parsed = parseLightRail(text, t)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun parseLightRail(text: String, t: EtaText): List<EtaEntry> {
        val root = rootObj(text)
        val platforms = firstArray(root, "platform_list", "platformList", "data")
        val out = mutableListOf<EtaEntry>()
        for (p in platforms) {
            val po = p as? JsonObject ?: continue
            val platformId = pick(po, "platform_id", "platformId", "id")
            val routes = runCatching {
                (po["route_list"] as? kotlinx.serialization.json.JsonArray)?.jsonArray
            }.getOrNull() ?: runCatching {
                (po["routeList"] as? kotlinx.serialization.json.JsonArray)?.jsonArray
            }.getOrNull() ?: continue

            routes.forEach { e ->
                val o = e as? JsonObject ?: return@forEach
                val routeNo = pick(o, "route_no", "routeNo")
                val destCh = pick(o, "dest_ch", "destCh", "dest_tc")
                val timeCh = pick(o, "time_ch", "timeCh", "time_tc")
                val minutes = Regex("(\\d+)").find(timeCh)?.value?.toIntOrNull()
                val remark = t.join(
                    buildList {
                        add(t.platformText(platformId))
                        if (pick(o, "special") == "1") add(t.special)
                        add(pick(o, "additionalInfo1", "additionalInfo"))
                        add(t.carsText(pick(o, "train_length", "trainLength")))
                    }
                )
                out.add(
                    EtaEntry(
                        op = Operator.LRT,
                        route = routeNo,
                        dest = destCh,
                        minutes = minutes,
                        clock = "",
                        remark = remark
                    )
                )
            }
        }
        return out
    }

    // ================= 工具 =================

    private inline fun <reified T> parseList(text: String): List<T> =
        json.decodeFromString<ApiList<T>>(text).data

    private fun rootObj(text: String): JsonObject =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))

    private fun firstArray(o: JsonObject, vararg names: String): List<JsonElement> {
        for (n in names) {
            val v = o[n] ?: continue
            if (v is kotlinx.serialization.json.JsonArray) return v.jsonArray
            val inner = v as? JsonObject ?: continue
            val arr = firstArray(inner, *names)
            if (arr.isNotEmpty()) return arr
        }
        return emptyList()
    }

    private fun txt(e: JsonElement?): String = when (e) {
        null -> ""
        is JsonPrimitive -> e.contentOrNull ?: ""
        else -> e.toString().trim('"')
    }

    private fun pick(o: JsonObject, vararg names: String): String {
        for (n in names) {
            val v = txt(o[n])
            if (v.isNotBlank() && v != "null") return v.trim()
        }
        return ""
    }

    private fun num(s: String): Double = s.toDoubleOrNull() ?: 0.0
}
