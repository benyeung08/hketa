package hk.eta.app.ui

import hk.eta.app.api.EtaModels.StopOption

/**
 * MORE_ROUTES_PER_STOP — 「定位附近路線」嘅自動定位加入**顯示同站更多路線**。
 *
 * 之前 `loadNearbyRoutes()` 直接 `repo.nearbyStops(lat, lng, radius, 6)`：
 * GeoStops 對同一個實體站會各出一個 KMB／CTB `StopOption`（兩家站號
 * 命名空間唔同），6 個配額好快被兩三實體站食晒，近站嘅另一半公司 ETA
 * 直接被 cut 走，望落「同一站得幾條路線」。
 *
 * 呢個純 Kotlin 層（無 Android／無網絡／可離線測）做三件事：
 *  1. ALL_ROUTES: [CANDIDATE_LIMIT]／[ENTITY_LIMIT]／[MAX_QUERIES] 全部設為
 *     `Int.MAX_VALUE` —— 半徑內**所有**站點、**所有**實體站、**所有**公司
 *     別名都保留，唔再有任何 24／6／12 配額截斷；
 *  2. [ETA_BATCH_SIZE] 將全部 ETA 查詢**分批**執行（每批最多 12 個並行），
 *     [BATCH_DELAY_MS] 喺批次之間讓一讓，避免一次過幾百個請求撞官方 429；
 *  3. [sortCards] 按 (距離 rank, 路線號, 營運商, 目的地) 排，同一站嘅路線排埋一齊、同號唔同公司相鄰，
 *     `App1933Format.cardsFrom()` 本來就唔 truncate 路線，全部如實出卡。
 *
 * **唔郁** 8 營運商資料層、`GeoStops`、`EtaRepository`、定位權限閘、
 * `LocationTracker` 前景位置更新、ETA 自動刷新，亦**唔加任何色值／View／掣**。
 * 自動定位同之後嘅位置更新都共用 `loadNearbyRoutes()`，所以兩者一齊生效。
 */
object NearbyMoreRoutes {

    /** ALL_ROUTES: 半徑內全部候選站，唔再只攞 24 個。 */
    const val CANDIDATE_LIMIT = Int.MAX_VALUE

    /** ALL_ROUTES: 全部實體站，唔再只留最近 6 個。 */
    const val ENTITY_LIMIT = Int.MAX_VALUE

    /** ALL_ROUTES: 全部站點別名都要查 ETA，唔再 cut 喺 12 個。 */
    const val MAX_QUERIES = Int.MAX_VALUE

    /** 每批最多幾個並行 `stopEta` 查詢，慳住官方 API 速率限制。 */
    const val ETA_BATCH_SIZE = 12

    /** 批次之間讓一讓（毫秒），減低連續打官方 API 撞 429 嘅機會。 */
    const val BATCH_DELAY_MS = 120L

    /**
     * 同 `EtaViewModel.fetchCards()` 嘅合併 bucket 用同一條規則：
     * 有中文站名就用站名，冇就用 stopId。咁 KMB「砵典乍街」同 CTB
     * 「砵典乍街」會算同一實體站；循環線喺 cardsFrom 仍各自成卡，唔喺呢層拆。
     */
    fun entityKey(stop: StopOption): String {
        val name = stop.stopName.trim()
        return if (name.isEmpty()) stop.stopId else name
    }

    /**
     * candidates 必須係 GeoStops 已按距離排好序嘅結果。
     * ALL_ROUTES: [ENTITY_LIMIT]／[MAX_QUERIES] 係 `Int.MAX_VALUE`，所以
     * **所有**候選站、所有實體站、所有公司別名都照收，回傳順序 = 距離序，
     * `safeEta` 嘅 rank 唔變。保留原本兩個 break 令日後改返限量唔使改結構。
     */
    fun select(candidates: List<StopOption>): List<StopOption> {
        val out = ArrayList<StopOption>()
        val entities = LinkedHashSet<String>()
        for (s in candidates) {
            val key = entityKey(s)
            if (!entities.contains(key)) {
                if (entities.size >= ENTITY_LIMIT) break
                entities.add(key)
            }
            out.add(s)
            if (out.size >= MAX_QUERIES) break
        }
        return out
    }

    /**
     * ALL_ROUTES: 將全部站點別名切成每批 [ETA_BATCH_SIZE] 個，等
     * `fetchCards()` 逐批 `awaitAll()`，唔會一次過開幾百個 coroutine。
     * 純函式、保留距離順序，可離線測。
     */
    fun batches(stops: List<IndexedValue<StopOption>>): List<List<IndexedValue<StopOption>>> =
        stops.chunked(ETA_BATCH_SIZE)

    /** 畀狀態列如實報告「幾個站」，而唔係「幾個 StopOption」。 */
    fun entityCount(stops: List<StopOption>): Int =
        stops.map { entityKey(it) }.toHashSet().size

    /** 同站路線集中、按路線號排；全部官方路線都保留，唔 take(N)。 */
    fun sortCards(cards: List<RouteCard>): List<RouteCard> =
        cards.sortedWith(
            compareBy<RouteCard> { it.rank }
                .thenBy { it.route }
                .thenBy { it.company }
                .thenBy { it.dest }
        )
}
