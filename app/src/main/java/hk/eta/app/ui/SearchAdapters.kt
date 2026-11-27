package hk.eta.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import hk.eta.app.R
import hk.eta.app.api.EtaRepository
import hk.eta.app.api.JointRoutes
import hk.eta.app.api.GovServiceHours

/** KMB_LWB_SPLIT: 營運商標記按官方 GTFS agency 分開，UNKNOWN 留空唔估。 */
private fun operatorBadge(company: String, route: String): String =
    GovServiceHours.Brand.badge(company, route)

/**
 * 「搜尋紀錄」列：綠色時鐘（只第一條）＋營運商標記＋「九巴 272P」
 * ＋「往 葵興站」＋備註＋右側 ✕。
 * preloaded 列（官方目錄預載）冇得移除，亦唔寫入歷史。
 */
class SearchHistoryAdapter(
    private val onClick: (SearchHistoryItem) -> Unit,
    private val onRemove: (SearchHistoryItem) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.VH>() {

    private var items: List<SearchHistoryItem> = emptyList()

    fun submit(list: List<SearchHistoryItem>) {
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
        holder.clock.visibility =
            if (position == 0 && !item.preloaded) View.VISIBLE else View.GONE
        val badge = operatorBadge(item.company, item.route)
        holder.badge.text = badge
        holder.badge.visibility = if (badge.isEmpty()) View.GONE else View.VISIBLE
        val label = App1933Format.companyLabel(item.company, item.route)
        holder.route.text = if (label.isEmpty()) item.route else "$label ${item.route}"
        // 官方目錄得目的地，冇起點：如實顯示「往 XXX」，唔捏造「A 至 B」。
        holder.dest.text = item.dest

        val bits = mutableListOf<String>()
        if (item.joint) bits.add(GovServiceHours.Brand.jointLabel(item.route))
        if (item.circular && item.style.isNotEmpty()) bits.add(item.style)
        if (item.preloaded) {
            bits.add(holder.root.context.getString(R.string.search_preloaded_tag))
        }
        if (item.note.isNotEmpty()) bits.add(item.note)
        val note = bits.distinct().joinToString(" · ")
        holder.note.text = note
        holder.note.visibility = if (note.isEmpty()) View.GONE else View.VISIBLE

        holder.remove.visibility = if (item.removable) View.VISIBLE else View.GONE
        holder.remove.setOnClickListener { onRemove(item) }
        holder.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.resultRoot)
        val clock: ImageView = view.findViewById(R.id.resultClock)
        val badge: TextView = view.findViewById(R.id.resultBadge)
        val route: TextView = view.findViewById(R.id.resultRoute)
        val dest: TextView = view.findViewById(R.id.resultDest)
        val note: TextView = view.findViewById(R.id.resultNote)
        val remove: ImageView = view.findViewById(R.id.resultRemove)
    }
}
