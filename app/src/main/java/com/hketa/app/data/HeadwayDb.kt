package com.hketa.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 官方班次間隔（第三方資料）—— **解決「官方冇公開時刻表」呢個限制**。
 *
 * ## 之前點解冇得做
 *
 * 各營辦商嘅即時到站接口（KMB／CTB／NLB／GMB／MTR）**全部只提供 ETA**，
 * 冇發車時刻表、冇班次間隔、冇頭尾班車 —— 所以之前要靠觀察累積去估。
 *
 * ## 其實官方有，只係唔喺 ETA 接口
 *
 * 運輸署有「公共交通服務班次資料」：
 *
 *   https://static.data.gov.hk/td/pt-headway-en/gtfs.zip
 *
 * 入面嘅 `frequencies.txt` 就係官方班次間隔（headway），每兩星期更新。
 *
 * 但嗰個係 **30 份 GTFS 嘅大 zip**，手機下載＋解析成本太高，
 * 所以呢度改用 **hkbus/hk-bus-crawling** 每日聚合好嘅版本：
 *
 *   https://data.hkbus.app/routeFareList.min.json
 *
 * 佢本身由 data.gov.hk ＋ 運輸署 GTFS ＋ 1823.gov.hk 公眾假期聚合，
 * 每個 route 有兩個我哋要嘅欄位：
 *
 * - `freq`     —— 官方班次間隔：serviceType → 時段起點(HH:mm) → [最快分鐘, 最慢分鐘]
 * - `holidays` —— 公眾假期（服務類型要跟住轉）
 *
 * ## 所以解決到啲乜
 *
 * | 之前 | 而家 |
 * |---|---|
 * | 班次間隔要靠 ETA 相鄰兩班估 | 直接顯示**官方**班次間隔 |
 * | 頭／尾班車淨係觀察累積 | 由 freq 時段推算**營運時段**（仍標明係推算） |
 * | 唔知今日係咪假日班次 | 有公眾假期清單，會提示 |
 *
 * ## 一定回退
 *
 * 冇下載／下載失敗／解析唔到 → [lookup] 一律返 `null`，
 * UI 行返原本（ETA 推算 ＋ 觀察累積），**功能完全一樣**。
 *
 * ## ⚠️ 未實測
 *
 * 寫呢個檔案嗰陣沙盒網絡被攔（HTTP 403），**下載唔到真資料實測**。
 * 所以解析器寫到極度防禦：任何欄位缺失、型別唔啱、數值古怪
 * 都只會令嗰條路線冇資料，**唔會崩、唔會令成個 db 變空**。
 */
object HeadwayDb {

    /** 資料來源網址 */
    const val URL = "https://data.hkbus.app/routeFareList.min.json"

    /** 來源標籤（UI 顯示用） */
    const val SOURCE_LABEL = "運輸署 GTFS · data.hkbus.app"

    /** Rust store 命名空間 */
    private const val NS = "hw"

    /** 存放 db 嘅 key */
    private const val KEY = "db"

    /** 超過呢個大小就放棄解析（費事食爆記憶體） */
    private const val MAX_BYTES = 48 * 1024 * 1024

    /** 資料新鮮度：超過 14 日提示可以更新（運輸署本身係兩星期一更） */
    private const val STALE_MS = 14 * 24 * 60 * 60 * 1000L

    // ──────────────────────────── 資料結構 ────────────────────────────

    /** 一條路線（一個方向）嘅官方班次間隔 */
    @Serializable
    data class RouteHeadway(
        val route: String = "",
        /** 起點站中文（用嚟對方向） */
        val origZh: String = "",
        /** 終點站中文 */
        val destZh: String = "",
        /** 起點站英文（hkbus 嘅 routeId 用，後備匹配） */
        val origEn: String = "",
        /** 終點站英文 */
        val destEn: String = "",
        /**
         * 班次間隔：serviceType → 時段起點(HH:mm) → [最快分鐘, 最慢分鐘]
         * 例如 `{"01": {"05:00": [10,15], "09:00": [12,18]}}`
         */
        val freq: Map<String, Map<String, List<Int>>> = emptyMap()
    ) {
        val hasFreq: Boolean
            get() = freq.values.any { it.isNotEmpty() }
    }

    /** 精簡後嘅資料庫 */
    @Serializable
    data class Db(
        val atMs: Long = 0L,
        val source: String = SOURCE_LABEL,
        /** 公眾假期（yyyy-MM-dd） */
        val holidays: List<String> = emptyList(),
        /**
         * 實際解析到嘅路線**方向**數。
         * 注意：唔係 [routes] 嘅 key 數 —— 因為同一條路線會寫兩個 key
         * （serviceType 有冇補零），key 數會多過實際路線數。
         */
        val entryCount: Int = 0,
        /** `"route+serviceType"` → 候選方向 */
        val routes: Map<String, List<RouteHeadway>> = emptyMap()
    ) {
        val routeCount: Int get() = entryCount
    }

    /** 查到嘅官方班次資料 */
    data class Official(
        /** 最快班次間隔（分鐘）；null = 官方冇畀 */
        val loMin: Int?,
        /** 最慢班次間隔（分鐘） */
        val hiMin: Int?,
        /** 營運時段起（HH:mm）—— 由 freq 時段**推算**，唔係官方頭班車 */
        val from: String?,
        /**
         * 表列**最後一個時段嘅開始**（HH:mm）。
         *
         * ⚠️ 唔係尾班車：hkbus 嘅 freq 淨係畀時段起點，冇 end_time，
         * 所以呢個係「表列最後一段幾時開始」，尾班車實際幾點**唔知**。
         */
        val to: String?,
        /** 今日係咪公眾假期 */
        val holiday: Boolean,
        /** 呢刻過咗表列時段起點未（未到就係未開班） */
        val running: Boolean,
        /**
         * 呢刻過咗表列最後一個時段（即 [to] 之後）。
         *
         * 因為冇 end_time，呢個**唔代表冇車** —— 只係官方表列資料到呢度為止。
         * UI 要講「表列時段已過」，唔可以寫「已過尾班車」。
         */
        val pastTable: Boolean,
        /** 方向有冇對到（false = 來回冇細分，係合併計） */
        val boundMatched: Boolean,
        /** 資料幾時抓 */
        val atMs: Long,
        val source: String = SOURCE_LABEL,
        /**
         * 資料有冇**官方 `end_time`**。
         *
         * - `true`（[TdHeadway]，運輸署 GTFS 原檔）：`to` 係真嘅時段結束時間，
         *   UI 可以講「已過官方表列服務時段」
         * - `false`（本檔，hkbus 聚合版）：`to` 只係「表列最後一段幾時開始」，
         *   UI **唔可以**講「已過尾班車」
         */
        val endKnown: Boolean = false,
        /** 今日官方表列冇班次（例假日表）—— 淨係 [TdHeadway] 會出 */
        val closedToday: Boolean = false
    ) {
        /** 間隔文字，例如「10–15」／「12」／null */
        val rangeText: String?
            get() {
                val a = loMin ?: return null
                val b = hiMin
                return if (b == null || b == a) a.toString() else "$a–$b"
            }
    }

    /** 更新結果 */
    data class UpdateResult(
        val ok: Boolean,
        val routeCount: Int = 0,
        val holidayCount: Int = 0,
        val message: String = ""
    )

    // ──────────────────────────── 狀態 ────────────────────────────

    @Volatile
    private var cached: Db? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /** 而家手上嘅資料（冇就 null） */
    fun current(): Db? = cached

    /** 有冇資料 */
    fun hasData(): Boolean = (cached?.routeCount ?: 0) > 0

    // ──────────────────────────── 讀寫 ────────────────────────────

    private fun legacyFile(context: Context) = File(context.filesDir, "hketa_headway.json")

    /**
     * 由 **Rust store** 讀；Rust 話冇就睇返舊 JSON 檔，讀到會順手搬遷。
     * Kotlin 版冇 .so 一樣用到（淨係效能差啲）。
     */
    fun load(context: Context): Db? {
        cached?.let { return it }
        val raw = if (RustStore.available) {
            RustStore.get(NS, KEY) ?: runCatching {
                val f = legacyFile(context)
                if (!f.exists()) null else f.readText().also { RustStore.put(NS, KEY, it) }
            }.getOrNull()
        } else {
            runCatching {
                val f = legacyFile(context)
                if (!f.exists()) null else f.readText()
            }.getOrNull()
        }
        if (raw.isNullOrBlank()) return null
        val db = runCatching { json.decodeFromString<Db>(raw) }.getOrNull()
        cached = db
        return db
    }

    private fun save(context: Context, db: Db) {
        val text = runCatching { json.encodeToString(db) }.getOrNull() ?: return
        cached = db
        if (RustStore.available) {
            runCatching { if (RustStore.put(NS, KEY, text)) RustStore.flush() }
            return
        }
        runCatching {
            val f = legacyFile(context)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
        }
    }

    /** 清除手上嘅資料 */
    fun clear(context: Context) {
        cached = null
        runCatching { RustStore.del(NS, KEY) }
        runCatching { RustStore.clear(NS) }
        runCatching { legacyFile(context).delete() }
    }

    /** 係咪已經過時（超過兩星期） */
    fun isStale(db: Db?): Boolean {
        val d = db ?: return true
        if (d.atMs <= 0L) return true
        return System.currentTimeMillis() - d.atMs > STALE_MS
    }

    // ──────────────────────────── 下載 ────────────────────────────

    /**
     * 下載並更新班次資料。
     *
     * 因為檔案唔細（成個 routeFareList），所以**淨係喺用家撳掣**嗰陣先落，
     * 唔會開 App 自動打。失敗一律返 [UpdateResult.ok] = false 並帶原因，
     * **唔會影響原本功能**。
     */
    suspend fun refresh(context: Context, force: Boolean = false): UpdateResult {
        val cur = load(context)
        if (!force && cur != null && !isStale(cur)) {
            return UpdateResult(true, cur.routeCount, cur.holidays.size, "already-fresh")
        }
        return runCatching {
            val raw = Http.get(URL)
            if (raw.length > MAX_BYTES) {
                return UpdateResult(false, message = "檔案過大（${raw.length / 1024 / 1024} MB）")
            }
            val db = parse(raw)
            if (db.routeCount == 0) {
                return UpdateResult(false, message = "解析唔到班次資料")
            }
            save(context, db)
            UpdateResult(true, db.routeCount, db.holidays.size, "ok")
        }.getOrElse { e ->
            UpdateResult(false, message = e.message ?: "下載失敗")
        }
    }

    // ──────────────────────────── 解析 ────────────────────────────

    /**
     * 由 hkbus 嘅 `routeFareList` 抽出淨係需要嘅嘢。
     *
     * 刻意**唔**用 @Serializable 反序列化成個檔案 —— 因為入面有大量
     * stop／fare 資料，我哋一件都唔要，全 deserialize 會嘥記憶體。
     * 所以手動由 JsonElement 抽完即丟。
     */
    fun parse(raw: String): Db {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return Db()

        // holidays: string[]
        val holidays = ArrayList<String>()
        runCatching {
            root["holidays"]?.jsonArray?.forEach { el ->
                val s = runCatching { el.jsonPrimitive.content }.getOrNull()
                if (!s.isNullOrBlank()) holidays.add(s.trim())
            }
        }

        // routeList: { routeId -> {...} }
        // routeId 格式：`route+serviceType+origEn+destEn`
        val routes = LinkedHashMap<String, ArrayList<RouteHeadway>>()
        var entryCount = 0
        val routeList = runCatching { root["routeList"]?.jsonObject }.getOrNull()
        routeList?.forEach { (rid, value) ->
            val o = runCatching { value.jsonObject }.getOrNull() ?: return@forEach
            val freq = extractFreq(o["freq"])
            if (freq.isEmpty()) return@forEach   // 冇班次資料嘅路線直接唔要

            val route = str(o["route"]) ?: return@forEach
            val svc = str(o["serviceType"]) ?: "01"
            val origZh = str(runCatching { o["orig"]?.jsonObject?.get("zh") }.getOrNull()).orEmpty()
            val destZh = str(runCatching { o["dest"]?.jsonObject?.get("zh") }.getOrNull()).orEmpty()
            val origEn = str(runCatching { o["orig"]?.jsonObject?.get("en") }.getOrNull()).orEmpty()
            val destEn = str(runCatching { o["dest"]?.jsonObject?.get("en") }.getOrNull()).orEmpty()

            // 索引 key 用 route+serviceType，兩個格式都寫（有冇補零都搵到）
            val s = svc.trim().ifBlank { "01" }
            val k1 = "$route+$s"
            val k2 = "$route+${s.trimStart('0').ifBlank { "0" }}"
            val item = RouteHeadway(route, origZh, destZh, origEn, destEn, freq)
            routes.getOrPut(k1) { ArrayList() }.add(item)
            if (k2 != k1) routes.getOrPut(k2) { ArrayList() }.add(item)
            entryCount++
        }

        return Db(
            atMs = System.currentTimeMillis(),
            holidays = holidays.distinct().sorted(),
            entryCount = entryCount,
            routes = routes
        )
    }

    /** 抽出 `freq`：serviceType → 時段(HH:mm) → [最快, 最慢] */
    private fun extractFreq(el: JsonElement?): Map<String, Map<String, List<Int>>> {
        val obj = runCatching { el?.jsonObject }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, Map<String, List<Int>>>()
        obj.forEach { (svc, value) ->
            val inner = runCatching { value.jsonObject }.getOrNull() ?: return@forEach
            val slots = LinkedHashMap<String, List<Int>>()
            inner.forEach { (tm, pair) ->
                // 值係 ["10","15"]，亦可能係 null（嗰段冇服務）
                val arr = runCatching { pair.jsonArray }.getOrNull() ?: return@forEach
                val lo = num(arr.getOrNull(0))
                if (lo == null) return@forEach
                val hi = num(arr.getOrNull(1)) ?: lo
                // 間隔應該係正數；超過 180 分鐘當壞資料
                if (lo in 1..180 && hi in 1..180) {
                    slots[tm.trim()] = listOf(lo, hi.coerceAtLeast(lo))
                }
            }
            if (slots.isNotEmpty()) out[svc.trim()] = slots
        }
        return out
    }

    private fun str(el: JsonElement?): String? =
        runCatching { el?.jsonPrimitive?.content }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }

    private fun num(el: JsonElement?): Int? {
        val c = runCatching { el?.jsonPrimitive?.content }.getOrNull() ?: return null
        return c.trim().toIntOrNull()
    }

    // ──────────────────────────── 查詢 ────────────────────────────

    /** 而家幾點（由 00:00 起計嘅分鐘數） */
    fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** 今日係咪公眾假期（yyyy-MM-dd） */
    fun todayIsHoliday(db: Db?): Boolean {
        val d = db ?: return false
        if (d.holidays.isEmpty()) return false
        val today = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        }.getOrNull() ?: return false
        return d.holidays.any { it == today }
    }

    /**
     * 查呢條路線嘅官方班次間隔。
     *
     * @return 搵到就返 [Official]；冇資料／未下載一律返 `null`（UI 行返原本做法）
     */
    fun lookup(rd: RouteDef, nowMin: Int = nowMinutes()): Official? {
        val db = cached ?: return null
        val route = rd.route.trim()
        if (route.isEmpty()) return null

        val stRaw = rd.serviceType.trim().ifBlank { "01" }
        val padded = if (stRaw.length == 1) "0$stRaw" else stRaw
        val stripped = stRaw.trimStart('0').ifBlank { "0" }

        val cands = db.routes["$route+$padded"]
            ?: db.routes["$route+$stripped"]
            ?: db.routes["$route+01"]
            ?: return null
        if (cands.isEmpty()) return null

        // ── 對方向：用中文總站名（orig/dest）
        val dest = rd.dest.trim()
        val orig = rd.orig.trim()
        var boundMatched = false

        fun pickBy(field: String, get: (RouteHeadway) -> String): RouteHeadway? {
            if (field.isBlank()) return null
            val exact = cands.firstOrNull { get(it) == field }
            if (exact != null) return exact
            // 站名有時會多咗括號註記（「（循環線）」），所以用互相包含
            return cands.firstOrNull {
                val v = get(it)
                v.isNotBlank() && (v.contains(field) || field.contains(v))
            }
        }

        val pick = pickBy(dest) { it.destZh }?.also { boundMatched = true }
            ?: pickBy(orig) { it.origZh }?.also { boundMatched = true }
            ?: pickBy(dest) { it.destEn }?.also { boundMatched = true }
            ?: cands.firstOrNull { it.hasFreq }
            ?: cands.first()

        // ── 揀 serviceType
        val freqMap = pick.freq[padded]
            ?: pick.freq[stripped]
            ?: pick.freq.values.firstOrNull { it.isNotEmpty() }
            ?: return null
        if (freqMap.isEmpty()) return null

        // ── 搵而家落邊個時段
        val slots = ArrayList<Pair<Int, String>>()
        freqMap.forEach { (tm, pair) ->
            val m = parseHhmm(tm)
            if (m != null) slots.add(m to tm)
        }
        if (slots.isEmpty()) return null
        slots.sortBy { it.first }

        val fromMin = slots.first().first
        val toMin = slots.last().first
        val curKey = slots.lastOrNull { it.first <= nowMin }?.second ?: slots.first().second
        val hw = freqMap[curKey]

        return Official(
            loMin = hw?.getOrNull(0),
            hiMin = hw?.getOrNull(1),
            from = hhmm(fromMin),
            to = hhmm(toMin),
            holiday = todayIsHoliday(db),
            running = nowMin >= fromMin,
            pastTable = nowMin > toMin,
            boundMatched = boundMatched,
            atMs = db.atMs
        )
    }

    /** "05:30" → 330；唔啱格式返 null */
    private fun parseHhmm(s: String): Int? {
        val p = s.trim().split(":")
        if (p.isEmpty()) return null
        val h = p[0].trim().toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        if (h !in 0..29 || m !in 0..59) return null
        return h * 60 + m
    }

    /** 330 → "05:30" */
    private fun hhmm(min: Int): String {
        val h = (min / 60) % 30
        val m = min % 60
        return "%02d:%02d".format(h, m)
    }
}
