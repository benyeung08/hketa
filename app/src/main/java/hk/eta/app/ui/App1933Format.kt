package hk.eta.app.ui

import hk.eta.app.api.EtaModels.EtaItem
import hk.eta.app.api.TimeUtils

/**
 * APP1933-style presentation of the SAME open-data EtaItem list.
 *
 * This is a pure formatting layer: it does NOT touch EtaRepository, GeoStops,
 * RouteDataStore or any of the 8 operators. The official app shows
 *   route no. | 往 destination | stop name (stop id) | big minutes on the right
 * and, after picking a route+stop, the NEXT THREE arrivals with markers for
 * "-" (just arrived), scheduled trips and no-service periods.
 */

/** One of the (up to) three arrivals shown for a route card. */
data class Trip(
    val label: String,      // "13", "-", "—", "20:46"
    val clock: String,      // "13:25 HKT" or ""
    val tag: String,        // "預定班次" / "沒有服務" / "剛到站" / "改道" / ""
    val operator: String    // joint-operator company, or ""
)

/** One row of the APP1933 home page. */
data class RouteCard(
    val rank: Int,          // nearby-stop order (used for sorting)
    val company: String,
    val route: String,
    val dest: String,
    val stopName: String,
    val stopId: String,
    val distanceNote: String,
    val trips: List<Trip>   // at most 3
)

object App1933Format {

    /** "九龍灣臨華街 (KT123)" — the official app puts the stop id in brackets. */
    fun stopLabel(name: String, id: String): String = when {
        id.isEmpty() -> name
        name.isEmpty() -> id
        else -> "$name ($id)"
    }

    /**
     * Group a flat ETA list by (company, route, stop, destination), keep the
     * three earliest arrivals of each group, and turn them into home-page cards.
     */
    fun cardsFrom(
        etas: List<EtaItem>,
        stopName: String,
        stopId: String,
        note: String,
        rank: Int = 0
    ): List<RouteCard> {
        val groups = LinkedHashMap<String, MutableList<EtaItem>>()
        for (e in etas) {
            val key = e.company + "|" + e.route + "|" + e.stopId + "|" + e.destTc
            groups.getOrPut(key) { ArrayList() }.add(e)
        }
        val out = ArrayList<RouteCard>()
        for ((_, list) in groups) {
            val sorted = list.sortedBy { it.etaMillis }
            val first = sorted.firstOrNull() ?: continue
            val trips = sorted.take(3).map { tripOf(it, first.company) }
            out.add(
                RouteCard(
                    rank,
                    first.company,
                    first.route,
                    first.destTc,
                    stopName,
                    if (stopId.isEmpty()) first.stopId else stopId,
                    note,
                    trips
                )
            )
        }
        return out.sortedBy { it.route }
    }

    private fun tripOf(e: EtaItem, cardCompany: String): Trip {
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
        // Jointly-operated routes: show which company runs this particular trip.
        val op = if (e.company != cardCompany) e.company else ""
        return Trip(label, clock, tag, op)
    }
}
