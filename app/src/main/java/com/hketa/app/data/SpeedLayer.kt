package com.hketa.app.data

import kotlinx.coroutines.CompletableDeferred

/**
 * # 加速層（純 Kotlin —— 唔使 .so，Rust 有冇 build 到都生效）
 *
 * Rust 嗰層解決嘅係「索引放邊度、點樣查」，但**網絡往返**呢一截佢幫唔到：
 * 每次入車站頁、每次切去班次表分頁、每次開主頁，都會重新打一次官方接口，
 * 就算 3 秒前先睇過都係咁 —— 呢個先係用戶最感受到嘅慢。
 *
 * 呢度做三件嘢：
 *
 * ### 1. `Memo` —— TTL 快取 ＋ stale-while-revalidate
 *
 * - 有新鮮嘅 → **即出**，連轉圈都唔使
 * - 得舊嘅 → **先出舊嘅**（畫面即刻有嘢），同時背景刷新
 * - 完全冇 → 先轉圈，正常抓取
 *
 * 呢個「先出舊嘅」係慳時間嘅關鍵：用戶入到畫面**即刻見到上一次嘅班次**，
 * 一秒後 quietly 變成最新，唔使對住轉圈等。
 *
 * ### 2. `SingleFlight` —— 同一個 key 同時淨係一個請求
 *
 * 快速撳幾下、或者重組觸發多次 `loadEta`，以前會開幾個並行請求打同一個站。
 * 而家第一個做 leader，其餘一齊等同一個結果。
 *
 * ### 3. TTL 點解係呢啲數
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

    /** ETA：站 ＋ 路線 */
    val eta = Memo<List<EtaEntry>>(ETA_TTL_MS, max = 128)

    /** 班次表：路線（op/route/bound/serviceType） */
    val timetable = Memo<RouteTimetable>(TIMETABLE_TTL_MS, max = 32)

    /** 附近車站：位置（四捨五入到 ~11 米）＋ 半徑 */
    val nearby = Memo<List<NearbyStop>>(NEARBY_TTL_MS, max = 16)

    /** 路線嘅車站：路線 key */
    val routeStops = Memo<List<StopWithSeq>>(ROUTE_STOPS_TTL_MS, max = 64)

    /** ETA 單飛：同一個站同時淨係一個網絡請求（值係 `etasFor` 嗰個 Pair） */
    val etaFlight = SingleFlight<Pair<List<EtaEntry>, String?>>()

    /** 附近車站嘅 key：位置要量化，否則每次定位抖動都當 miss */
    fun nearbyKey(lat: Double, lon: Double, radiusMeters: Int): String =
        "%.4f,%.4f,%d".format(lat, lon, radiusMeters)

    /** ETA 嘅 key：站 id ＋ 營辦商 ＋（有指定路線就加埋路線） */
    fun etaKeyOf(stop: StopDef, route: RouteDef?): String =
        "${stop.op.name}|${stop.id.trim()}|${route?.route.orEmpty().trim()}"
            .replace(Regex("[^A-Za-z0-9_|.-]"), "_")
            .take(160)

    /** 班次表／路線車站共用嘅路線 key */
    fun routeKey(op: Operator, route: String, bound: String, serviceType: String): String =
        "${op.name}|$route|$bound|$serviceType"

    fun clearAll() {
        eta.clear()
        timetable.clear()
        nearby.clear()
        routeStops.clear()
    }

    /** 命中率（設定頁會顯示，等我同用戶一眼睇到係咪真係生效） */
    fun stats(): Pair<Long, Long> = eta.hits to eta.misses
}

/**
 * TTL 快取。
 *
 * - [get]：淨係返**未過期**嘅；過期當 miss（並計數）
 * - [peek]：唔理過期照返 —— 用嚟做 stale-while-revalidate
 *
 * 用 access-order 嘅 `LinkedHashMap` 做 LRU，超過 [max] 自動踢最耐冇用嗰個；
 * 全部 `@Synchronized`，因為附近車站會喺 `Dispatchers.IO` 入面讀寫。
 */
class Memo<T>(private val ttlMs: Long, private val max: Int = 64) {

    private class Box<T>(val value: T, val atMs: Long)

    private val map = LinkedHashMap<String, Box<T>>(32, 0.75f, true)

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

    private fun trimLocked() {
        while (map.size > max) {
            val oldest = map.keys.firstOrNull() ?: return
            map.remove(oldest)
        }
    }
}

/**
 * 單飛：同一個 key 同時淨係跑一次 [block]，其餘嘅一齊等同一個結果。
 *
 * 快速撳幾下／重組觸發多次請求嗰陣，唔會再開幾個並行連線打同一個站。
 * leader 完成（成功定失敗）之後會把自己由表入面移除，所以下次會重新開一個。
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
