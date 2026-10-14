package com.hketa.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 官方班次資料（**運輸署 GTFS 原檔**）—— 補返 [HeadwayDb] 缺嗰兩樣嘢。
 *
 * ## [HeadwayDb] 而家用緊乜
 *
 * 用緊 hkbus 聚合好嘅 `routeFareList`，方便但**先天缺兩樣**：
 *
 * 1. `freq` 淨係畀時段**起點**，冇 `end_time` —— 所以「營運時段」係推算，
 *    過咗只可以講「已過表列時段（唔代表冇車）」
 * 2. 假日要用邊個 `serviceType` **冇得驗證**，所以淨係提示、唔自動揀
 *
 * ## 運輸署原檔有呢兩樣
 *
 * `frequencies.txt`（`trip_id, start_time, end_time, headway_secs`）**有 `end_time`**；
 * `calendar.txt`（`monday`…`sunday`）有**每條 service 邊日行**；
 * `calendar_dates.txt`（`exception_type` 1=加開 2=停開）有**個別日子例外** ——
 * 正好補上面嗰兩個窿。
 *
 * ## 點解之前唔直接用
 *
 * 個 zip 裝住 **30 份 GTFS**（包埋 `stop_times.txt`，得個大字）。
 * 呢度改為**淨係落 6 個細文字檔**，逐個 streaming 解析，
 * 而且 `stop_times.txt` **完全唔落**（班次間隔唔需要逐站時間）。
 *
 * ## 一定回退
 *
 * 冇下載／下載失敗／解析唔到／呢條路線官方冇 → [lookup] 一律返 `null`，
 * [HeadwayDb] 同 ETA 推算**照舊**，功能完全一樣。
 *
 * ## ⚠️ 未實測
 *
 * 寫呢個檔案嗰陣沙盒網絡被攔（HTTP 403），**下載唔到真檔實測**。
 * 所以：
 * - CSV **按表頭名**搵欄位（[headerIdx]），**唔靠欄位順序** —— 欄位多咗／次序唔同都唔怕
 * - 語系目錄繁中優先，冇就落英文（運輸署把三語合併咗做一個資料集）
 * - 任何一步壞咗只會令**嗰條路線冇資料**，唔會崩、唔會令成個 db 變空
 */
object TdHeadway {

    /** 語系目錄：繁中優先，唔存在就落英文 */
    private val BASES = listOf(
        "https://static.data.gov.hk/td/pt-headway-tc/",
        "https://static.data.gov.hk/td/pt-headway-en/"
    )

    /**
     * 需要嘅 GTFS 檔（全部純文字 CSV）。
     * `stop_times.txt` 刻意**唔落** —— 淨係要班次間隔，逐站時間一件都唔要。
     */
    private val FILES = listOf(
        "agency.txt",
        "routes.txt",
        "trips.txt",
        "calendar.txt",
        "calendar_dates.txt",
        "frequencies.txt"
    )

    /** 呢兩件冇咗都可以照做（淨係少咗營辦商／例外日） */
    private val OPTIONAL_FILES = setOf("agency.txt", "calendar_dates.txt")

    /** 來源標籤（UI 顯示用） */
    const val SOURCE_LABEL = "運輸署 GTFS 原檔"

    private const val NS = "tdhw"
    private const val KEY = "db"

    /** 單檔上限；超過就當壞資料 */
    private const val MAX_BYTES = 24 * 1024 * 1024

    /** 運輸署本身兩星期一更 */
    private const val STALE_MS = 14 * 24 * 60 * 60 * 1000L

    /** 路線方向數上限（費事序列化出個巨型 JSON） */
    private const val MAX_ENTRIES = 60000

    // ──────────────────────────── 資料結構 ────────────────────────────

    /** 一段官方班次：由 [start] 到 [end]（分鐘），每 [secs] 秒一班 */
    @Serializable
    data class Band(
        val start: Int = 0,
        val end: Int = 0,
        val secs: Int = 0
    )

    /** 一條 service（一個「邊日行」嘅版本） */
    @Serializable
    data class Svc(
        /** bit0..6 = 週一..週日（GTFS `calendar.txt` 嘅 monday..sunday） */
        val mask: Int = 0,
        val bands: List<Band> = emptyList(),
        /** `"yyyyMMdd"` → 1（加開）／2（停開） */
        val ex: Map<String, Int> = emptyMap()
    )

    /** 一條路線嘅一個方向 */
    @Serializable
    data class Entry(
        val route: String = "",
        val orig: String = "",
        val dest: String = "",
        val agency: String = "",
        val svcs: List<Svc> = emptyList()
    )

    @Serializable
    data class Db(
        val atMs: Long = 0L,
        /** 下載咗幾多 bytes（UI 顯示用） */
        val bytes: Long = 0L,
        /** 實際落到邊個語系目錄 */
        val base: String = "",
        val entries: List<Entry> = emptyList()
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
    fun hasData(): Boolean = (cached?.entries?.size ?: 0) > 0

    // ──────────────────────────── 讀寫 ────────────────────────────

    private fun legacyFile(context: Context) = File(context.filesDir, "hketa_td_headway.json")

    /** 由 **Rust store** 讀；Rust 話冇就睇返舊 JSON 檔，讀到會順手搬遷 */
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
     * 下載並更新班次資料（**淨係喺用家撳掣**嗰陣先落）。
     *
     * 失敗一律返 `ok = false` 並帶原因，**唔會影響原本功能**。
     */
    suspend fun refresh(context: Context, force: Boolean = false): HeadwayDb.UpdateResult {
        val cur = load(context)
        if (!force && cur != null && !isStale(cur)) {
            return HeadwayDb.UpdateResult(true, cur.entries.size, 0, "already-fresh")
        }
        var lastErr = "下載失敗"
        for (base in BASES) {
            val texts = LinkedHashMap<String, String>()
            var bytes = 0L
            var ok = true
            for (f in FILES) {
                val t = runCatching { Http.get(base + f) }.getOrNull()
                if (t.isNullOrBlank()) {
                    if (f in OPTIONAL_FILES) continue
                    ok = false
                    lastErr = "$f 下載失敗"
                    break
                }
                if (t.length > MAX_BYTES) {
                    ok = false
                    lastErr = "$f 過大"
                    break
                }
                texts[f] = t
                bytes += t.length
            }
            if (!ok) continue
            val db = runCatching { parse(texts) }.getOrNull()
            if (db == null || db.entries.isEmpty()) {
                lastErr = "解析唔到班次資料"
                continue
            }
            save(context, db.copy(bytes = bytes, base = base))
            return HeadwayDb.UpdateResult(true, db.entries.size, 0, "ok")
        }
        return HeadwayDb.UpdateResult(false, message = lastErr)
    }

    // ──────────────────────────── 解析 ────────────────────────────

    /**
     * 由 6 個 GTFS 檔砌出淨係需要嘅嘢。
     *
     * 刻意**逐個 streaming** 解析，中途產品（[freqByTrip]）用完即丟，
     * 費事一次過揸住幾十萬行喺記憶體。
     */
    fun parse(texts: Map<String, String>): Db {
        // 1. agency.txt：agency_id → 名（用嚟對營辦商）
        val agencyName = HashMap<String, String>()
        texts["agency.txt"]?.let { t ->
            eachRow(t) { m, r ->
                val id = col(m, r, "agency_id")
                if (id.isNotEmpty()) agencyName[id] = col(m, r, "agency_name")
            }
        }

        // 2. routes.txt：route_id → (路線號, 長名, agency_id)
        val routeInfo = HashMap<String, Triple<String, String, String>>()
        texts["routes.txt"]?.let { t ->
            eachRow(t) { m, r ->
                val id = col(m, r, "route_id")
                if (id.isEmpty()) return@eachRow
                val short = col(m, r, "route_short_name").ifBlank { col(m, r, "route_id") }
                routeInfo[id] = Triple(short, col(m, r, "route_long_name"), col(m, r, "agency_id"))
            }
        }
        if (routeInfo.isEmpty()) return Db()

        // 3. calendar.txt：service_id → 星期 mask
        val calMask = HashMap<String, Int>()
        val dayCols = listOf(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
        )
        texts["calendar.txt"]?.let { t ->
            eachRow(t) { m, r ->
                val id = col(m, r, "service_id")
                if (id.isEmpty()) return@eachRow
                var mask = 0
                dayCols.forEachIndexed { i, d -> if (col(m, r, d) == "1") mask = mask or (1 shl i) }
                calMask[id] = mask
            }
        }

        // 4. calendar_dates.txt：service_id → (yyyyMMdd → 1/2)
        val calDates = HashMap<String, HashMap<String, Int>>()
        texts["calendar_dates.txt"]?.let { t ->
            eachRow(t) { m, r ->
                val id = col(m, r, "service_id")
                val date = col(m, r, "date")
                val type = col(m, r, "exception_type").toIntOrNull()
                if (id.isEmpty() || date.isEmpty() || type == null) return@eachRow
                if (type != 1 && type != 2) return@eachRow
                val mm = calDates.getOrPut(id) { HashMap() }
                if (mm.size < 400) mm[date] = type
            }
        }

        // 5. frequencies.txt：trip_id → 時段（**有 end_time**，就係呢度贏 hkbus 嗰截）
        val freqByTrip = HashMap<String, ArrayList<Band>>()
        texts["frequencies.txt"]?.let { t ->
            eachRow(t) { m, r ->
                val trip = col(m, r, "trip_id")
                if (trip.isEmpty()) return@eachRow
                val s = parseTime(col(m, r, "start_time")) ?: return@eachRow
                val e = parseTime(col(m, r, "end_time")) ?: return@eachRow
                val secs = col(m, r, "headway_secs").toIntOrNull() ?: return@eachRow
                if (secs <= 0 || secs > 6 * 3600 || e <= s) return@eachRow
                val list = freqByTrip.getOrPut(trip) { ArrayList() }
                if (list.size < 32) list.add(Band(s, e, secs))
            }
        }
        if (freqByTrip.isEmpty()) return Db()

        // 6. trips.txt：淨係留「有班次資料」嘅 trip → route_id → service_id → bands
        val byRouteSvc = HashMap<String, HashMap<String, ArrayList<Band>>>()
        texts["trips.txt"]?.let { t ->
            eachRow(t) { m, r ->
                val trip = col(m, r, "trip_id")
                if (trip.isEmpty()) return@eachRow
                val bands = freqByTrip[trip] ?: return@eachRow
                val rid = col(m, r, "route_id")
                if (rid.isEmpty()) return@eachRow
                val svc = col(m, r, "service_id")
                val per = byRouteSvc.getOrPut(rid) { HashMap() }
                val list = per.getOrPut(svc) { ArrayList() }
                if (list.size < 64) list.addAll(bands)
            }
        }
        freqByTrip.clear()   // 中途產品，用完即丟

        // 7. 砌最終 entries
        val byKey = LinkedHashMap<String, Entry>()
        for ((rid, per) in byRouteSvc) {
            val info = routeInfo[rid] ?: continue
            val short = info.first.trim()
            if (short.isEmpty()) continue
            val svcs = ArrayList<Svc>()
            for ((svc, bands) in per) {
                val merged = mergeBands(bands)
                if (merged.isEmpty()) continue
                svcs.add(
                    Svc(
                        mask = calMask[svc] ?: 0,
                        bands = merged,
                        ex = calDates[svc] ?: emptyMap()
                    )
                )
            }
            if (svcs.isEmpty()) continue
            val pair = splitLongName(info.second)
            val agency = agencyName[info.third].orEmpty()
            val key = "$short|${pair.first}|${pair.second}|$agency"
            val prev = byKey[key]
            byKey[key] = if (prev == null) {
                Entry(short, pair.first, pair.second, agency, svcs)
            } else {
                Entry(short, pair.first, pair.second, agency, mergeSvcs(prev.svcs + svcs))
            }
        }

        val entries = byKey.values.take(MAX_ENTRIES)
        return Db(atMs = System.currentTimeMillis(), entries = entries)
    }

    // ──────────────────────────── 查詢 ────────────────────────────

    /**
     * 查呢條路線嘅官方班次。
     *
     * 因為有 `end_time`，返嘅 `Official` 會帶 `endKnown = true` ——
     * UI 可以講「已過官方表列服務時段」，唔使再講「唔代表冇車」。
     *
     * @return 搵到就返 `Official`；冇資料一律返 `null`（[HeadwayDb] 照舊頂上）
     */
    fun lookup(rd: RouteDef, nowMin: Int = HeadwayDb.nowMinutes()): HeadwayDb.Official? {
        val db = cached ?: return null
        val route = rd.route.trim()
        if (route.isEmpty()) return null

        val want = rd.op
        var cands = db.entries.filter {
            it.route.equals(route, ignoreCase = true) && agencyMatch(it.agency, want)
        }
        if (cands.isEmpty()) {
            cands = db.entries.filter { it.route.equals(route, ignoreCase = true) }
        }
        if (cands.isEmpty()) return null

        // ── 對方向：用中文總站名
        val dest = rd.dest.trim()
        val orig = rd.orig.trim()
        var boundMatched = false

        fun pick(field: String, get: (Entry) -> String): Entry? {
            if (field.isBlank()) return null
            return cands.firstOrNull { get(it).equals(field, ignoreCase = true) }
                ?: cands.firstOrNull {
                    val v = get(it)
                    v.isNotBlank() && (v.contains(field, true) || field.contains(v, true))
                }
        }

        val e = pick(dest) { it.dest }?.also { boundMatched = true }
            ?: pick(orig) { it.orig }?.also { boundMatched = true }
            ?: cands.first()

        // ── 今日行邊條 service：先看例外日，再看星期
        val todayIdx = todayIndex()
        val today = todayKey()
        val active = e.svcs.filter { s ->
            when (s.ex[today]) {
                1 -> true
                2 -> false
                else -> if (s.mask == 0) true else ((s.mask shr todayIdx) and 1) == 1
            }
        }
        // 今日官方表列冇班次 → 都要出到「表列時段」，所以退返用全部 service
        val closedToday = active.isEmpty()
        val useSvcs = if (closedToday) e.svcs else active

        val bands = mergeBands(useSvcs.flatMap { it.bands })
        if (bands.isEmpty()) return null

        val fromMin = bands.minOf { it.start }
        val toMin = bands.maxOf { it.end }
        // 而家落邊段：喺段內 → 未到就取第一 → 過咗就取最後
        val cur = bands.firstOrNull { nowMin >= it.start && nowMin < it.end }
            ?: bands.firstOrNull { nowMin < it.start }
            ?: bands.lastOrNull()
        val hwMin = if (closedToday || cur == null) null else (cur.secs + 59) / 60

        return HeadwayDb.Official(
            loMin = hwMin,
            hiMin = hwMin,
            from = hhmm(fromMin),
            to = hhmm(toMin),
            holiday = false,
            running = !closedToday && nowMin >= fromMin,
            pastTable = closedToday || nowMin > toMin,
            boundMatched = boundMatched,
            atMs = db.atMs,
            source = SOURCE_LABEL,
            endKnown = true,
            closedToday = closedToday
        )
    }

    // ──────────────────────────── 小工具 ────────────────────────────

    /** 今日係星期幾（0 = 週一 … 6 = 週日），對應 [Svc.mask] 嘅 bit */
    private fun todayIndex(): Int {
        val c = Calendar.getInstance()
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    /** 今日 `yyyyMMdd`（對應 GTFS `calendar_dates.txt` 嘅 `date`） */
    private fun todayKey(): String =
        runCatching { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) }.getOrNull().orEmpty()

    /** `"HH:mm:ss"`（GTFS 可以超過 24:00）→ 分鐘 */
    private fun parseTime(s: String): Int? {
        val p = s.trim().split(":")
        if (p.size < 2) return null
        val h = p[0].trim().toIntOrNull() ?: return null
        val m = p[1].trim().toIntOrNull() ?: return null
        if (h !in 0..47 || m !in 0..59) return null
        return h * 60 + m
    }

    /** 330 → `"05:30"` */
    private fun hhmm(min: Int): String {
        val h = (min / 60) % 48
        val m = min % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    /** 去掉重複、排序、設上限 */
    private fun mergeBands(src: List<Band>): List<Band> {
        val seen = LinkedHashMap<String, Band>()
        src.forEach { b ->
            val k = "${b.start}-${b.end}-${b.secs}"
            if (seen.size < 64 && !seen.containsKey(k)) seen[k] = b
        }
        return seen.values.sortedWith(compareBy<Band> { it.start }.thenBy { it.end })
    }

    /** 同一個「邊日行」嘅 service 併埋（union 班次時段） */
    private fun mergeSvcs(src: List<Svc>): List<Svc> {
        val out = LinkedHashMap<String, Svc>()
        src.forEach { s ->
            if (s.bands.isEmpty()) return@forEach
            val k = "${s.mask}|${s.ex.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }}"
            val prev = out[k]
            out[k] = if (prev == null) s else Svc(s.mask, mergeBands(prev.bands + s.bands), s.ex)
            if (out.size >= 24) return@forEach
        }
        return out.values.toList()
    }

    /**
     * `route_long_name` 拆做（起點, 終點）。
     *
     * GTFS 冇統一分隔符，常見 `>` ／ `-` ／ `to`，所以逐個試。
     * 拆唔到就兩邊都留空 —— 淨係會令方向對唔中（退化合併），**唔會無端端配錯**。
     */
    private fun splitLongName(longName: String): Pair<String, String> {
        val s = longName.trim()
        if (s.isEmpty()) return "" to ""
        val parts = when {
            s.contains(">") -> s.split(">")
            s.contains("＞") -> s.split("＞")     // 全形
            s.contains(" - ") -> s.split(" - ")
            s.contains(" to ") -> s.split(" to ")
            else -> null
        }
        if (parts == null || parts.size < 2) return "" to ""
        return parts.first().trim() to parts.last().trim()
    }

    /** 營辦商對唔對（TD 嘅 agency 名冇統一款式，所以逐個關鍵字試） */
    private fun agencyMatch(agency: String, op: Operator): Boolean {
        if (agency.isBlank()) return false
        val s = agency.lowercase(Locale.US)
        return when (op) {
            Operator.KMB -> s.contains("kmb") || s.contains("九龍") || s.contains("九巴")
            Operator.CTB -> s.contains("citybus") || s.contains("城巴")
            Operator.NLB -> s.contains("nlb") || s.contains("嶼巴") || s.contains("lantau")
            Operator.GMB -> s.contains("gmb") || s.contains("小巴") || s.contains("minibus")
            Operator.MTR_BUS, Operator.MTR_HR -> s.contains("mtr") || s.contains("港鐵")
            Operator.LRT -> s.contains("light rail") || s.contains("輕鐵") || s.contains("lrt")
        }
    }

    // ──────────────────────────── CSV ────────────────────────────

    /**
     * 逐行讀 CSV（**streaming**，唔會一次過砌個 List 出嚟）。
     *
     * 第一行當表頭，之後每行 call [onRow]。
     */
    private inline fun eachRow(
        text: String,
        crossinline onRow: (Map<String, Int>, List<String>) -> Unit
    ) {
        var header: Map<String, Int>? = null
        val reader = java.io.BufferedReader(StringReader(text), 1 shl 16)
        reader.useLines { seq ->
            seq.forEach { raw ->
                val line = raw.trim().trimStart('\uFEFF')
                if (line.isEmpty()) return@forEach
                val idx = header
                if (idx == null) {
                    val h = HashMap<String, Int>()
                    splitCsv(line).forEachIndexed { i, c ->
                        h[c.trim().trimStart('\uFEFF').lowercase(Locale.US)] = i
                    }
                    header = h
                    return@forEach
                }
                onRow(idx, splitCsv(line))
            }
        }
    }

    /** 按表頭**名**攞欄位（唔靠順序） */
    private fun col(idx: Map<String, Int>, row: List<String>, name: String): String =
        row.getOrNull(idx[name] ?: -1)?.trim().orEmpty()

    /** 拆一行 CSV（處理 `"..."` 引號同 `""` 轉義） */
    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(16)
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                when (c) {
                    '"' -> if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQuotes = false

                    else -> sb.append(c)
                }
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
