package com.hketa.app.data

import kotlinx.coroutines.CompletableDeferred

/**
 * # 加速層 —— 由 Rust 內核持有（Kotlin 版做回退）
 *
 * Rust 嗰層解決嘅係「索引放邊度、點樣查、點樣計」，但**網絡往返**呢一截
 * 要靠快取先慳到：每次入車站頁、每次切去班次表分頁、每次開主頁，
 * 都會重新打一次官方接口，就算 3 秒前先睇過都係咁 —— 呢個先係用戶最感受到嘅慢。
 *
 * ## 1. TTL 快取 ＋ stale-while-revalidate
 *
 * - 有新鮮嘅 → **即出**，連轉圈都唔使
 * - 得舊嘅 → **先出舊嘅**（畫面即刻有嘢），同時背景刷新
 * - 完全冇 → 先轉圈，正常抓取
 *
 * 呢個「先出舊嘅」係慳時間嘅關鍵：用戶入到畫面**即刻見到上一次嘅班次**，
 * 一秒後 quietly 變成最新，唔使對住轉圈等。
 *
 * ## 2. 點解而家放落 Rust（之前係純 Kotlin）
 *
 * 純 Kotlin 嗰陣，`Memo<T>` 持有嘅係**成個物件圖**：`List<EtaEntry>`、
 * `List<NearbyStop>`（入面仲包住 `StopDef` 同 `List<String>`）—— 即係快取入面嘅嘢
 * 一樣會畀 GC 掃到、一樣佔住 heap。
 *
 * 而家 payload 係一段**唔透明嘅字串**擺喺 native heap（見 `rust/src/speed.rs`），
 * TTL 判斷、踢走最舊、命中率統計全部由 Rust 做，JVM 淨係喺**要顯示嗰一刻**
 * 先 decode 砌物件。Rust 唔使知道 ETA／班次表／附近車站嘅形狀，所以加一種
 * 新嘅快取唔使改 Rust。
 *
 * **一定會回退**：`.so` 唔喺度／JNI 甩咗就自動行下面嘅 Kotlin 版 [Memo]，
 * 行為完全一樣（TTL、容量、命中率口徑都一樣）。
 *
 * ## 3. 單飛（仍然係 Kotlin）
 *
 * [SingleFlight] 要 `suspend`，係協程語義，搬落 Rust 要自己開執行緒同回呼，
 * 唔值得 —— 所以佢繼續留喺 Kotlin。佢防嘅係「快速撳幾下開幾個並行連線」，
 * 同快取放邊度無關。
 *
 * ## 4. TTL 點解係呢啲數
 *
 * | 嘢 | TTL | 點解 |
 * |---|---|---|
 * | ETA | 20 秒 | 官方 ETA 本身就係每 20–30 秒先變一次，再密冇意義 |
 * | 班次表 | 90 秒 | 要打 12 個站，成本高；切分頁返嚟應該即出 |
 * | 附近車站 | 45 秒 | 自動定位每 60 秒重整一次，45 秒唔會用到過期嘢 |
 * | 路線車站 | 10 分鐘 | 路線嘅站序幾乎唔會變 |
 */
object SpeedLayer {

    const val ETA_TTL_MS = 20_000L
    const val TIMETABLE_TTL_MS = 90_000L
    const val NEARBY_TTL_MS = 45_000L
    const val ROUTE_STOPS_TTL_MS = 600_000L

    /** 單飛：同一個站同時淨係一個網絡請求（值係 `etasFor` 嗰個 Pair） */
    val etaFlight = SingleFlight<Pair<List<EtaEntry>, String?>>()

    /** ETA 嘅 key：站 id ＋ 營辦商 ＋（有指定路線就加埋路線） */
    fun etaKeyOf(stop: StopDef, route: RouteDef?): String =
        "${stop.op.name}|${stop.id.trim()}|${route?.route.orEmpty().trim()}"
            .replace(Regex("[^A-Za-z0-9_|.-]"), "_")
            .take(160)

    /** 附近車站嘅 key：位置要量化，否則每次定位抖動都當 miss */
    fun nearbyKey(lat: Double, lon: Double, radiusMeters: Int): String =
        "%.4f,%.4f,%d".format(lat, lon, radiusMeters)

    /** 班次表／路線車站共用嘅路線 key */
    fun routeKey(op: Operator, route: String, bound: String, serviceType: String): String =
        "${op.name}|$route|$bound|$serviceType"

    // ------------------------------------------------------------ ETA

    /** 淨係攞未過期嘅 ETA（過期就 null） */
    fun etaGet(key: String): List<EtaEntry>? {
        if (RustKernel.speedAvailable) {
            val p = RustKernel.speedGet(RustKernel.NS_ETA, key, System.currentTimeMillis())
            if (p != null) return decodeEtas(p)
        }
        return ktEta.get(key)
    }

    /** 唔理過期照攞（用嚟先出舊嘅、再背景刷新） */
    fun etaPeek(key: String): List<EtaEntry>? {
        if (RustKernel.speedAvailable) {
            val p = RustKernel.speedPeek(RustKernel.NS_ETA, key)
            if (p != null) return decodeEtas(p)
        }
        return ktEta.peek(key)
    }

    /** 呢條 key 嘅 ETA 係幾時抓落嚟（冇就 0） */
    fun etaAt(key: String): Long {
        if (RustKernel.speedAvailable) {
            val v = RustKernel.speedAt(RustKernel.NS_ETA, key)
            if (v > 0L) return v
        }
        return ktEta.atOf(key)
    }

    fun etaPut(key: String, value: List<EtaEntry>) {
        val ok = RustKernel.speedAvailable &&
            RustKernel.speedPut(RustKernel.NS_ETA, key, encodeEtas(value), System.currentTimeMillis())
        if (!ok) ktEta.put(key, value)
    }

    // ------------------------------------------------------------ 班次表

    fun ttGet(key: String): RouteTimetable? {
        if (RustKernel.speedAvailable) {
            val p = RustKernel.speedGet(RustKernel.NS_TIMETABLE, key, System.currentTimeMillis())
            if (p != null) return decodeTimetable(p)
        }
        return ktTimetable.get(key)
    }

    fun ttPut(key: String, value: RouteTimetable) {
        val ok = RustKernel.speedAvailable &&
            RustKernel.speedPut(
                RustKernel.NS_TIMETABLE, key, encodeTimetable(value), System.currentTimeMillis()
            )
        if (!ok) ktTimetable.put(key, value)
    }

    // ------------------------------------------------------------ 附近車站

    fun nearGet(key: String): List<NearbyStop>? {
        if (RustKernel.speedAvailable) {
            val p = RustKernel.speedGet(RustKernel.NS_NEARBY, key, System.currentTimeMillis())
            if (p != null) return decodeNearby(p)
        }
        return ktNearby.get(key)
    }

    fun nearPut(key: String, value: List<NearbyStop>) {
        val ok = RustKernel.speedAvailable &&
            RustKernel.speedPut(
                RustKernel.NS_NEARBY, key, encodeNearby(value), System.currentTimeMillis()
            )
        if (!ok) ktNearby.put(key, value)
    }

    // ------------------------------------------------------------ 路線車站

    fun rsGet(key: String): List<StopWithSeq>? {
        if (RustKernel.speedAvailable) {
            val p = RustKernel.speedGet(RustKernel.NS_ROUTE_STOPS, key, System.currentTimeMillis())
            if (p != null) return decodeRouteStops(p)
        }
        return ktRouteStops.get(key)
    }

    fun rsPut(key: String, value: List<StopWithSeq>) {
        val ok = RustKernel.speedAvailable &&
            RustKernel.speedPut(
                RustKernel.NS_ROUTE_STOPS, key, encodeRouteStops(value), System.currentTimeMillis()
            )
        if (!ok) ktRouteStops.put(key, value)
    }

    // ------------------------------------------------------------ 管理

    fun clearAll() {
        RustKernel.speedClearAll()
        ktEta.clear()
        ktTimetable.clear()
        ktNearby.clear()
        ktRouteStops.clear()
    }

    /**
     * 命中率（設定頁會顯示，等我同用戶一眼睇到係咪真係生效）。
     *
     * 口徑同 Rust 版一致：淨係計 [etaGet]（即 ETA 呢個 namespace），
     * 而 [etaPeek] 唔計 —— 佢唔係命中，只係借用舊資料頂住畫面。
     */
    fun stats(): Pair<Long, Long> =
        if (RustKernel.speedAvailable) {
            RustKernel.speedHits(RustKernel.NS_ETA) to RustKernel.speedMisses(RustKernel.NS_ETA)
        } else {
            ktEta.hits to ktEta.misses
        }

    /** 加速層由邊度管（設定頁顯示） */
    fun backendLabel(): String = if (RustKernel.speedAvailable) "Rust / kernel" else "Kotlin / fallback"

    // ------------------------------------------------------------ 回退用嘅 Kotlin Memo

    private val ktEta = Memo<List<EtaEntry>>(ETA_TTL_MS, max = 128)
    private val ktTimetable = Memo<RouteTimetable>(TIMETABLE_TTL_MS, max = 32)
    private val ktNearby = Memo<List<NearbyStop>>(NEARBY_TTL_MS, max = 16)
    private val ktRouteStops = Memo<List<StopWithSeq>>(ROUTE_STOPS_TTL_MS, max = 64)

    // ------------------------------------------------------------ 編碼（跨邊界用）

    private const val SEP = '\u001F'
    private const val SEP2 = '\u001E'
    private const val PAIR = ';'
    private const val GS = '\u001D'
    private const val FS = '\u001C'
    private const val NO_MIN = "N"

    /** ETA：每筆 `op\u001Eroute\u001Edest\u001Eminutes\u001Eclock\u001Eremark` */
    private fun encodeEtas(list: List<EtaEntry>): String =
        list.joinToString(SEP.toString()) { e ->
            listOf(
                e.op.name,
                e.route,
                e.dest,
                e.minutes?.toString() ?: NO_MIN,
                e.clock,
                e.remark
            ).joinToString(SEP2.toString())
        }

    private fun decodeEtas(payload: String): List<EtaEntry> {
        if (payload.isEmpty()) return emptyList()
        return payload.split(SEP).mapNotNull { row ->
            val f = row.split(SEP2)
            if (f.size < 6) return@mapNotNull null
            EtaEntry(
                op = runCatching { Operator.valueOf(f[0]) }.getOrDefault(Operator.KMB),
                route = f[1],
                dest = f[2],
                minutes = if (f[3] == NO_MIN) null else f[3].toIntOrNull(),
                clock = f[4],
                remark = f[5]
            )
        }
    }

    /** 附近車站：每筆 `op\u001Eid\u001Ename\u001EnameEn\u001Elat\u001Elon\u001Edist\u001Eroutes;routes` */
    private fun encodeNearby(list: List<NearbyStop>): String =
        list.joinToString(SEP.toString()) { n ->
            listOf(
                n.stop.op.name,
                n.stop.id,
                n.stop.name,
                n.stop.nameEn,
                n.stop.lat.toString(),
                n.stop.lon.toString(),
                n.distanceMeters.toString(),
                n.routes.joinToString(PAIR.toString()),
                n.ops.joinToString(PAIR.toString()) { it.name }
            ).joinToString(SEP2.toString())
        }

    private fun decodeNearby(payload: String): List<NearbyStop> {
        if (payload.isEmpty()) return emptyList()
        return payload.split(SEP).mapNotNull { row ->
            val f = row.split(SEP2)
            if (f.size < 8) return@mapNotNull null
            NearbyStop(
                stop = StopDef(
                    op = runCatching { Operator.valueOf(f[0]) }.getOrDefault(Operator.KMB),
                    id = f[1],
                    name = f[2],
                    nameEn = f[3],
                    lat = f[4].toDoubleOrNull() ?: 0.0,
                    lon = f[5].toDoubleOrNull() ?: 0.0
                ),
                distanceMeters = f[6].toIntOrNull() ?: 0,
                routes = if (f[7].isEmpty()) emptyList() else f[7].split(PAIR),
                // ops 係顯示用嘅補充；舊 payload 冇呢格就留空（唔會崩）
                ops = if (f.size < 9 || f[8].isEmpty()) emptyList()
                      else f[8].split(PAIR).mapNotNull {
                          runCatching { Operator.valueOf(it) }.getOrNull()
                      }
            )
        }
    }

    /** 路線車站：每筆 `seq\u001Eop\u001Eid\u001Ename\u001EnameEn\u001Elat\u001Elon` */
    private fun encodeRouteStops(list: List<StopWithSeq>): String =
        list.joinToString(SEP.toString()) { s ->
            listOf(
                s.seq.toString(),
                s.stop.op.name,
                s.stop.id,
                s.stop.name,
                s.stop.nameEn,
                s.stop.lat.toString(),
                s.stop.lon.toString()
            ).joinToString(SEP2.toString())
        }

    private fun decodeRouteStops(payload: String): List<StopWithSeq> {
        if (payload.isEmpty()) return emptyList()
        return payload.split(SEP).mapNotNull { row ->
            val f = row.split(SEP2)
            if (f.size < 7) return@mapNotNull null
            StopWithSeq(
                seq = f[0].toIntOrNull() ?: 0,
                stop = StopDef(
                    op = runCatching { Operator.valueOf(f[1]) }.getOrDefault(Operator.KMB),
                    id = f[2],
                    name = f[3],
                    nameEn = f[4],
                    lat = f[5].toDoubleOrNull() ?: 0.0,
                    lon = f[6].toDoubleOrNull() ?: 0.0
                )
            )
        }
    }

    /**
     * 班次表：
     * `headway\u001Ffirst\u001Flast\u001Fstate\u001FstopNames;…\u001Ftrips`
     *
     * 每班車用 `\u001D` 分隔，格入面分鐘／時刻用 `\u001C`。
     */
    private fun encodeTimetable(t: RouteTimetable): String {
        val trips = t.trips.joinToString(GS.toString()) { trip ->
            trip.seq.toString() + SEP2 + trip.cells.joinToString(PAIR.toString()) { c ->
                (c.minutes?.toString() ?: NO_MIN) + FS + c.clock
            }
        }
        return listOf(
            t.headwayMin?.toString() ?: NO_MIN,
            t.firstObserved.orEmpty(),
            t.lastObserved.orEmpty(),
            t.state,
            t.stopNames.joinToString(PAIR.toString()),
            trips
        ).joinToString(SEP.toString())
    }

    private fun decodeTimetable(payload: String): RouteTimetable {
        val f = payload.split(SEP)
        if (f.size < 6) return RouteTimetable()
        val trips = if (f[5].isEmpty()) emptyList() else f[5].split(GS).mapNotNull { row ->
            val q = row.split(SEP2)
            if (q.size < 2) return@mapNotNull null
            val seq = q[0].toIntOrNull() ?: return@mapNotNull null
            val cells = if (q[1].isEmpty()) emptyList() else q[1].split(PAIR).map { p ->
                val c = p.split(FS)
                TripCell(
                    stopName = "",
                    minutes = if (c.getOrNull(0).orEmpty() == NO_MIN) null
                    else c.getOrNull(0)?.toIntOrNull(),
                    clock = c.getOrNull(1).orEmpty()
                )
            }
            Trip(seq = seq, cells = cells)
        }
        return RouteTimetable(
            trips = trips,
            headwayMin = if (f[0] == NO_MIN || f[0].isBlank()) null else f[0].toIntOrNull(),
            firstObserved = f[1].ifBlank { null },
            lastObserved = f[2].ifBlank { null },
            state = f[3],
            stopNames = if (f[4].isEmpty()) emptyList() else f[4].split(PAIR)
        )
    }
}

/**
 * TTL 快取（**回退用** —— Rust 喺度嗰陣唔會用到）。
 *
 * - [get]：淨係返**未過期**嘅；過期當 miss（並計數）
 * - [peek]：唔理過期照返 —— 用嚟做 stale-while-revalidate
 *
 * 超過 [max] 自動踢走最舊（[trimLocked]）—— **唔係** LRU，因為要同 Rust 版一致；
 * 全部 `@Synchronized`，因為附近車站會喺 `Dispatchers.IO` 入面讀寫。
 */
class Memo<T>(private val ttlMs: Long, private val max: Int = 64) {

    private class Box<T>(val value: T, val atMs: Long)

    // 插入序（accessOrder = false）：踢走係按 [Box.atMs]，唔係最近讀取時間，
    // 所以唔可以用 access-order —— 否則同 Rust 版嘅踢走次序會唔同。
    private val map = LinkedHashMap<String, Box<T>>(32, 0.75f, false)

    @Volatile
    var hits: Long = 0L
        private set

    @Volatile
    var misses: Long = 0L
        private set

    @Synchronized
    fun get(key: String, nowMs: Long = System.currentTimeMillis()): T? {
        val b = map[key]
        return if (b != null && nowMs - b.atMs <= ttlMs) {
            hits++
            b.value
        } else {
            misses++
            null
        }
    }

    /** 唔理過期照返（用嚟先出舊嘅、再背景刷新） */
    @Synchronized
    fun peek(key: String): T? = map[key]?.value

    /** 呢個 key 嘅資料係幾時抓落嚟（用唔到就 0） */
    @Synchronized
    fun atOf(key: String): Long = map[key]?.atMs ?: 0L

    @Synchronized
    fun put(key: String, value: T, nowMs: Long = System.currentTimeMillis()) {
        map[key] = Box(value, nowMs)
        trimLocked()
    }

    @Synchronized
    fun remove(key: String) {
        map.remove(key)
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun size(): Int = map.size

    /**
     * 踢走最舊嗰個 —— **要同 Rust 版逐位一樣**：`rust/src/speed.rs` 嘅 `put()`
     * 係用 `min_by_key(|(_, s)| s.at_ms)` 踢最舊，即係**寫入時間**而唔係
     * 最近讀取時間。所以呢度唔可以用 `LinkedHashMap` 嘅 access-order 去踢
     * （嗰個係 LRU），否則「有冇 `.so`」會改變踢走邊個。
     */
    private fun trimLocked() {
        while (map.size > max) {
            val oldest = map.entries.minByOrNull { it.value.atMs }?.key ?: return
            map.remove(oldest)
        }
    }
}

/**
 * 單飛：同一個 key 同時淨係跑一次 [block]，其餘嘅一齊等同一個結果。
 *
 * 快速撳幾下／重組觸發多次請求嗰陣，唔會再開幾個並行連線打同一個站。
 * leader 完成（成功定失敗）之後會把自己由表入面移除，所以下次會重新開一個。
 *
 * 呢個留喺 Kotlin 係刻意嘅：要 `suspend` 語義，搬落 Rust 要自己開執行緒同回呼。
 */
class SingleFlight<T> {

    private val lock = Any()
    private val inflight = HashMap<String, CompletableDeferred<T>>()

    suspend fun join(key: String, block: suspend () -> T): T {
        val mine = CompletableDeferred<T>()

        val existing = synchronized(lock) {
            val cur = inflight[key]
            if (cur != null) {
                cur
            } else {
                inflight[key] = mine
                null
            }
        }

        // 已經有人跑緊 → 一齊等佢嘅結果，唔會再開第二個請求
        if (existing != null) return existing.await()

        return try {
            val v = block()
            mine.complete(v)
            v
        } catch (t: Throwable) {
            mine.completeExceptionally(t)
            throw t
        } finally {
            synchronized(lock) {
                if (inflight[key] === mine) inflight.remove(key)
            }
        }
    }
}
