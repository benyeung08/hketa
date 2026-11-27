package hk.eta.app.ui

import hk.eta.app.api.EtaModels.EtaItem
import hk.eta.app.api.EtaModels.StopOption
import hk.eta.app.api.CircularRoutes
import hk.eta.app.api.EtaRepository
import hk.eta.app.api.JointRoutes
import hk.eta.app.api.GovServiceHours
import hk.eta.app.api.TimeUtils

/**
 * APP1933-style presentation of the SAME open-data EtaItem list.
 *
 * This is a pure formatting layer: it does NOT touch EtaRepository, GeoStops,
 * RouteDataStore or any of the 8 operators. The official app shows
 *   route no. | 往 destination | stop name (stop id) | big minutes on the right
 * and, after picking a route+stop, the NEXT THREE arrivals with markers for
 * "-" (just arrived), scheduled trips and no-service periods.
 *
 * JOINT_KMB_CTB: when the same route+stop+destination has BOTH KMB and CTB
 * arrivals, they become ONE card labelled 城巴/九巴, and every one of the
 * three trips says 該班次：城巴 / 該班次：九巴.
 */

/** One of the (up to) three arrivals shown for a route card. */
data class Trip(
    val label: String,      // "13", "-", "—", "20:46"
    val clock: String,      // "13:25 HKT" or ""
    val tag: String,        // "預定班次" / "沒有服務" / "剛到站" / "改道" / ""
    val operator: String,   // joint-operator company code, or ""
    val operatorText: String = ""   // "該班次：城巴" / "該班次：九巴" / ""
)

/**
 * CTB_STOP_NAME / CTB_ONLY_NAME: the SAME physical nearby stop has a KMB
 * stop id/name AND a Citybus stop id/name. Each operator's card must show
 * ITS OWN official stop label — ONLY Citybus cards use the Citybus wording,
 * never a KMB label inherited from the merged nearby bucket. Joint cards
 * keep the KMB label (previous request); KMB cards keep the KMB label.
 */
data class StopDisplay(val name: String, val id: String)

/** One row of the APP1933 home page. */
data class RouteCard(
    val rank: Int,          // nearby-stop order (used for sorting)
    val company: String,
    val route: String,
    val dest: String,
    val stopName: String,
    val stopId: String,
    val distanceNote: String,
    val trips: List<Trip>,  // at most 3
    val circular: Boolean = false,
    val style: String = ""   // CIRCULAR_ROUTES: 九巴循環線 / 城巴循環線
)

object App1933Format {

    /** "九龍灣臨華街 (KT123)" — the official app puts the stop id in brackets. */
    fun stopLabel(name: String, id: String): String = when {
        id.isEmpty() -> name
        name.isEmpty() -> id
        else -> "$name ($id)"
    }

    /**
     * CTB_ONLY_NAME: pure, offline-testable resolution of a card's stop label.
     * ONLY a card whose company is CTB picks the Citybus StopDisplay; JOINT
     * keeps the KMB one (registered by the caller), and every other company
     * falls back to the caller's own StopOption name / id unchanged.
     */
    fun resolveStopDisplay(
        cardCompany: String,
        displays: Map<String, StopDisplay>,
        fallback: StopDisplay
    ): StopDisplay = displays[cardCompany] ?: fallback

    /**
     * KMB_LWB_SPLIT: ROUTE 級標籤嘅唯一入口。九巴路線只寫「九巴」、
     * 龍運路線只寫「龍運」；官方 GTFS 冇記錄就留空，唔誤標对方。
     */
    /**
     * DIRECTION_TITLE_SYNC: 官方 route-stop 站序入面該方向嘅**終點站名**。
     * 當官方目錄冇方向級 dest_tc（嶼巴／小巴／港鐵／輕鐵／接駁巴士）時，
     * 用呢個官方站名降級更新紅頂標題，唔捏造、零 IO、可離線測。
     */
    fun destinationForBound(stops: List<StopOption>, bound: String): String {
        if (bound.isEmpty()) return ""
        return stops.filter {
            it.bound.equals(bound, ignoreCase = true) && it.stopName.isNotBlank()
        }.maxByOrNull { it.seq }?.stopName?.trim()
            ?.removePrefix("往 ")?.trim().orEmpty()
    }

    fun companyLabel(company: String, route: String = ""): String =
        GovServiceHours.Brand.label(company, route)

    /** Single source of truth for the TRIP-level "該班次：…" label. */
    fun tripOperatorText(trip: Trip, route: String = ""): String = when {
        trip.operatorText.isNotEmpty() ->
            JointRoutes.TRIP_PREFIX + trip.operatorText
        trip.operator.isNotEmpty() -> {
            // JOINT_EVERY_TRIP_OPERATOR: 官方 GTFS 判唔到歸屬（例如特別線／
            // 聯營目錄交疊）時，用 ETA 本身嘅 company code 如實降級，
            // 唔再留空 —— 用戶要「每一個班次」後面都見到營運商。
            // KMB -> 九巴 / CTB -> 城巴；龍運聯營仍由 tripLabel 優先判「龍運」。
            val name = GovServiceHours.Brand.tripLabel(trip.operator, route)
                .ifEmpty { JointRoutes.operatorLabel(trip.operator) }
            JointRoutes.TRIP_PREFIX + name
        }
        else -> ""
    }

    /**
     * Group a flat ETA list by (route, stop, destination) — deliberately NOT
     * by company — then hand EACH group to SameRouteOperators:
     * ONLY an exact KMB+CTB non-circular pair becomes one 城巴/九巴 joint card;
     * EVERY OTHER company combination is SPLIT into one card per operator,
     * so the same route number served by different bus companies never shares
     * a card. Each card keeps the three earliest arrivals of ITS OWN company.
     */
    fun cardsFrom(
        etas: List<EtaItem>,
        stopName: String,
        stopId: String,
        note: String,
        rank: Int = 0,
        displays: Map<String, StopDisplay> = emptyMap(),
        circularHint: Boolean = false
    ): List<RouteCard> {
        val groups = LinkedHashMap<String, MutableList<EtaItem>>()
        // JOINT_EVERY_TRIP_OPERATOR: 零 IO、純函式咁由呢批 ETA 自己判定
        // 「邊啲路線號係聯營」—— 同一 route 同時有 KMB + CTB 班次即係。
        // 唔靠 isJointRoute()（要讀本機目錄，唔應該喺 render() main thread 做），
        // 亦唔受 destTc 用字唔同／circularHint 拆卡／partner ETA 只回一邊影響。
        val companiesByRoute = LinkedHashMap<String, MutableSet<String>>()
        for (e in etas) {
            companiesByRoute.getOrPut(e.route) { HashSet() }.add(e.company)
        }
        val jointRoutes = companiesByRoute
            .filter { (_, cs) -> cs.contains(EtaRepository.KMB)
                && cs.contains(EtaRepository.CTB) }
            .keys.toSet()
        for (e in etas) {
            // JOINT_KMB_CTB: KMB 同 CTB 嘅 stopId 屬兩個唔同命名空間。
            // 用呼叫方傳入嘅「同一實體站」站號做分組鍵，兩家公司嘅班次
            // 先可以歸入同一個 group；未提供時退回原本 e.stopId。
            val groupStop = if (stopId.isNotEmpty()) stopId else e.stopId
            val key = e.route + "|" + groupStop + "|" + e.destTc
            groups.getOrPut(key) { ArrayList() }.add(e)
        }
        val out = ArrayList<RouteCard>()
        for ((_, list) in groups) {
            // SEPARATE_OPERATORS: 同路線號碼唔同營運商各自成卡；
            // 只有剛好 KMB+CTB 且兩邊都非循環先合併做既有聯營卡。
            for (group in SameRouteOperators.partition(list, circularHint)) {
                val sorted = group.etas.sortedBy { it.etaMillis }
                val first = sorted.firstOrNull() ?: continue
                val cardCompany = group.company
                val joint = cardCompany == JointRoutes.JOINT
                // CIRCULAR_ONLY: a Citybus circular card shows the Citybus way,
                // a KMB circular card the App1933 way —判定用自己嘅班次。
                val style = if (group.circular) {
                    CircularRoutes.styleLabel(cardCompany, first.route)
                } else {
                    ""
                }
                // JOINT_EVERY_TRIP_OPERATOR: 聯營路線即使被拆成兩張卡
                // （目的地用字唔同／循環線／partner 只回一邊），每一張卡嘅
                // 每一個班次都要帶自己嘅營運商，唔再只靠合併卡嘅 joint flag。
                val showOperator = joint || jointRoutes.contains(first.route)
                val trips = sorted.take(3).map { tripOf(it, cardCompany, showOperator) }
                // CTB_STOP_NAME: pick the stop label of THE OPERATOR OF THIS CARD.
                // displays[JOINT] is the KMB label (joint routes keep KMB naming);
                // displays[CTB] is the Citybus label. A split single-company card
                // falls back to ITS OWN first.stopId, never the bucket's KMB id.
                val fallbackId = if (stopId.isEmpty() || !joint) {
                    first.stopId
                } else {
                    stopId
                }
                val fallback = StopDisplay(stopName, fallbackId)
                val display = resolveStopDisplay(cardCompany, displays, fallback)
                out.add(
                    RouteCard(
                        rank,
                        cardCompany,
                        first.route,
                        first.destTc,
                        display.name.ifEmpty { fallback.name },
                        display.id.ifEmpty { fallback.id },
                        note,
                        trips,
                        group.circular,
                        style
                    )
                )
            }
        }
        // 同號唔同公司排埋一齊，方便一眼睇到「分開顯示」。
        return out.sortedWith(
            compareBy<RouteCard> { it.route }
                .thenBy { it.company }
                .thenBy { it.dest }
        )
    }

    private fun tripOf(e: EtaItem, cardCompany: String, showOperator: Boolean): Trip {
        val now = System.currentTimeMillis()
        val diff = ((e.etaMillis - now) / 60_000L).toInt()
        val remark = e.remarkTc

        // The official app marks "no service in this period" with a dash icon.
        val noService = remark.contains("沒有服務")
            || remark.contains("無服務")
            || remark.contains("暫停服務")
            || remark.contains("時段沒有服務")

        val label = when {
            noService -> "—"
            e.etaMillis <= 0L || diff <= 0 -> "-"
            diff > 60 -> TimeUtils.hhmm(e.etaMillis)
            else -> diff.toString()
        }
        val tag = when {
            noService -> "沒有服務"
            remark.contains("預定") -> "預定班次"
            remark.contains("改道") -> "改道"
            label == "-" -> "剛到站"
            remark.contains("即將") -> "即將到站"
            else -> ""
        }
        val clock = if (e.etaMillis > 0L) TimeUtils.hhmm(e.etaMillis) + " HKT" else ""
        // JOINT_KMB_CTB + JOINT_EVERY_TRIP_OPERATOR: 聯營路線 EVERY trip
        // names its own operator; on a single-operator card keep the old
        // "only if different" behaviour. tripLabel() 判唔到就用 company code
        // 如實降級，唔留空、唔估第二間公司。
        val op = if (showOperator || e.company != cardCompany) e.company else ""
        val opText = if (showOperator)
            GovServiceHours.Brand.tripLabel(e.company, e.route)
                .ifEmpty { JointRoutes.operatorLabel(e.company) }
            else ""
        return Trip(label, clock, tag, op, opText)
    }
}
