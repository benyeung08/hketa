package com.hketa.app.data

/**
 * 點對點／起訖路線規劃（對齊 hkbus.app 嘅 Planning / P2P 頁）。
 *
 * 純 Kotlin、無 Android 依賴、無網絡 —— 可以直接行 JUnit。
 * 用現成 [IndexData]（routes / stops / routeStops）自己砌反向索引，唔使改 ViewModel。
 *
 * 範圍（誠實講清楚）：**直達 + 一次轉乘**，唔做多段轉乘／行車時間優化／
 * 跨營辦商聯合轉乘優惠。呢個係「由站名搵到點搭」嘅實用版，
 * 唔係 hkbus.app 後端嗰套完整 journey planner。
 *
 * 方向判斷靠 `RouteStopDef.seq`：起點 seq 必須細過終點 seq，
 * 避免建議「倒頭車」。循環線因為 seq 會返轉，可能漏，屬已知限制。
 */
enum class JourneyKind { DIRECT, TRANSFER }

data class Journey(
    val kind: JourneyKind,
    val firstRoute: String,
    val firstOp: String,
    val firstFrom: String,
    val firstTo: String,
    val secondRoute: String = "",
    val secondOp: String = "",
    val interchange: String = "",
    val stops: Int = 0,
    val fromStop: String = "",
    val toStop: String = ""
) {
    val label: String
        get() = if (kind == JourneyKind.DIRECT) {
            "$firstRoute  $firstOp　$fromStop → $toStop"
        } else {
            "$firstRoute → $interchange → $secondRoute　$fromStop → $toStop"
        }
}

object JourneyPlanner {

    private const val MAX_CANDIDATES = 6
    private const val MAX_RESULTS = 20

    private data class Leg(
        val key: String,
        val route: String,
        val op: String,
        val seq: Int,
        val stopId: String,
        val stopName: String
    )

    /**
     * @param fromQuery 起點站名（部分匹配，中文／英文都得）
     * @param toQuery   終點站名
     */
    fun plan(index: IndexData, fromQuery: String, toQuery: String, limit: Int = MAX_RESULTS): List<Journey> {
        val f = fromQuery.trim()
        val t = toQuery.trim()
        if (f.isBlank() || t.isBlank() || f.equals(t, ignoreCase = true)) return emptyList()
        if (index.routeStops.isEmpty() || index.stops.isEmpty()) return emptyList()

        val nameById = HashMap<String, String>(index.stops.size * 2)
        index.stops.forEach { nameById.putIfAbsent("${it.op.name}|${it.id}", it.name) }

        // stopId → 該站出現喺邊啲路線、第幾站
        val byStop = HashMap<String, ArrayList<Leg>>()
        // routeKey → 有序站序
        val byRoute = HashMap<String, ArrayList<Leg>>()
        index.routeStops.forEach { rs ->
            val key = "${rs.op.name}|${rs.route}|${rs.bound}|${rs.serviceType}|${rs.routeId}"
            val name = nameById["${rs.op.name}|${rs.stopId}"]
                ?: rs.stopId
            val leg = Leg(key, rs.route, rs.op.name, rs.seq, rs.stopId, name)
            byStop.getOrPut("${rs.op.name}|${rs.stopId}") { ArrayList() }.add(leg)
            byRoute.getOrPut(key) { ArrayList() }.add(leg)
        }
        byRoute.values.forEach { it.sortBy { l -> l.seq } }

        val fromIds = matchStopIds(index, f)
        val toIds = matchStopIds(index, t)
        if (fromIds.isEmpty() || toIds.isEmpty()) return emptyList()

        val fromLegs = LinkedHashMap<String, MutableList<Leg>>()
        fromIds.take(MAX_CANDIDATES).forEach { id ->
            byStop[id].orEmpty().forEach { l -> fromLegs.getOrPut(l.key) { ArrayList() }.add(l) }
        }
        val toLegs = LinkedHashMap<String, MutableList<Leg>>()
        toIds.take(MAX_CANDIDATES).forEach { id ->
            byStop[id].orEmpty().forEach { l -> toLegs.getOrPut(l.key) { ArrayList() }.add(l) }
        }

        val out = ArrayList<Journey>()

        // ① 直達：同一條路線同時有起點同終點，且 seq 遞增
        fromLegs.forEach { (routeKey, fLegs) ->
            val tLegs = toLegs[routeKey] ?: return@forEach
            for (a in fLegs) {
                for (b in tLegs) {
                    if (a.seq >= b.seq) continue
                    out.add(
                        Journey(
                            kind = JourneyKind.DIRECT,
                            firstRoute = a.route,
                            firstOp = a.op,
                            firstFrom = a.stopName,
                            firstTo = b.stopName,
                            stops = b.seq - a.seq,
                            fromStop = a.stopName,
                            toStop = b.stopName
                        )
                    )
                    break
                }
            }
        }

        // ② 一次轉乘：
        //    A 線由起點出發，之後停靠嘅任何一站 M；
        //    B 線喺 M 之後會去到終點 → M 就係轉車站。
        //    兩段各自用返自己路線嘅 seq 判斷方向，唔會撈亂尺度。
        val seenPairs = HashSet<String>()
        fromLegs.forEach { (aKey, fLegs) ->
            val aRoute = byRoute[aKey].orEmpty()
            val startSeq = fLegs.minOfOrNull { it.seq } ?: return@forEach
            // A 線喺起點之後嘅站（即可以落車轉車嘅位置）
            val aAfter = aRoute.filter { it.seq > startSeq }
            if (aAfter.isEmpty()) return@forEach
            val aAfterIds = aAfter.map { "${it.op}|${it.stopId}" }.toHashSet()

            toLegs.forEach { (bKey, tLegs) ->
                if (bKey == aKey) return@forEach       // 直達已處理
                val bRoute = byRoute[bKey].orEmpty()
                val endSeq = tLegs.maxOfOrNull { it.seq } ?: return@forEach
                // B 線喺終點之前嘅站（即可以上車嘅位置）
                val bBefore = bRoute.filter { it.seq < endSeq }
                for (m in bBefore) {
                    val midId = "${m.op}|${m.stopId}"
                    if (midId !in aAfterIds) continue
                    val aMid = aAfter.first { "${it.op}|${it.stopId}" == midId }
                    val start = fLegs.minByOrNull { it.seq } ?: continue
                    val end = tLegs.maxByOrNull { it.seq } ?: continue
                    val tag = "$aKey|$bKey|${m.stopName}"
                    if (!seenPairs.add(tag)) continue
                    out.add(
                        Journey(
                            kind = JourneyKind.TRANSFER,
                            firstRoute = aRoute.firstOrNull()?.route.orEmpty(),
                            firstOp = aRoute.firstOrNull()?.op.orEmpty(),
                            firstFrom = start.stopName,
                            firstTo = m.stopName,
                            secondRoute = bRoute.firstOrNull()?.route.orEmpty(),
                            secondOp = bRoute.firstOrNull()?.op.orEmpty(),
                            interchange = m.stopName,
                            stops = (aMid.seq - start.seq) + (end.seq - m.seq),
                            fromStop = start.stopName,
                            toStop = end.stopName
                        )
                    )
                }
            }
        }

        return out
            .sortedWith(compareBy({ it.kind.ordinal }, { it.stops }, { it.firstRoute }))
            .distinctBy { it.label }
            .take(limit)
    }

    /** 站名部分匹配；優先完全一致，其次包含，最多 6 個候選，避免「中環」炸出幾十個站 */
    private fun matchStopIds(index: IndexData, q: String): List<String> {
        val key = q.trim().lowercase()
        val exact = ArrayList<String>()
        val partial = ArrayList<String>()
        for (s in index.stops) {
            val zh = s.name.trim().lowercase()
            val en = s.nameEn.trim().lowercase()
            val id = "${s.op.name}|${s.id}"
            when {
                zh == key || en == key -> exact.add(id)
                zh.contains(key) || (en.isNotEmpty() && en.contains(key)) -> partial.add(id)
            }
        }
        return (exact + partial).distinct().take(MAX_CANDIDATES)
    }
}
