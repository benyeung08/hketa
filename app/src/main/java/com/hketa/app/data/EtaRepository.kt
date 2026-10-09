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
    fun gmbRouteStops(routeId: String, seq: String) = "$GMB/route-stop/${enc(routeId)}/${enc(seq)}"
    fun gmbStop(stopId: String) = "$GMB/stop/${enc(stopId)}"
    fun gmbEta(stopId: String, routeId: String) =
        "$GMB/stop-eta/${enc(stopId)}" + if (routeId.isBlank()) "" else "/${enc(routeId)}"

    fun mtrBusSchedule() = "$MTR/getSchedule"

    /** 港鐵重鐵：line = AEL/TML/TKL… ，sta = 三字母站碼 */
    fun mtrHeavyRail(line: String, sta: String) =
        "https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line=${enc(line)}&sta=${enc(sta)}&lang=TC"

    /** 輕鐵：station_id 為數字（官方同時提供 v1 / v2 路徑，失敗時自動互換） */
    fun lightRailV1(stationId: String) =
        "https://rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule?station_id=${enc(stationId)}&with_special=1"

    fun lightRailV2(stationId: String) =
        "https://rt.data.gov.hk/v2/transport/mtr/lrt/getSchedule?station_id=${enc(stationId)}&with_special=1"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

class EtaRepository {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
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
                nameEn = it.name_en,
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
                nameEn = it.name_en,
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
                nameEn = NlbStopNames.englishOf(zhName).orEmpty(),
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

    suspend fun gmbRoutes(region: String): List<RouteDef> {
        val root = rootObj(Http.get(Urls.gmbRoutes(region)))
        val data = root["data"] as? JsonObject ?: root
        val arr = firstArray(data, "routes", "route_list", "data")
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = pick(o, "route_id", "routeId", "id")
            val code = pick(o, "route_code", "routeCode", "route_no", "routeNo")
            if (id.isBlank() || code.isBlank()) return@mapNotNull null
            RouteDef(
                op = Operator.GMB,
                route = code,
                orig = pick(o, "description_tc", "description", "orig_tc"),
                dest = pick(o, "destination_tc", "dest_tc", "destination"),
                routeId = id
            )
        }
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
                nameEn = pick(o, "name_en", "stopNameEn", "description_en"),
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
            nameEn = pick(o, "name_en", "stopNameEn", "description_en"),
            lat = num(pick(o, "latitude", "lat")),
            lon = num(pick(o, "longitude", "long", "lon", "lng"))
        )
    }

    suspend fun gmbEta(stopId: String, routeId: String): List<EtaEntry> {
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

    suspend fun mtrBus(routeName: String): Pair<List<StopDef>, List<EtaEntry>> {
        val text = Http.postForm(
            Urls.mtrBusSchedule(),
            listOf("language" to "zh-Hant", "routeName" to routeName)
        )
        val root = rootObj(text)
        val data = root["data"] as? JsonObject ?: root
        val arr = firstArray(data, "busStops", "bus_stops", "stops", "stopList", "routes")
        val stops = mutableListOf<StopDef>()
        val etas = mutableListOf<EtaEntry>()
        arr.forEach { e ->
            val o = e as? JsonObject ?: return@forEach
            val id = pick(o, "stopId", "stop_id", "id", "code")
            val name = pick(o, "stopNameTc", "stopName", "nameTc", "name_tc", "name")
            val stop = StopDef(
                op = Operator.MTR_BUS,
                id = id.ifBlank { name },
                name = name,
                lat = num(pick(o, "latitude", "lat")),
                lon = num(pick(o, "longitude", "long", "lon", "lng"))
            )
            if (stop.name.isNotBlank()) stops.add(stop)
            val raw = pick(o, "eta", "etaTime", "time", "estimatedArrival")
            if (raw.isNotBlank()) {
                etas.add(
                    EtaEntry(
                        op = Operator.MTR_BUS,
                        route = routeName,
                        dest = pick(o, "destTc", "destinationTc", "destination_tc", "dest_tc"),
                        minutes = TimeUtil.etaMinutes(raw),
                        clock = TimeUtil.etaClock(raw),
                        remark = pick(o, "remark", "remarkTc")
                    )
                )
            }
        }
        return stops to etas
    }

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
        val candidates = listOf(stationId, padded).distinct()
            .flatMap { listOf(Urls.lightRailV1(it), Urls.lightRailV2(it)) }

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
