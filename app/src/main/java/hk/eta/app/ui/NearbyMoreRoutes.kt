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
 *  1. 由較多候選（[CANDIDATE_LIMIT]）入面，按**實體站名**分組，保留最近
 *     [ENTITY_LIMIT] 個實體站嘅**全部**公司別名，唔再被單一配額截斷；
 *  2. 用 [MAX_QUERIES] 閘住並行 ETA 查詢數，避免官方 429；
 *  3. [sortCards] 按 (距離 rank, 路線號, 營運商, 目的地) 排，同一站嘅路線排埋一齊、同號唔同公司相鄰，
 *     `App1933Format.cardsFrom()` 本來就唔 truncate 路線，全部如實出卡。
 *
 * **唔郁** 8 營運商資料層、`GeoStops`、`EtaRepository`、定位權限閘、
 * `LocationTracker` 前景位置更新、ETA 自動刷新，亦**唔加任何色值／View／掣**。
 * 自動定位同之後嘅位置更新都共用 `loadNearbyRoutes()`，所以兩者一齊生效。
 */
object NearbyMoreRoutes {

    /** 向 GeoStops 攞多啲候選，畀分組邏輯有得揀（唔係最終顯示數）。 */
    const val CANDIDATE_LIMIT = 24

    /** 最近幾個「實體站」；每個站嘅全部公司別名都保留。 */
    const val ENTITY_LIMIT = 6

    /** 最多並行查幾多條 `stopEta`，慳住官方 API 速率限制。 */
    const val MAX_QUERIES = 12

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
     * 遇到第 7 個新實體站就停；已收錄嘅實體站，其餘公司別名照收，
     * 直到 [MAX_QUERIES] 為止。回傳順序 = 距離序，`safeEta` 嘅 rank 唔變。
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
