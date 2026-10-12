package com.hketa.app.data

/**
 * 領域核心嘅 Rust 實作（kernel）—— **計數，唔係儲存**。
 *
 * 同 `RustIndex`（索引放邊度、點樣查）唔同，呢度搬嘅係**實際嘅運算邏輯**：
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
 * 同 `RustIndex` 一樣**唔用 JSON**。字段之間用 `\u001F`，字段內部用 `\u001E`，
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
                loaded = runCatching { System.loadLibrary("hketa_store"); true }
                    .getOrDefault(false)
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

    // ---------------------------------------------------------------- JNI

    private external fun nativeKernelVersion(): Int
    private external fun nativeSampleIndices(n: Int, max: Int): IntArray?
    private external fun nativeBuildTimetable(cols: Array<String>, maxTrips: Int): Array<String>?
    private external fun nativeDiagnose(total: Int, noData: Int, maxGap: Int): Int
    private external fun nativeMergeEta(rows: Array<String>, cap: Int): Array<String>?
    private external fun nativeNormalizeRoute(q: String): Array<String>?
}
