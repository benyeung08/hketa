package com.hketa.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 補返**官方接口攞唔到嘅車站資料** —— 解決索引統計入面嗰啲「0」。
 *
 * ## 問題
 *
 * 索引統計入面有幾個營辦商長期係 0：
 *
 * | 營辦商 | 路線 | 車站 | 原因 |
 * |---|---|---|---|
 * | 城巴 CTB | 407 | **0** | 官方**冇全量車站接口**，淨得逐條路線查 |
 * | 專線小巴 GMB | **0** | **0** | 官方 `/route/{region}` 成日失敗 |
 * | 港鐵巴士 MTR_BUS | **0** | **0** | 官方淨得逐條路線 `getSchedule` |
 *
 * 逐條路線去補（即「深度索引」）理論上得，但要打幾百個請求，
 * 而且官方接口一改就斷 —— 實測係「新增 0 個車站」。
 *
 * ## 解法：用 GitHub 上每日聚合好嘅一份
 *
 * [hkbus/hk-bus-crawling](https://github.com/hkbus/hk-bus-crawling) 每日
 * 由 data.gov.hk、運輸署 GTFS、MTR open data 聚合一次，出一份
 * `routeFareList.min.json`（gh-pages，`https://data.hkbus.app/`），
 * 入面**一次過有齊全部營辦商嘅路線、車站同名同座標**：
 *
 * ```jsonc
 * routeList: { [routeId]: {
 *   route, co: ["kmb","ctb",...],
 *   stops: { [co]: ["stopId", ...] },   // 車站順序就係站序
 *   bound: { [co]: "O" | "I" },
 *   orig: {zh,en}, dest: {zh,en}, serviceType, nlbId, gtfsId
 * } }
 * stopList: { [stopId]: { location: {lat,lng}, name: {zh,en} } }
 * ```
 *
 * 即係**一個請求**就補到城巴車站、專線小巴、港鐵巴士。
 *
 * ## 邊個屬邊個營辦商
 *
 * `co` 嘅值：`kmb` / `ctb` / `nlb` / `gmb` / `lrtfeeder` / `mtr` / `lightRail`。
 * 當中 **`lrtfeeder` 就係港鐵巴士**（MTR bus，即 K51、K54 呢啲），
 * 唔係輕鐵 —— 輕鐵係 `lightRail`。呢個對位係關鍵，對錯咗
 * 港鐵巴士會永遠係 0。
 *
 * ## 只補缺少嘅，唔覆蓋已有嘅
 *
 * [merge] 嘅規則係「現有係空先用聚合檔」：
 *
 * - 城巴：路線用官方（407 條，最準），**車站同站序用聚合檔**
 * - 專線小巴／港鐵巴士：本來就係 0 → 全部用聚合檔
 * - 九巴／嶼巴／港鐵／輕鐵：**一律唔掂**，官方資料好地地
 *
 * 所以唔會出現「補完反而少咗」或者「ETA 本來查到變查唔到」。
 *
 * ## 唔會自動下載
 *
 * 嗰份檔唔細（幾 MB），所以同官方班次資料一樣要**手動撳掣下載**，
 * 落咗會 cache 喺 `filesDir/hketa_hkbus.json`，之後重建索引自動併入。
 */
object HkbusIndex {

    /** 聚合檔（同 [HeadwayDb.URL] 同一份，理論上可以共用快取） */
    const val URL = "https://data.hkbus.app/routeFareList.min.json"

    private const val FILE = "hketa_hkbus.json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 落咗嘅聚合資料（淨係留三個缺資料嘅營辦商）。
     *
     * 刻意**唔**存成個 `routeFareList` —— 入面有大量 fare／freq 資料，
     * 我哋一件都唔要，存晒會嘥幾 MB。
     */
    @Serializable
    data class Bundle(
        val atMs: Long = 0L,
        val routes: List<RouteDef> = emptyList(),
        val stops: List<StopDef> = emptyList(),
        val routeStops: List<RouteStopDef> = emptyList(),
        /**
         * 站名英文名對照（`stopList.name.en`）。
         *
         * 用嚟解決「嶼巴英文名淨係得內置嗰 154 個」呢條限制 ——
         * 聚合檔每個站都有 `name.en`，覆蓋晒全部營辦商。
         */
        val names: List<NamePair> = emptyList(),
        /** 解析嗰陣嘅欄位命中率（畀 [DataGuard.fieldSanity] 判斷格式有冇變） */
        val diag: Diag = Diag()
    )

    /** 一條站名中英對照 */
    @Serializable
    data class NamePair(
        val op: Operator = Operator.KMB,
        val id: String = "",
        val zh: String = "",
        val en: String = ""
    )

    /**
     * 解析嗰陣數到嘅**欄位命中率**。
     *
     * 全部帶預設值 —— 舊快取（冇呢個字段）照 decode 到，只係命中率係 0，
     * 而 [DataGuard.fieldSanity] 見到 0 又 `stopsSeen` 唔夠會直接放行，
     * 所以唔會因為升級就無啦啦話「格式變咗」。
     *
     * 用嚟捉**靜默降級**：假如 `name.en` 改咗名做 `nameEn`，
     * 解析器唔會報錯，只係每條拎到空字串 —— 英文名會全部消失，
     * 靜靜雞降級去內置嗰 154 個，用戶永遠唔會發現。
     */
    @Serializable
    data class Diag(
        /** 解析器睇咗幾多個站（用嚟判斷「睇咗咁多仲係零」係咪唔正常） */
        val stopsSeen: Int = 0,
        /** 當中有幾多個拎到官方英文名 `name.en` */
        val nameEn: Int = 0,
        /** 當中有幾多個拎到緯度 `location.lat` */
        val locLat: Int = 0,
        /** 解析器睇咗幾多條路線 */
        val routesSeen: Int = 0
    )

    /** 要補嘅營辦商：城巴（缺車站）、專線小巴、港鐵巴士 */
    private val TARGETS = listOf(Operator.CTB, Operator.GMB, Operator.MTR_BUS)

    // ──────────────────────────── 讀寫 ────────────────────────────

    private fun file(context: Context) = File(context.filesDir, FILE)

    /** 上次落咗嘅資料；冇落過返 `null` */
    fun cached(context: Context): Bundle? {
        val f = file(context)
        val raw = runCatching { if (f.exists()) f.readText() else null }.getOrNull()
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<Bundle>(raw) }.getOrNull()
    }

    /** 有冇落過（設定頁顯示用） */
    fun hasCache(context: Context): Boolean = cached(context)?.stops?.isNotEmpty() ?: false

    /** 落咗幾耐（ms）；冇落過返 0 */
    fun ageMs(context: Context): Long {
        val at = cached(context)?.atMs ?: 0L
        return if (at <= 0L) 0L else (System.currentTimeMillis() - at).coerceAtLeast(0L)
    }

    private fun save(context: Context, b: Bundle) {
        val text = runCatching { json.encodeToString(b) }.getOrNull() ?: return
        runCatching {
            val f = file(context)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
        }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    // ──────────────────── 英文名對照（解決「冇官方英文名」） ────────────────────

    @Volatile private var byId: Map<String, String> = emptyMap()
    @Volatile private var byZh: Map<String, String> = emptyMap()
    @Volatile private var totalNames: Int = 0

    /**
     * 把 cache 讀入記憶體。
     *
     * 一定要 call —— [HkbusIndex.enOf] 淨係睇記憶體入面嗰份，
     * 唔會每次都讀碟（車站解析係逐個站行，讀碟會慢到冇可能接受）。
     */
    fun prime(context: Context) {
        val b = runCatching { cached(context) }.getOrNull()
        val ns = b?.names ?: emptyList()
        totalNames = ns.size
        byId = HashMap<String, String>(ns.size).apply {
            for (n in ns) if (n.id.isNotBlank() && n.en.isNotBlank()) putIfAbsent(n.id, n.en)
        }
        byZh = HashMap<String, String>(ns.size).apply {
            for (n in ns) if (n.zh.isNotBlank() && n.en.isNotBlank()) putIfAbsent(n.zh, n.en)
        }
    }

    /**
     * 由聚合檔補英文名。
     *
     * 先對 stop id（最準）；對唔到就用**中文名**對 ——
     * 因為官方 id 同聚合檔 id 未必同源（例如小巴），
     * 但中文站名兩邊都係官方名，一定對得到。
     *
     * @return 搵到就返英文名；搵唔到返 `""`（呼叫方照舊用中文，唔會亂砌）
     */
    fun enOf(zh: String, id: String = ""): String {
        if (id.isNotBlank()) byId[id]?.let { return it }
        val z = zh.trim()
        if (z.isNotBlank()) byZh[z]?.let { return it }
        return ""
    }

    /** 補咗幾多條中英對照（設定頁顯示用） */
    fun nameCount(): Int = totalNames

    /**
     * 下載並存檔。
     *
     * 落碟之前要過兩關（見 [DataGuard]）：
     *
     * 1. **欄位命中率** —— `name.en`／`location.lat` 一個都拎唔到即係格式變咗，
     *    寧願大聲失敗好過靜靜雞降級
     * 2. **條目數暴跌** —— 同上一次比跌超過 30% 就**唔寫**，
     *    機上嗰份好嘅快取會原封不動留低
     *
     * 兩關都唔過會 `error(...)`，`suspendCatching` 會捉住做 `Result.failure` ——
     * 設定頁見到嘅就係嗰句原因，唔係一句「下載失敗」。
     *
     * 用 [suspendCatching] 而唔係 `runCatching` —— [Http.get] 係 suspend，
     * 標準 runCatching 嘅非 suspend lambda 包唔住。
     */
    suspend fun download(context: Context): Result<Bundle> = suspendCatching {
        val raw = Http.get(URL)
        if (raw.isBlank()) error("下載返嚟係空嘅")
        val b = parse(raw)
        if (b.stops.isEmpty()) error("解析唔到車站（可能格式變咗）")

        // 關一：欄位係咪消失咗（捉靜默降級）
        DataGuard.fieldSanity(b.diag.stopsSeen, b.diag.nameEn, b.diag.locLat)
            ?.let { error(it) }

        // 關二：同上一次比，暴跌就留住舊嘅唔好寫
        val prev = cached(context)
        DataGuard.shouldReject(
            prevStops = prev?.stops?.size ?: 0,
            prevNames = prev?.names?.size ?: 0,
            nextStops = b.stops.size,
            nextNames = b.names.size
        )?.let { error(it) }

        save(context, b)
        b
    }

    // ──────────────────────────── 解析 ────────────────────────────

    /**
     * 由 `routeFareList` 抽出車站、站序同路線。
     *
     * 全部手動由 [JsonElement] 抽完即丟 —— 唔會為咗幾個欄位
     * 反序列化成個幾 MB 嘅檔。任何一層唔啱就跳過嗰條，
     * **唔會令成個索引冇咗**。
     */
    fun parse(raw: String): Bundle {
        val root = objOf(runCatching { json.parseToJsonElement(raw) }.getOrNull())
            ?: return Bundle()
        val stopList = objOf(root["stopList"])
        val routeList = objOf(root["routeList"]) ?: return Bundle()

        val routes = LinkedHashMap<String, RouteDef>()
        val stops = LinkedHashMap<String, StopDef>()
        val rs = LinkedHashMap<String, RouteStopDef>()
        val names = LinkedHashMap<String, NamePair>()

        // 欄位命中率：數吓「睇咗幾多個站」入面有幾多個真係拎到
        // name.en / location.lat。全部係零即係欄位改咗名（見 [Diag]）。
        var seenRoutes = 0
        var seenStops = 0
        var seenNameEn = 0
        var seenLat = 0

        routeList.forEach { (_, value) ->
            val o = objOf(value) ?: return@forEach
            val routeNo = s(o["route"])
            if (routeNo.isBlank()) return@forEach
            seenRoutes++

            val coArr = runCatching { o["co"]?.jsonArray }.getOrNull() ?: return@forEach
            val stopsByCo = objOf(o["stops"]) ?: return@forEach
            val boundByCo = objOf(o["bound"])
            val svc = s(o["serviceType"]).ifBlank { "01" }
            val orig = pick(objOf(o["orig"]), "zh", "tc")
            val dest = pick(objOf(o["dest"]), "zh", "tc")
            val gtfsId = s(o["gtfsId"])
            val nlbId = s(o["nlbId"])

            for (ce in coArr) {
                val co = s(ce)
                if (co.isBlank()) continue
                val op = opOf(co) ?: continue

                val ids = runCatching { stopsByCo[co]?.jsonArray }.getOrNull()

                // ★ 英文名對照：全部營辦商都 harvest（九巴除外 —— 官方本身有 name_en）
                harvest(op, ids, stopList, names)

                if (op !in TARGETS) continue
                if (ids == null || ids.isEmpty()) continue

                val bound = boundOf(op, s(boundByCo?.get(co)))
                // 小巴用 GTFS id；港鐵巴士同官方一樣淨係認路線名
                val routeId = when (op) {
                    Operator.GMB -> gtfsId
                    Operator.NLB -> nlbId
                    else -> ""
                }
                val rd = RouteDef(
                    op = op,
                    route = routeNo,
                    bound = bound,
                    serviceType = svc,
                    orig = orig,
                    dest = dest,
                    routeId = routeId
                )
                routes.putIfAbsent(rd.key, rd)

                var seq = 0
                for (se in ids) {
                    val sid = s(se)
                    if (sid.isBlank()) continue
                    seq++
                    // 同一個站牌唔同營辦商有唔同 id → 各自記一份（op 唔同）
                    val key = "${op.name}|$sid"
                    if (!stops.containsKey(key)) {
                        val sd = stopOf(op, sid, stopList)
                        stops[key] = sd
                        seenStops++
                        if (sd.nameEn.isNotBlank()) seenNameEn++
                        if (sd.lat != 0.0) seenLat++
                    }
                    rs["${op.name}|$routeNo|$bound|$routeId|$sid"] =
                        RouteStopDef(op, routeNo, bound, svc, routeId, seq, sid)
                }
            }
        }

        return Bundle(
            System.currentTimeMillis(),
            routes.values.toList(),
            stops.values.toList(),
            rs.values.toList(),
            names.values.toList(),
            Diag(
                stopsSeen = seenStops,
                nameEn = seenNameEn,
                locLat = seenLat,
                routesSeen = seenRoutes
            )
        )
    }

    /**
     * 收集中英站名對照（淨係做 **除咗九巴以外** 嘅營辦商）。
     *
     * 九巴官方接口本身有 `name_en`，唔使補；其餘營辦商（尤其嶼巴）
     * 官方接口**冇英文欄位**，所以要用聚合檔嘅 `stopList.name.en`。
     */
    private fun harvest(
        op: Operator,
        ids: JsonArray?,
        stopList: JsonObject?,
        out: LinkedHashMap<String, NamePair>
    ) {
        if (op == Operator.KMB) return
        if (ids == null) return
        for (se in ids) {
            val sid = s(se)
            if (sid.isBlank() || out.containsKey(sid)) continue
            val so = objOf(stopList?.get(sid)) ?: continue
            val zh = pick(objOf(so["name"]), "zh", "tc")
                .ifBlank { s(so["name_tc"]).ifBlank { s(so["name"]) } }
            val en = pick(objOf(so["name"]), "en")
                .ifBlank { s(so["name_en"]) }
            if (zh.isBlank() || en.isBlank()) continue
            out[sid] = NamePair(op, sid, zh, en)
        }
    }

    /** 由 `stopList` 砌一個站；搵唔到都照出（有名冇座標都好過冇） */
    private fun stopOf(op: Operator, sid: String, stopList: JsonObject?): StopDef {
        val so = objOf(stopList?.get(sid)) ?: return StopDef(op = op, id = sid)
        val nameObj = objOf(so["name"])
        val name = pick(nameObj, "zh", "tc")
            .ifBlank { s(so["name_tc"]).ifBlank { s(so["name"]) } }
        val nameEn = pick(nameObj, "en")
            .ifBlank { s(so["name_en"]) }
        val loc = objOf(so["location"])
        val lat = d(loc?.get("lat"), loc?.get("latitude"))
        val lon = d(loc?.get("lng"), loc?.get("long"), loc?.get("lon"))
        return StopDef(op = op, id = sid, name = name, nameEn = nameEn, lat = lat, lon = lon)
    }

    /**
     * `co` → [Operator]。
     *
     * `lrtfeeder` = **港鐵巴士**（MTR bus，K51／K54 呢啲），
     * `lightRail` 先係輕鐵 —— 呢兩個對掉咗港鐵巴士會永遠係 0。
     */
    private fun opOf(co: String): Operator? = when (co.trim().lowercase()) {
        "kmb" -> Operator.KMB
        "ctb" -> Operator.CTB
        "nlb" -> Operator.NLB
        "gmb" -> Operator.GMB
        "lrtfeeder" -> Operator.MTR_BUS
        "mtr" -> Operator.MTR_HR
        "lightrail", "light_rail", "light-rail", "lrt" -> Operator.LRT
        else -> null
    }

    /**
     * 聚合檔一律用 `O` / `I`；項目入面九巴跟官方用 `1` / `2`。
     * 城巴官方本身就係 `O` / `I`，照用。
     */
    private fun boundOf(op: Operator, raw: String): String {
        val v = raw.trim()
        if (v.isEmpty()) return ""
        val up = v.uppercase()
        return if (op == Operator.KMB) when (up) {
            "O" -> "1"
            "I" -> "2"
            else -> up
        } else up
    }

    // ──────────────────────────── 合併 ────────────────────────────

    /**
     * 把聚合檔併入現有索引。
     *
     * 規則係**補缺唔覆蓋**：現有嘅一律保留排先（官方最準），
     * 聚合檔淨係補「現有冇嘅」嗰啲。所以
     *
     * - 城巴：官方 407 條路線原封不動，補嘅係**車站同站序**
     *   （官方冇全量車站接口，本來係 0）
     * - 專線小巴／港鐵巴士：本來就係 0 → 全部由聚合檔出
     * - 九巴／嶼巴／港鐵／輕鐵：**一律唔掂**（聚合檔亦唔會出呢幾個）
     *
     * 用「補缺」而唔係「現有係空先用聚合檔」，係因為深度索引可能
     * 只補到一部份（例如淨補咗十條線），嗰陣兩邊要**互補**而唔係二選一。
     */
    fun merge(cur: IndexData, b: Bundle): IndexData {
        val haveRoutes = cur.routes.mapTo(HashSet()) { it.key }
        val haveStops = cur.stops.mapTo(HashSet()) { "${it.op.name}|${it.id}" }
        val haveRs = cur.routeStops.mapTo(HashSet()) { rsKey(it) }

        val addRoutes = b.routes.filter { it.op in TARGETS && it.key !in haveRoutes }
        val addStops = b.stops.filter { it.op in TARGETS && "${it.op.name}|${it.id}" !in haveStops }
        val addRs = b.routeStops.filter { it.op in TARGETS && rsKey(it) !in haveRs }

        return cur.copy(
            routes = (cur.routes + addRoutes).distinctBy { it.key },
            stops = (cur.stops + addStops).distinctBy { "${it.op.name}|${it.id}" },
            routeStops = (cur.routeStops + addRs).distinctBy { rsKey(it) },
            builtAt = System.currentTimeMillis()
        )
    }

    /** 站序嘅唯一 key（同索引去重嗰條一致） */
    private fun rsKey(rs: RouteStopDef): String =
        "${rs.op.name}|${rs.route}|${rs.bound}|${rs.routeId}|${rs.stopId}"

    /** 每個營辦商補咗幾多個站（設定頁／訊息顯示用） */
    fun countByOp(b: Bundle): Map<Operator, Int> {
        val out = LinkedHashMap<Operator, Int>()
        for (op in TARGETS) {
            val n = b.stops.count { it.op == op }
            if (n > 0) out[op] = n
        }
        return out
    }

    // ──────────────────────────── 小工具 ────────────────────────────

    private fun objOf(el: JsonElement?): JsonObject? = runCatching { el?.jsonObject }.getOrNull()

    private fun s(el: JsonElement?): String =
        runCatching { el?.jsonPrimitive?.content }.getOrNull()?.trim().orEmpty()

    private fun d(vararg els: JsonElement?): Double {
        for (el in els) {
            val v = runCatching { el?.jsonPrimitive?.content }.getOrNull()?.trim()
            val n = v?.toDoubleOrNull()
            if (n != null && n != 0.0) return n
        }
        return 0.0
    }

    private fun pick(o: JsonObject?, vararg names: String): String {
        if (o == null) return ""
        for (n in names) {
            val v = s(o[n])
            if (v.isNotBlank()) return v
        }
        return ""
    }
}
