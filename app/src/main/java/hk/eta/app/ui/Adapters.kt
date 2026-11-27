package hk.eta.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import hk.eta.app.R
import hk.eta.app.api.EtaModels.EtaItem
import hk.eta.app.api.EtaModels.StopOption
import hk.eta.app.api.EtaRepository
import hk.eta.app.api.GovServiceHours
import hk.eta.app.api.TimeUtils

/** KMB_LWB_SPLIT: 站序／ETA 列嘅公司名都按路線歸屬分開。 */
private fun operatorLabel(company: String, route: String): String =
    GovServiceHours.Brand.label(company, route)

private fun operatorColor(company: String): Int = when (company) {
    EtaRepository.KMB -> R.color.kmb
    EtaRepository.CTB -> R.color.ctb
    EtaRepository.NLB -> R.color.nlb
    EtaRepository.GMB -> R.color.gmb
    EtaRepository.MTR -> R.color.mtr
    EtaRepository.LRT -> R.color.lrt
    EtaRepository.MTRB -> R.color.mtrb
    else -> R.color.brand
}

private fun directionLabel(bound: String): String = when (bound.uppercase()) {
    "O" -> "去程"
    "I" -> "回程"
    "1" -> "去程"
    "2" -> "回程"
    "UP" -> "上行"
    "DOWN" -> "下行"
    "ALL" -> "雙方向"
    "NEARBY" -> "附近"
    "" -> "全部"
    else -> bound
}

class StopAdapter(private val onClick: (StopOption) -> Unit) :
    RecyclerView.Adapter<StopAdapter.VH>() {

    private var items: List<StopOption> = emptyList()

    fun submit(list: List<StopOption>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_stop, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val stop = items[position]
        holder.seq.text = if (stop.bound == EtaRepository.NEARBY) {
            "${stop.seq} m"
        } else {
            "#${stop.seq}"
        }
        // APP1933 puts the stop id in brackets after the Chinese stop name.
        holder.name.text = App1933Format.stopLabel(stop.stopName, stop.stopId)
        val bits = mutableListOf<String>()
        operatorLabel(stop.company, stop.route).takeIf { it.isNotEmpty() }
            ?.let { bits.add(it) }
        if (stop.bound != EtaRepository.NEARBY) {
            bits.add(directionLabel(stop.bound))
        }
        if (stop.note.isNotEmpty()) {
            bits.add(stop.note)
        }
        holder.meta.text = bits.joinToString(" · ")
        holder.root.setOnClickListener { onClick(stop) }
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.stopRoot)
        val seq: TextView = view.findViewById(R.id.stopSeq)
        val name: TextView = view.findViewById(R.id.stopName)
        val meta: TextView = view.findViewById(R.id.stopMeta)
    }
}

class EtaAdapter : RecyclerView.Adapter<EtaAdapter.VH>() {

    private var items: List<EtaItem> = emptyList()

    fun submit(list: List<EtaItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_eta, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val eta = items[position]
        holder.route.text = eta.route
        holder.dest.text = eta.destTc.ifEmpty { "往總站" }

        val minutes = ((eta.etaMillis - System.currentTimeMillis()) / 60_000L)
            .toInt().coerceAtLeast(0)
        holder.minutes.text = when {
            minutes <= 0 -> "即將到站"
            minutes <= 60 -> "$minutes 分鐘"
            else -> TimeUtils.hhmm(eta.etaMillis)
        }

        holder.company.text = operatorLabel(eta.company, eta.route)
        holder.company.setTextColor(holder.company.context.getColor(operatorColor(eta.company)))
        holder.time.text = TimeUtils.hhmm(eta.etaMillis) + " HKT"

        val stamp = TimeUtils.parseHkIso(eta.dataTimestampIso)
            .takeIf { it > 0L } ?: TimeUtils.parseHkCompact(eta.dataTimestampIso)
        val stampText = if (stamp > 0L) " · 資料 ${TimeUtils.hhmm(stamp)}" else ""
        holder.meta.text = "第 ${eta.etaSeq} 班 · ${directionLabel(eta.dir)}$stampText"
        holder.remark.text = eta.remarkTc.ifEmpty { "實時預計到站時間" }
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val route: TextView = view.findViewById(R.id.etaRoute)
        val dest: TextView = view.findViewById(R.id.etaDest)
        val remark: TextView = view.findViewById(R.id.etaRemark)
        val meta: TextView = view.findViewById(R.id.etaMeta)
        val company: TextView = view.findViewById(R.id.etaCompany)
        val minutes: TextView = view.findViewById(R.id.etaMinutes)
        val time: TextView = view.findViewById(R.id.etaTime)
    }
}
