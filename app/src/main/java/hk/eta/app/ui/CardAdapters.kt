package hk.eta.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import hk.eta.app.R
import hk.eta.app.api.EtaRepository
import hk.eta.app.api.RouteSearch

private fun cardOperatorLabel(company: String): String = when (company) {
    EtaRepository.KMB -> "九巴 / 龍運"
    EtaRepository.CTB -> "城巴"
    EtaRepository.NLB -> "嶼巴"
    EtaRepository.GMB -> "綠色小巴"
    EtaRepository.MTR -> "港鐵重鐵"
    EtaRepository.LRT -> "輕鐵"
    EtaRepository.MTRB -> "港鐵接駁巴士"
    else -> company
}

/** APP1933 home page: one row per route+stop, up to three arrivals. */
class RouteCardAdapter(
    private val onClick: (RouteCard) -> Unit
) : RecyclerView.Adapter<RouteCardAdapter.VH>() {

    private var items: List<RouteCard> = emptyList()

    fun submit(list: List<RouteCard>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_route_card, parent, false)
        )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val card = items[position]
        holder.route.text = card.route
        holder.dest.text = if (card.dest.isEmpty()) "往總站" else "往 " + card.dest
        holder.stop.text = App1933Format.stopLabel(card.stopName, card.stopId)
        holder.meta.text = cardOperatorLabel(card.company) +
            if (card.distanceNote.isEmpty()) "" else " · " + card.distanceNote

        val chips = arrayOf(holder.trip1, holder.trip2, holder.trip3)
        for (i in chips.indices) {
            if (i < card.trips.size) {
                val t = card.trips[i]
                val numeric = t.label.isNotEmpty() && t.label.all { it.isDigit() }
                val base = if (numeric) t.label + " 分鐘" else t.label
                var text = if (t.tag.isEmpty()) base else base + " · " + t.tag
                if (t.operator.isNotEmpty()) {
                    text = text + " · " + cardOperatorLabel(t.operator)
                }
                chips[i].text = text
                chips[i].visibility = View.VISIBLE
            } else {
                chips[i].visibility = View.GONE
            }
        }

        val first = card.trips.firstOrNull()
        holder.minutes.text = first?.label ?: "—"
        val numericFirst = first != null && first.label.isNotEmpty() &&
            first.label.all { it.isDigit() }
        holder.unit.text = if (numericFirst) {
            holder.unit.context.getString(R.string.unit_minutes)
        } else {
            ""
        }
        holder.clock.text = first?.clock ?: ""
        holder.root.setOnClickListener { onClick(card) }
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.cardRoot)
        val route: TextView = view.findViewById(R.id.cardRoute)
        val dest: TextView = view.findViewById(R.id.cardDest)
        val stop: TextView = view.findViewById(R.id.cardStop)
        val meta: TextView = view.findViewById(R.id.cardMeta)
        val trip1: TextView = view.findViewById(R.id.cardTrip1)
        val trip2: TextView = view.findViewById(R.id.cardTrip2)
        val trip3: TextView = view.findViewById(R.id.cardTrip3)
        val minutes: TextView = view.findViewById(R.id.cardMinutes)
        val unit: TextView = view.findViewById(R.id.cardUnit)
        val clock: TextView = view.findViewById(R.id.cardClock)
    }
}

/** Favourites tab: tap to open, long-press to remove. */
class FavoriteAdapter(
    private val onClick: (String) -> Unit,
    private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<FavoriteAdapter.VH>() {

    private var items: List<String> = emptyList()

    fun submit(list: List<String>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_favorite, parent, false)
        )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val key = items[position]
        val parts = key.split("|")
        holder.title.text = if (parts.size >= 3) {
            parts[1] + " · " + parts[2]
        } else {
            key
        }
        holder.meta.text = key.replace("|", " · ")
        holder.root.setOnClickListener { onClick(key) }
        holder.root.setOnLongClickListener {
            onRemove(key)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.favRoot)
        val title: TextView = view.findViewById(R.id.favTitle)
        val meta: TextView = view.findViewById(R.id.favMeta)
    }
}

/**
 * APP1933 route-search result: big route number, "往 destination", an
 * optional operator / DATA.GOV.HK attribution line and a chevron.
 * Tapping a row opens that route's stop sequence (unchanged Java layer).
 */
class RouteResultAdapter(
    private val onClick: (RouteSearch.Result) -> Unit
) : RecyclerView.Adapter<RouteResultAdapter.VH>() {

    private var items: List<RouteSearch.Result> = emptyList()

    fun submit(list: List<RouteSearch.Result>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_route_result, parent, false)
        )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.route.text = item.route
        holder.dest.text = item.dest
        holder.note.text = item.note
        holder.note.visibility = if (item.note.isEmpty()) View.GONE else View.VISIBLE
        holder.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.resultRoot)
        val route: TextView = view.findViewById(R.id.resultRoute)
        val dest: TextView = view.findViewById(R.id.resultDest)
        val note: TextView = view.findViewById(R.id.resultNote)
    }
}
