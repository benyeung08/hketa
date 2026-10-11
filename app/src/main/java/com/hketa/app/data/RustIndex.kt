package com.hketa.app.data

/**
 * Rust 索引庫（`libhketa_store.so`）嘅 JNI 封裝。
 *
 * ## 點解要呢個
 *
 * 索引規模：2099 條路線 / 7250 個車站 / 63150 筆路線-車站對應、JSON 約 7 MB。
 *
 * | | 純 Kotlin（JSON） | Rust |
 * |---|---|---|
 * | 磁碟 | 7.20 MB | **1.04 MB** |
 * | 字串實例 | 每筆獨立 `String` | **字串表去重，全部共用** |
 * | 附近車站 | 遍歷 JVM 物件 | Rust 側連續記憶體 |
 *
 * ## 點解唔用 JSON 跨邊界
 *
 * 7 MB JSON 解析一次要幾百毫秒，咁就冇咗用 Rust 嘅意義。所以：
 *
 * - `nativeStrings()` 一次過攞去重後嘅字串表
 * - 其餘全部係 `IntArray` / `DoubleArray`（扁平化）
 * - Kotlin 側用「字串表 ＋ 索引」砌物件 → **所有記錄共用同一個 `String` 實例**
 *
 * ## 安全設計：一定會回退
 *
 * `.so` 可能唔存在（例如本地 build 冇裝 Rust toolchain）、或者加載失敗。
 * 呢個時候 `available == false`，`IndexStore` 會行返原本嘅純 Kotlin 路徑 ——
 * **App 功能完全唔受影響**，只係享受唔到 Rust 嘅好處。
 */
object RustIndex {

    /** `.so` 係咪用得到。呢個係唯一嘅判斷依據，失敗一律回退。 */
    val available: Boolean by lazy {
        runCatching {
            System.loadLibrary("hketa_store")
            // 叫一次最簡單嘅 native 方法，確認個 library 真係啱版本
            nativeVersion() >= 1
        }.getOrDefault(false)
    }

    // ---------- 生命週期 ----------

    external fun nativeLoad(path: String): Long
    external fun nativeFromJson(json: String): Long
    external fun nativeSave(handle: Long, path: String): Boolean
    external fun nativeFree(handle: Long)
    external fun nativeVersion(): Int

    // ---------- 讀取（全部係扁平陣列）----------

    /** `[routes, stops, routeStops, strings]` */
    external fun nativeStats(handle: Long): IntArray?

    external fun nativeBuiltAt(handle: Long): Long

    /** 去重後嘅字串表 —— Kotlin 側當做共用嘅 `String` 池 */
    external fun nativeStrings(handle: Long): Array<String>?

    /** 每條路線 9 個 int：`op, bound, route, routeId, orig, origEn, dest, destEn, serviceType` */
    external fun nativeRoutes(handle: Long): IntArray?

    /** 每個站 4 個 int：`op, id, name, nameEn` */
    external fun nativeStops(handle: Long): IntArray?

    /** `lat, lon` 交替 */
    external fun nativeStopsLatLon(handle: Long): DoubleArray?

    /** 每筆 7 個 int：`op, bound, seq, route, serviceType, stopId, routeId` */
    external fun nativeRouteStops(handle: Long): IntArray?

    /** 半徑內嘅車站，按距離排。每項 2 個 int：`stop 下標, 距離米` */
    external fun nativeNearby(handle: Long, lat: Double, lon: Double, radiusM: Double): IntArray?

    /** 某個站有邊啲路線 —— 返回字串表索引 */
    external fun nativeRoutesAtStop(handle: Long, op: String, stopId: String): IntArray?

    // ---------- 常駐（Rust 完全擁有記憶體）----------
    //
    // 上面嗰啲係 `Box::into_raw`：Kotlin 攞到 handle，用完要自己 `nativeFree`，
    // 而 `toIndexData()` 會把成個索引砌返落 JVM heap —— 即係 JVM 仍然有份副本。
    //
    // 下面呢組係 **常駐**：索引由 Rust 放喺 native heap（`Box::leak`），
    // Kotlin 淨係揸住一個 `Long`。查詢全部喺 Rust 側做，**淨係把要顯示嗰
    // 幾十筆傳過界** —— JVM heap 淨係持有暫時性、結果咁大嘅物件。

    /** 由碟上嘅二進制索引開一個常駐 handle；失敗返回 0 */
    external fun nativeOpen(path: String): Long

    /** 由 JSON 建一個常駐 handle（第一次建索引嗰陣用） */
    external fun nativeOpenJson(json: String): Long

    /** 收返 handle，並釋放 native heap 上面嘅索引 */
    external fun nativeClose(handle: Long)

    /** 常駐索引數量（診斷用，正常係 1） */
    external fun nativeResidentCount(): Int

    /** 把常駐索引寫落碟（原子寫） */
    external fun nativeSaveResident(handle: Long, path: String): Boolean

    external fun nativeStatsResident(handle: Long): IntArray?

    external fun nativeBuiltAtResident(handle: Long): Long

    /** 搵路線。每行：`op|route|bound|serviceType|routeId|orig|dest` */
    external fun nativeSearchRoutes(handle: Long, query: String, limit: Int): Array<String>?

    /** 搵車站。每行：`op|id|name|nameEn|lat|lon|route1,route2,…` */
    external fun nativeSearchStops(handle: Long, query: String, limit: Int): Array<String>?

    /** 某條路線嘅車站（按 seq）。每行：`op|stopId|seq|name|nameEn|lat|lon` */
    external fun nativeRouteStopsRows(
        handle: Long, op: String, route: String, bound: String, serviceType: String
    ): Array<String>?

    /** 半徑內嘅車站。每行：`op|id|name|nameEn|lat|lon|distM` */
    external fun nativeNearbyRows(
        handle: Long, lat: Double, lon: Double, radiusM: Double, limit: Int
    ): Array<String>?

    // ==================== 高階封裝 ====================

    /**
     * 由 Rust handle 砌返一個 `IndexData`。
     *
     * 重點：**所有字串都係由 `strings` 陣列攞** —— 所以同一個站名出現
     * 幾百次，JVM 入面都係同一個 `String` 實例，唔會開幾百份。
     */
    fun toIndexData(handle: Long): IndexData? {
        if (handle == 0L) return null
        return runCatching {
            val strings = nativeStrings(handle) ?: return null
            val routesRaw = nativeRoutes(handle) ?: return null
            val stopsRaw = nativeStops(handle) ?: return null
            val latLon = nativeStopsLatLon(handle) ?: return null
            val rsRaw = nativeRouteStops(handle) ?: return null
            val builtAt = nativeBuiltAt(handle)
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
}

// ==================== 常駐模式：查詢結果嘅封裝 ====================
//
// 行分隔符係 `\u001F`（unit separator）—— 站名入面唔會有呢個字符，
// 所以唔使自訂二進制格式（少一個出錯位）。

/** 行分隔符，要同 Rust `lib.rs` 嘅 `SEP` 一致 */
private const val ROW_SEP = '\u001F'

/** Rust 搵路線嘅結果（已經係 `RouteDef`，可以直接畀 UI） */
fun searchRoutes(handle: Long, query: String, limit: Int): List<RouteDef> {
    if (handle == 0L || query.isBlank() || !RustIndex.available) return emptyList()
    val rows = runCatching { RustIndex.nativeSearchRoutes(handle, query.trim(), limit) }
        .getOrNull() ?: return emptyList()
    val out = ArrayList<RouteDef>(rows.size)
    for (row in rows) {
        val f = row.split(ROW_SEP)
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
data class StopRow(
    val stop: StopDef,
    /** 該站停靠嘅路線號（已排序） */
    val routes: List<String>,
    /** 距離（米）；`nearbyRows` 先會有，否則 0 */
    val distM: Int = 0
)

fun searchStops(handle: Long, query: String, limit: Int): List<StopRow> {
    if (handle == 0L || query.isBlank() || !RustIndex.available) return emptyList()
    val rows = runCatching { RustIndex.nativeSearchStops(handle, query.trim(), limit) }
        .getOrNull() ?: return emptyList()
    val out = ArrayList<StopRow>(rows.size)
    for (row in rows) {
        val f = row.split(ROW_SEP)
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
    if (handle == 0L || !RustIndex.available) return emptyList()
    val rows = runCatching {
        RustIndex.nativeRouteStopsRows(handle, op.name, route, bound, serviceType)
    }.getOrNull() ?: return emptyList()
    val out = ArrayList<Pair<Int, StopDef>>(rows.size)
    for (row in rows) {
        val f = row.split(ROW_SEP)
        if (f.size < 7) continue
        val o = runCatching { Operator.valueOf(f[0]) }.getOrNull() ?: continue
        out.add(
            f[2].toIntOrNull() ?: 0 to StopDef(
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
    if (handle == 0L || !RustIndex.available) return emptyList()
    val rows = runCatching { RustIndex.nativeNearbyRows(handle, lat, lon, radiusM, limit) }
        .getOrNull() ?: return emptyList()
    val out = ArrayList<StopRow>(rows.size)
    for (row in rows) {
        val f = row.split(ROW_SEP)
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
fun stats(handle: Long): IntArray? {
    if (handle == 0L || !RustIndex.available) return null
    return runCatching { RustIndex.nativeStatsResident(handle) }.getOrNull()
}

fun builtAt(handle: Long): Long {
    if (handle == 0L || !RustIndex.available) return 0L
    return runCatching { RustIndex.nativeBuiltAtResident(handle) }.getOrDefault(0L)
}
