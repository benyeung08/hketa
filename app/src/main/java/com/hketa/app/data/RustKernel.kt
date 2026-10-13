package com.hketa.app.data

/**
 * **HKETA 嘅 Rust 內核 —— Kotlin 側唯一入口。**
 *
 * 一個 `.so`（`libhketa_store.so`）三件事，全部由呢個 object 對外：
 *
 * | 模組（Rust 側） | 負責 | 呢度嘅 API |
 * |---|---|---|
 * | `rust/src/index.rs` | **儲存**（緊湊二進制 ＋ 字串去重）＋ **常駐記憶體**（native heap） | `nativeIndex*` |
 * | `rust/src/kernel.rs` | **計數**（取樣、班次表、診斷、ETA 合併） | `sampleIndices` 等 |
 * | `rust/src/speed.rs` | **TTL 快取**（payload 放 native heap） | `speed*` |
 *
 * 「內存與儲存」以前係另一個 object（`RustIndex`），而家**併入內核**：
 * 共用同一個 `System.loadLibrary`、同一個 [available] 判斷，JNI 亦全部改咗名做
 * `Java_com_hketa_app_data_RustKernel_nativeIndex*`。
 *
 * 內核負責嘅係**計算**：
 *
 * | 邏輯 | 說明 |
 * |---|---|
 * | `sampleIndices` | 沿線取樣最多 N 個站（頭、尾 ＋ 均勻中間） |
 * | `buildTimetable` | 由「每站一列 ETA」砌班次表 ＋ 計班次間隔 |
 * | `diagnose` | 路線狀況（全線冇班次／部分站冇／間隔偏大） |
 * | `mergeEta` | ETA 合併去重 ＋ 排序 |
 * | `normalizeRoute` | 路線號正規化 |
 *
 * ## 點解要搬
 *
 * 呢幾樣嘢共同嘅特徵係 **輸入大、輸出細、邏輯純**：班次表要食
 * 12 個站 × 每站 6 班 = 72 筆，淨係輸出最多 6 班。呢類「食大吐細」
 * 嘅純邏輯最啱放喺 native 度做 —— JVM 側連中轉物件都唔使起。
 *
 * ## ⚠️ 最重要：一定有回退
 *
 * **每一個函數都有逐行對應嘅純 Kotlin 實作**，而且算法**刻意寫到逐位一樣**
 * （例如取樣用 `toInt()` 截斷而唔係四捨五入）。所以：
 *
 * * `.so` 唔喺度 → 行 Kotlin 版 → **結果完全一樣**
 * * JNI call 甩 exception → `runCatching` 捉住 → 行 Kotlin 版
 *
 * 呢個「兩邊結果必定相同」係設計前提，唔係巧合 —— 所以改動任何一邊，
 * **另一邊都要跟住改**，否則就會出現「有冇 Rust 行為唔同」呢類最難 debug 嘅 bug。
 *
 * ## 跨邊界格式
 *
 * 跨邊界同索引一樣**唔用 JSON**。字段之間用 `\u001F`，字段內部用 `\u001E`，
 * 同一欄入面多個 pair 用 `;`。分鐘數「冇資料」編碼做 `"N"`。
 */
object RustKernel {

    /** 字段分隔（unit separator） */
    private const val SEP = '\u001F'
    /** 字段內部嘅 pair 分隔（record separator） */
    private const val SEP2 = '\u001E'
    /** 同一欄入面多個 pair 嘅分隔 */
    private const val PAIR = ';'
    /** 分鐘數「冇資料」嘅編碼 */
    private const val NO_MIN = "N"

    private var loaded = false
    private var tried = false

    /** `.so` 係咪載到；載唔到所有函數都會行純 Kotlin 版 */
    val available: Boolean
        get() {
            if (!tried) {
                tried = true
                loaded = runCatching {
                    System.loadLibrary("hketa_store")
                    // 叫一次最簡單嘅 native 方法，確認個 library 真係啱版本
                    nativeIndexVersion() >= 1
                }.getOrDefault(false)
            }
            return loaded
        }

    /** kernel 版本；`.so` 唔喺度會係 0 */
    val version: Int
        get() = if (!available) 0 else
            runCatching { nativeKernelVersion() }.getOrDefault(0)

    // ---------------------------------------------------------------- 取樣

    /**
     * 由 `n` 個站入面取樣最多 `max` 個：頭、尾 ＋ 均勻中間。
     *
     * 同 Rust 版**逐位一樣**（`(i * step).toInt()` 係截斷唔係四捨五入）。
     */
    fun sampleIndices(n: Int, max: Int): List<Int> {
        if (!available) return sampleIndicesKt(n, max)
        val arr = runCatching { nativeSampleIndices(n, max) }.getOrNull()
        // Rust 版有去重，所以長度可能細過 max；只要唔係空就用佢
        if (arr == null || arr.isEmpty()) return sampleIndicesKt(n, max)
        return arr.toList()
    }

    private fun sampleIndicesKt(n: Int, max: Int): List<Int> {
        if (n <= 0 || max <= 0) return emptyList()
        if (n <= max) return (0 until n).toList()
        val step = (n - 1).toDouble() / (max - 1).toDouble()
        val out = ArrayList<Int>(max)
        for (i in 0 until max) {
            val idx = (i * step).toInt().coerceIn(0, n - 1)
            if (idx !in out) out.add(idx)
        }
        return out
    }

    // ---------------------------------------------------------------- 班次表

    /** 砌出嚟嘅班次表 */
    data class TimetableResult(
        val trips: List<Trip>,
        val headwayMin: Int?
    )

    /**
     * 由「每站一列 ETA」砌班次表。
     *
     * 官方 ETA 本身已按到站先後排好，所以**第 N 項就係第 N 班車** ——
     * 呢個係真實資料，唔係估算。
     *
     * 每欄格式：`"12\u001E21:10;15\u001E21:15"`（分鐘數冇資料用 `N`）。
     *
     * @return null 代表要用 Kotlin 版（.so 唔喺度／JNI 甩咗）
     */
    fun buildTimetable(cols: List<String>, maxTrips: Int): TimetableResult? {
        if (!available || maxTrips <= 0 || cols.isEmpty()) return null
        val rows = runCatching { nativeBuildTimetable(cols.toTypedArray(), maxTrips) }
            .getOrNull() ?: return null
        if (rows.isEmpty()) return null

        var headway: Int? = null
        val trips = ArrayList<Trip>(rows.size)
        for (row in rows) {
            val f = row.split(SEP)
            when {
                f.isEmpty() -> continue
                f[0] == "H" -> {
                    if (f.size >= 2) headway = if (f[1] == NO_MIN) null else f[1].toIntOrNull()
                }
                f[0] == "T" -> {
                    if (f.size < 3) continue
                    val seq = f[1].toIntOrNull() ?: continue
                    val cells = if (f[2].isEmpty()) emptyList() else f[2].split(PAIR).map { p ->
                        val q = p.split(SEP2)
                        val m = q.getOrNull(0).orEmpty()
                        TripCell(
                            stopName = "",
                            minutes = if (m == NO_MIN || m.isBlank()) null else m.toIntOrNull(),
                            clock = q.getOrNull(1).orEmpty()
                        )
                    }
                    trips.add(Trip(seq = seq, cells = cells))
                }
            }
        }
        return TimetableResult(trips = trips, headwayMin = headway)
    }

    /** 把一格編碼做跨邊界格式 */
    fun encodeCell(minutes: Int?, clock: String): String {
        val m = minutes?.toString() ?: NO_MIN
        return "$m$SEP2$clock"
    }

    /** 把一個站嘅 ETA 清單編碼做一欄 */
    fun encodeCol(etas: List<Pair<Int?, String>>): String =
        etas.joinToString(PAIR.toString()) { (m, c) -> encodeCell(m, c) }

    // ---------------------------------------------------------------- 路線狀況

    /**
     * 路線狀況診斷
     *
     * ```text
     * 0 = 正常
     * 1 = 全線冇班次（可能暫停／已過尾班車）
     * 2 = 部分車站冇班次資料
     * 3 = 班次間隔偏大（>= 30 分鐘）
     * 4 = 班次極疏（>= 60 分鐘）
     * ```
     *
     * 順序係刻意嘅：全線冇嘢最嚴重，其次「有站冇資料」（對用戶最直接），
     * 最後先係間隔偏大（可能只係正常嘅疏班次路線）。
     */
    fun diagnose(total: Int, noData: Int, maxGap: Int): Int {
        if (!available) return diagnoseKt(total, noData, maxGap)
        val v = runCatching { nativeDiagnose(total, noData, maxGap) }.getOrNull()
        return v ?: diagnoseKt(total, noData, maxGap)
    }

    private fun diagnoseKt(total: Int, noData: Int, maxGap: Int): Int {
        if (total == 0) return 0
        if (noData >= total) return 1
        if (noData > 0) return 2
        if (maxGap >= 60) return 4
        if (maxGap >= 30) return 3
        return 0
    }

    // ---------------------------------------------------------------- ETA 合併

    /** 一筆 ETA 嘅跨邊界編碼 */
    fun encodeEta(minutes: Int?, clock: String, route: String, dest: String): String {
        val m = minutes?.toString() ?: NO_MIN
        return "$m$SEP2$clock$SEP2$route$SEP2$dest"
    }

    /**
     * ETA 合併去重 ＋ 排序（分鐘數昇冪，冇資料排最後）。
     *
     * @return null 代表要用 Kotlin 版
     */
    fun mergeEta(rows: List<String>, cap: Int): List<String>? {
        if (!available || cap <= 0 || rows.isEmpty()) return null
        val out = runCatching { nativeMergeEta(rows.toTypedArray(), cap) }.getOrNull()
        return out?.toList()
    }

    // ---------------------------------------------------------------- 路線號

    /** 路線號正規化：去前後空格 ＋ 轉大寫 */
    fun normalizeRoute(q: String): String {
        if (!available) return q.trim().uppercase()
        val out = runCatching { nativeNormalizeRoute(q) }.getOrNull()
        return out?.firstOrNull() ?: q.trim().uppercase()
    }

    // ---------------------------------------------------------------- 索引：儲存 ＋ 常駐記憶體
    //
    // 「內存與儲存」而家都係**內核**嘅一部分（Rust 側喺 `rust/src/index.rs`）：
    //
    // * **儲存**：緊湊二進制格式 ＋ 字串去重，7.20 MB JSON → 1.04 MB
    // * **常駐記憶體**：索引放喺 native heap（`Box::leak`），Kotlin 淨係揸住一個 `Long`
    // * **查詢喺 Rust 側做**：淨係把「要顯示嗰幾十筆」傳過界
    //
    // 以前呢截係另一個 object（`RustIndex`），而家併入內核 —— 共用同一個
    // `System.loadLibrary`、同一個 `available` 判斷，Kotlin 側入口得一個。

    /** 由碟上嘅二進制索引建一個「一次性」handle；用完要 [nativeIndexFree] */
    external fun nativeIndexLoad(path: String): Long

    /** 由 JSON 建一個「一次性」handle（第一次建索引嗰陣用） */
    external fun nativeIndexFromJson(json: String): Long

    /** 寫落碟（原子寫） */
    external fun nativeIndexSave(handle: Long, path: String): Boolean

    /** 釋放一次性 handle */
    external fun nativeIndexFree(handle: Long)

    /** 索引格式版本；`.so` 唔喺度會係 0 */
    external fun nativeIndexVersion(): Int

    /** `[routes, stops, routeStops, strings]` */
    external fun nativeIndexStats(handle: Long): IntArray?

    external fun nativeIndexBuiltAt(handle: Long): Long

    /** 去重後嘅字串表 —— Kotlin 側當做共用嘅 `String` 池 */
    external fun nativeIndexStrings(handle: Long): Array<String>?

    /** 每條路線 9 個 int：`op, bound, route, routeId, orig, origEn, dest, destEn, serviceType` */
    external fun nativeIndexRoutes(handle: Long): IntArray?

    /** 每個站 4 個 int：`op, id, name, nameEn` */
    external fun nativeIndexStops(handle: Long): IntArray?

    /** `lat, lon` 交替 */
    external fun nativeIndexStopsLatLon(handle: Long): DoubleArray?

    /** 每筆 7 個 int：`op, bound, seq, route, serviceType, stopId, routeId` */
    external fun nativeIndexRouteStops(handle: Long): IntArray?

    /** 半徑內嘅車站，按距離排。每項 2 個 int：`stop 下標, 距離米` */
    external fun nativeIndexNearby(handle: Long, lat: Double, lon: Double, radiusM: Double): IntArray?

    /** 某個站有邊啲路線 —— 返回字串表索引 */
    external fun nativeIndexRoutesAtStop(handle: Long, op: String, stopId: String): IntArray?

    // ---------- 常駐（Rust 完全擁有記憶體）----------

    /** 由碟上嘅二進制索引開一個常駐 handle；失敗返回 0 */
    external fun nativeIndexOpen(path: String): Long

    /** 由 JSON 建一個常駐 handle（第一次建索引嗰陣用） */
    external fun nativeIndexOpenJson(json: String): Long

    /** 收返 handle，並釋放 native heap 上面嘅索引 */
    external fun nativeIndexClose(handle: Long)

    /** 常駐索引數量（診斷用，正常係 1） */
    external fun nativeIndexResidentCount(): Int

    /** 把常駐索引寫落碟（原子寫） */
    external fun nativeIndexSaveResident(handle: Long, path: String): Boolean

    external fun nativeIndexStatsResident(handle: Long): IntArray?

    external fun nativeIndexBuiltAtResident(handle: Long): Long

    /** 搵路線。每行：`op|route|bound|serviceType|routeId|orig|dest` */
    external fun nativeIndexSearchRoutes(handle: Long, query: String, limit: Int): Array<String>?

    /** 搵車站。每行：`op|id|name|nameEn|lat|lon|route1,route2,…` */
    external fun nativeIndexSearchStops(handle: Long, query: String, limit: Int): Array<String>?

    /** 某條路線嘅車站（按 seq）。每行：`op|stopId|seq|name|nameEn|lat|lon` */
    external fun nativeIndexRouteStopsRows(
        handle: Long, op: String, route: String, bound: String, serviceType: String
    ): Array<String>?

    /** 半徑內嘅車站。每行：`op|id|name|nameEn|lat|lon|distM` */
    external fun nativeIndexNearbyRows(
        handle: Long, lat: Double, lon: Double, radiusM: Double, limit: Int
    ): Array<String>?

    // ---------- 高階封裝 ----------

    /**
     * 由 Rust handle 砌返一個 `IndexData`（一次性 handle 用）。
     *
     * 重點：**所有字串都係由 `strings` 陣列攞** —— 所以同一個站名出現
     * 幾百次，JVM 入面都係同一個 `String` 實例，唔會開幾百份。
     */
    fun toIndexData(handle: Long): IndexData? {
        if (handle == 0L) return null
        return runCatching {
            val strings = nativeIndexStrings(handle) ?: return null
            val routesRaw = nativeIndexRoutes(handle) ?: return null
            val stopsRaw = nativeIndexStops(handle) ?: return null
            val latLon = nativeIndexStopsLatLon(handle) ?: return null
            val rsRaw = nativeIndexRouteStops(handle) ?: return null
            val builtAt = nativeIndexBuiltAt(handle)
            val ops = Operator.values()

            fun str(i: Int): String = if (i in strings.indices) strings[i] else ""

            val routes = ArrayList<RouteDef>(routesRaw.size / 9)
            var i = 0
            while (i + 8 < routesRaw.size) {
                val op = ops.getOrNull(routesRaw[i]) ?: Operator.KMB
                val bound = routesRaw[i + 1]
                routes.add(
                    RouteDef(
                        op = op,
                        route = str(routesRaw[i + 2]),
                        bound = boundStr(bound),
                        serviceType = str(routesRaw[i + 8]),
                        routeId = str(routesRaw[i + 3]),
                        orig = str(routesRaw[i + 4]),
                        dest = str(routesRaw[i + 6])
                    )
                )
                i += 9
            }

            val stops = ArrayList<StopDef>(stopsRaw.size / 4)
            i = 0
            var j = 0
            while (i + 3 < stopsRaw.size) {
                val lat = if (j < latLon.size) latLon[j] else 0.0
                val lon = if (j + 1 < latLon.size) latLon[j + 1] else 0.0
                stops.add(
                    StopDef(
                        op = ops.getOrNull(stopsRaw[i]) ?: Operator.KMB,
                        id = str(stopsRaw[i + 1]),
                        name = str(stopsRaw[i + 2]),
                        nameEn = str(stopsRaw[i + 3]),
                        lat = lat,
                        lon = lon
                    )
                )
                i += 4
                j += 2
            }

            val routeStops = ArrayList<RouteStopDef>(rsRaw.size / 7)
            i = 0
            while (i + 6 < rsRaw.size) {
                routeStops.add(
                    RouteStopDef(
                        op = ops.getOrNull(rsRaw[i]) ?: Operator.KMB,
                        route = str(rsRaw[i + 3]),
                        bound = boundStr(rsRaw[i + 1]),
                        serviceType = str(rsRaw[i + 4]),
                        stopId = str(rsRaw[i + 5]),
                        seq = rsRaw[i + 2],
                        routeId = str(rsRaw[i + 6])
                    )
                )
                i += 7
            }

            IndexData(
                version = 2,
                builtAt = if (builtAt != 0L) builtAt else System.currentTimeMillis(),
                routes = routes,
                stops = stops,
                routeStops = routeStops
            )
        }.getOrNull()
    }

    private fun boundStr(b: Int): String = when (b) {
        1 -> "O"
        2 -> "I"
        else -> ""
    }

    /** Rust 搵車站／附近車站嘅一行結果 */
    data class StopRow(
        val stop: StopDef,
        /** 該站停靠嘅路線號（已排序） */
        val routes: List<String>,
        /** 距離（米）；`nearbyRows` 先會有，否則 0 */
        val distM: Int = 0
    )

    /** Rust 搵路線嘅結果（已經係 `RouteDef`，可以直接畀 UI） */
    fun searchRoutes(handle: Long, query: String, limit: Int): List<RouteDef> {
        if (handle == 0L || query.isBlank() || !available) return emptyList()
        val rows = runCatching { nativeIndexSearchRoutes(handle, query.trim(), limit) }
            .getOrNull() ?: return emptyList()
        val out = ArrayList<RouteDef>(rows.size)
        for (row in rows) {
            val f = row.split(SEP)
            if (f.size < 7) continue
            val op = runCatching { Operator.valueOf(f[0]) }.getOrNull() ?: continue
            out.add(
                RouteDef(
                    op = op,
                    route = f[1],
                    bound = f[2],
                    serviceType = f[3],
                    routeId = f[4],
                    orig = f[5],
                    dest = f[6]
                )
            )
        }
        return out
    }

    /** Rust 搵車站嘅結果 */
    fun searchStops(handle: Long, query: String, limit: Int): List<StopRow> {
        if (handle == 0L || query.isBlank() || !available) return emptyList()
        val rows = runCatching { nativeIndexSearchStops(handle, query.trim(), limit) }
            .getOrNull() ?: return emptyList()
        val out = ArrayList<StopRow>(rows.size)
        for (row in rows) {
            val f = row.split(SEP)
            if (f.size < 7) continue
            val op = runCatching { Operator.valueOf(f[0]) }.getOrNull() ?: continue
            val routes = if (f[6].isEmpty()) emptyList() else f[6].split(",")
            out.add(
                StopRow(
                    stop = StopDef(
                        op = op,
                        id = f[1],
                        name = f[2],
                        nameEn = f[3],
                        lat = f[4].toDoubleOrNull() ?: 0.0,
                        lon = f[5].toDoubleOrNull() ?: 0.0
                    ),
                    routes = routes
                )
            )
        }
        return out
    }

    /** 某條路線（某方向）嘅車站，按 `seq` 排 */
    fun routeStops(
        handle: Long, op: Operator, route: String, bound: String, serviceType: String
    ): List<Pair<Int, StopDef>> {
        if (handle == 0L || !available) return emptyList()
        val rows = runCatching {
            nativeIndexRouteStopsRows(handle, op.name, route, bound, serviceType)
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<Int, StopDef>>(rows.size)
        for (row in rows) {
            val f = row.split(SEP)
            if (f.size < 7) continue
            val o = runCatching { Operator.valueOf(f[0]) }.getOrNull() ?: continue
            out.add(
                (f[2].toIntOrNull() ?: 0) to StopDef(
                    op = o,
                    id = f[1],
                    name = f[3],
                    nameEn = f[4],
                    lat = f[5].toDoubleOrNull() ?: 0.0,
                    lon = f[6].toDoubleOrNull() ?: 0.0
                )
            )
        }
        return out
    }

    /** 半徑內嘅車站，按距離排 */
    fun nearbyStops(
        handle: Long, lat: Double, lon: Double, radiusM: Double, limit: Int
    ): List<StopRow> {
        if (handle == 0L || !available) return emptyList()
        val rows = runCatching { nativeIndexNearbyRows(handle, lat, lon, radiusM, limit) }
            .getOrNull() ?: return emptyList()
        val out = ArrayList<StopRow>(rows.size)
        for (row in rows) {
            val f = row.split(SEP)
            if (f.size < 7) continue
            val op = runCatching { Operator.valueOf(f[0]) }.getOrNull() ?: continue
            out.add(
                StopRow(
                    stop = StopDef(
                        op = op,
                        id = f[1],
                        name = f[2],
                        nameEn = f[3],
                        lat = f[4].toDoubleOrNull() ?: 0.0,
                        lon = f[5].toDoubleOrNull() ?: 0.0
                    ),
                    routes = emptyList(),
                    distM = f[6].toIntOrNull() ?: 0
                )
            )
        }
        return out
    }

    /** 常駐索引嘅統計：`[routes, stops, routeStops, strings]` */
    fun statsResident(handle: Long): IntArray? {
        if (handle == 0L || !available) return null
        return runCatching { nativeIndexStatsResident(handle) }.getOrNull()
    }

    fun builtAtResident(handle: Long): Long {
        if (handle == 0L || !available) return 0L
        return runCatching { nativeIndexBuiltAtResident(handle) }.getOrDefault(0L)
    }

    // ---------------------------------------------------------------- 加速層

    /** namespace：ETA（車站頁） */
    const val NS_ETA: Int = 0
    /** namespace：班次表 */
    const val NS_TIMETABLE: Int = 1
    /** namespace：附近車站 */
    const val NS_NEARBY: Int = 2
    /** namespace：路線嘅車站（站序） */
    const val NS_ROUTE_STOPS: Int = 3

    /**
     * 加速層係咪由 Rust 持有。
     *
     * 同 [available] 同一個條件（`.so` 載到），分開寫係因為語義唔同：
     * 呢度係「快取由邊度管」，[available] 係「計數由邊度做」。
     */
    val speedAvailable: Boolean get() = available

    /**
     * 寫入加速層。
     *
     * @return true = Rust 收咗；false = 要行 Kotlin 版 `Memo`
     */
    fun speedPut(ns: Int, key: String, payload: String, nowMs: Long): Boolean {
        if (!available) return false
        return runCatching { nativeSpeedPut(ns, key, payload, nowMs) }.getOrDefault(false)
    }

    /** 淨係攞未過期嘅；過期就 null（Rust 會計一次 miss） */
    fun speedGet(ns: Int, key: String, nowMs: Long): String? {
        if (!available) return null
        return runCatching { nativeSpeedGet(ns, key, nowMs) }.getOrNull()
    }

    /** 唔理過期照攞（stale-while-revalidate 用），唔計數 */
    fun speedPeek(ns: Int, key: String): String? {
        if (!available) return null
        return runCatching { nativeSpeedPeek(ns, key) }.getOrNull()
    }

    /** 呢條 key 幾時抓落嚟（冇就 0） */
    fun speedAt(ns: Int, key: String): Long {
        if (!available) return 0L
        return runCatching { nativeSpeedAt(ns, key) }.getOrDefault(0L)
    }

    fun speedRemove(ns: Int, key: String) {
        if (!available) return
        runCatching { nativeSpeedRemove(ns, key) }
    }

    fun speedClear(ns: Int) {
        if (!available) return
        runCatching { nativeSpeedClear(ns) }
    }

    fun speedClearAll() {
        if (!available) return
        runCatching { nativeSpeedClearAll() }
    }

    /** 命中次數（設定頁顯示） */
    fun speedHits(ns: Int): Long {
        if (!available) return 0L
        return runCatching { nativeSpeedHits(ns) }.getOrDefault(0L)
    }

    /** 未中次數（設定頁顯示） */
    fun speedMisses(ns: Int): Long {
        if (!available) return 0L
        return runCatching { nativeSpeedMisses(ns) }.getOrDefault(0L)
    }

    // ---------------------------------------------------------------- 儲存（store）
    //
    // 成個 APK 嘅「記憶體與儲存」：`rust/src/store.rs`。
    // 高層封裝喺 `RustStore`（命名空間常量、init、回退），呢度淨係 JNI 嘅直譯。

    /** 初始化 store 根目錄（建目錄 ＋ 掃描已有命名空間） */
    fun storeInit(root: String): Boolean {
        if (!available) return false
        return runCatching { nativeStoreInit(root) }.getOrDefault(false)
    }

    /** store 係咪已經初始化（[available] 係「.so 載到」，唔同） */
    fun storeReady(): Boolean {
        if (!available) return false
        return runCatching { nativeStoreReady() }.getOrDefault(false)
    }

    fun storePut(ns: String, key: String, value: String, nowMs: Long): Boolean {
        if (!available) return false
        return runCatching { nativeStorePut(ns, key, value, nowMs) }.getOrDefault(false)
    }

    fun storeGet(ns: String, key: String): String? {
        if (!available) return null
        return runCatching { nativeStoreGet(ns, key) }.getOrNull()
    }

    fun storeAt(ns: String, key: String): Long {
        if (!available) return 0L
        return runCatching { nativeStoreAt(ns, key) }.getOrDefault(0L)
    }

    fun storeDel(ns: String, key: String): Boolean {
        if (!available) return false
        return runCatching { nativeStoreDel(ns, key) }.getOrDefault(false)
    }

    fun storeKeys(ns: String): Array<String>? {
        if (!available) return null
        return runCatching { nativeStoreKeys(ns) }.getOrNull()
    }

    fun storeClear(ns: String): Boolean {
        if (!available) return false
        return runCatching { nativeStoreClear(ns) }.getOrDefault(false)
    }

    fun storeCount(ns: String): Long {
        if (!available) return 0L
        return runCatching { nativeStoreCount(ns) }.getOrDefault(0L)
    }

    fun storeBytes(ns: String): Long {
        if (!available) return 0L
        return runCatching { nativeStoreBytes(ns) }.getOrDefault(0L)
    }

    /** 把所有 dirty 命名空間原子落碟 */
    fun storeFlush(): Boolean {
        if (!available) return false
        return runCatching { nativeStoreFlush() }.getOrDefault(false)
    }

    fun storeNamespaces(): Array<String>? {
        if (!available) return null
        return runCatching { nativeStoreNamespaces() }.getOrNull()
    }

    /** 例如 `ns=6 keys=1234 bytes=567890 hits=.. misses=..` */
    fun storeStats(): String? {
        if (!available) return null
        return runCatching { nativeStoreStats() }.getOrNull()
    }

    // ---------------------------------------------------------------- JNI

    private external fun nativeKernelVersion(): Int
    private external fun nativeSampleIndices(n: Int, max: Int): IntArray?
    private external fun nativeBuildTimetable(cols: Array<String>, maxTrips: Int): Array<String>?
    private external fun nativeDiagnose(total: Int, noData: Int, maxGap: Int): Int
    private external fun nativeMergeEta(rows: Array<String>, cap: Int): Array<String>?
    private external fun nativeNormalizeRoute(q: String): Array<String>?

    private external fun nativeSpeedPut(ns: Int, key: String, payload: String, nowMs: Long): Boolean
    private external fun nativeSpeedGet(ns: Int, key: String, nowMs: Long): String?
    private external fun nativeSpeedPeek(ns: Int, key: String): String?
    private external fun nativeSpeedAt(ns: Int, key: String): Long
    private external fun nativeSpeedRemove(ns: Int, key: String)
    private external fun nativeSpeedClear(ns: Int)
    private external fun nativeSpeedClearAll()
    private external fun nativeSpeedHits(ns: Int): Long
    private external fun nativeSpeedMisses(ns: Int): Long
    private external fun nativeStoreInit(root: String): Boolean
    private external fun nativeStoreReady(): Boolean
    private external fun nativeStorePut(
        ns: String,
        key: String,
        value: String,
        nowMs: Long
    ): Boolean
    private external fun nativeStoreGet(ns: String, key: String): String?
    private external fun nativeStoreAt(ns: String, key: String): Long
    private external fun nativeStoreDel(ns: String, key: String): Boolean
    private external fun nativeStoreKeys(ns: String): Array<String>?
    private external fun nativeStoreClear(ns: String): Boolean
    private external fun nativeStoreCount(ns: String): Long
    private external fun nativeStoreBytes(ns: String): Long
    private external fun nativeStoreFlush(): Boolean
    private external fun nativeStoreNamespaces(): Array<String>?
    private external fun nativeStoreStats(): String?
}
