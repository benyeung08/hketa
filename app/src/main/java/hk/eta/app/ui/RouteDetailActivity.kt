package hk.eta.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import hk.eta.app.R
import hk.eta.app.api.EtaModels.StopOption

/**
 * ROUTE_DETAIL_NO_MAP — opened when a nearby-route card on the Home page is
 * tapped. Reproduces the reference screenshot's red header and the lower
 * stop-sequence / next-three-arrivals list, WITHOUT any map view.
 *
 * All displayed data comes from the same public open-data layer:
 *   - stop sequence  -> EtaViewModel.loadStops()
 *   - three arrivals -> EtaViewModel.selectStop() + App1933Format.cardsFrom()
 * Fare and predicted alighting time are NOT in those APIs, so the layout shows
 * an explicit "—" plus a footer note instead of inventing "$5.6" / "15:31".
 *
 * The four side chips and three header buttons all have real actions (no dead
 * buttons): board / back to nearby / favourite / Settings, and refresh trips /
 * favourite / switch direction.
 */
class RouteDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROUTE = "detail_route"
        const val EXTRA_COMPANY = "detail_company"
        const val EXTRA_STOP_ID = "detail_stop_id"
        const val EXTRA_STOP_NAME = "detail_stop_name"
        const val EXTRA_DEST = "detail_dest"
        const val EXTRA_BOUND = "detail_bound"
    }

    private lateinit var vm: EtaViewModel
    private lateinit var adapter: DetailStopAdapter
    private lateinit var tvTitle: TextView
    private lateinit var tvBubble: TextView
    private lateinit var tvFooter: TextView

    private var route = ""
    private var company = ""
    private var stopId = ""
    private var stopName = ""
    private var dest = ""
    private var bound = ""
    private var selectedId = ""
    private var autoSelected = false
    private var emptyNotified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)

        route = intent.getStringExtra(EXTRA_ROUTE) ?: ""
        company = intent.getStringExtra(EXTRA_COMPANY) ?: ""
        stopId = intent.getStringExtra(EXTRA_STOP_ID) ?: ""
        stopName = intent.getStringExtra(EXTRA_STOP_NAME) ?: ""
        dest = intent.getStringExtra(EXTRA_DEST) ?: ""
        bound = intent.getStringExtra(EXTRA_BOUND) ?: ""
        selectedId = stopId

        tvTitle = findViewById(R.id.tvDetailTitle)
        tvBubble = findViewById(R.id.tvStopBubble)
        tvFooter = findViewById(R.id.tvDetailFooter)
        tvTitle.text = buildTitle()
        tvBubble.text = App1933Format.stopLabel(stopName, stopId).ifEmpty { route }

        vm = ViewModelProvider(this)[EtaViewModel::class.java]
        adapter = DetailStopAdapter { stop -> boardAt(stop) }
        findViewById<RecyclerView>(R.id.detailStopsRecycler).apply {
            layoutManager = LinearLayoutManager(this@RouteDetailActivity)
            adapter = this@RouteDetailActivity.adapter
        }

        wireActions()
        vm.state.observe(this) { render(it) }
        renderFooter()
        // No location permission needed here: this is a route/stop lookup only.
        // "ALL" / NEARBY are not real filterable bounds, so pass "" in that
        // case; a tapped search row's "O" / "I" / "1" is honoured as-is.
        val requestedBound = when (bound) {
            "", "ALL", "NEARBY" -> ""
            else -> bound
        }
        vm.loadStops(route, company, requestedBound)
    }

    override fun onResume() {
        super.onResume()
        // 同一個 ETA 自動刷新設定：返到詳情頁重啟輪詢 + 過咗間隔就補一次
        vm.onForeground()
        renderFooter()
    }

    override fun onPause() {
        vm.onBackground()
        super.onPause()
    }

    private fun renderFooter() {
        tvFooter.text = getString(R.string.detail_footer_honest) + "\n" +
                EtaRefreshPrefs.statusLabel(this)
    }

    /** "12 往 尖沙咀東（麼地道）(YT927)" — same order as the screenshot. */
    private fun buildTitle(): String {
        val head = if (dest.isNotEmpty()) "$route 往 $dest" else route
        return if (stopId.isNotEmpty()) "$head ($stopId)" else head
    }

    private fun wireActions() {
        findViewById<View>(R.id.btnDetailBack).setOnClickListener { finish() }
        findViewById<View>(R.id.detailActionTrips).setOnClickListener {
            vm.refresh()
            Toast.makeText(this, R.string.detail_trips_refreshed, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.detailActionFav).setOnClickListener { addFavourite() }
        findViewById<View>(R.id.detailActionDirection).setOnClickListener {
            autoSelected = false
            vm.toggleDirection()
        }
        findViewById<View>(R.id.chipBoard).setOnClickListener {
            val first = vm.state.value?.stops?.firstOrNull()
            if (first != null) boardAt(first)
        }
        findViewById<View>(R.id.chipNearby).setOnClickListener { finish() }
        findViewById<View>(R.id.chipFav).setOnClickListener { addFavourite() }
        findViewById<View>(R.id.chipMore).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun addFavourite() {
        val key = vm.addCurrentFavorite()
        if (key == null) {
            Toast.makeText(this, R.string.detail_fav_need, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                getString(R.string.detail_fav_ok, App1933Format.stopLabel(stopName, selectedId)),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun boardAt(stop: StopOption) {
        selectedId = stop.stopId
        stopName = stop.stopName.ifEmpty { stop.stopId }
        tvBubble.text = App1933Format.stopLabel(stopName, selectedId)
        vm.selectStop(stop)
    }

    private fun render(state: UiState) {
        val visible = if (state.activeBound.isNotEmpty()) {
            state.stops.filter { it.bound == state.activeBound || it.bound.isEmpty() }
        } else {
            state.stops
        }
        val cards = App1933Format.cardsFrom(state.etas, stopName, selectedId, "")
        val trips = (cards.firstOrNull { it.route == route } ?: cards.firstOrNull())
            ?.trips ?: emptyList()

        adapter.submit(visible, selectedId, trips)
        renderFooter()

        if (!autoSelected && visible.isNotEmpty()) {
            autoSelected = true
            val target = visible.firstOrNull { it.stopId == selectedId } ?: visible.first()
            selectedId = target.stopId
            stopName = target.stopName.ifEmpty { stopName }
            tvBubble.text = App1933Format.stopLabel(stopName, selectedId)
            vm.selectStop(target)
        }
        if (visible.isEmpty() && !state.loading && !emptyNotified) {
            emptyNotified = true
            Toast.makeText(this, R.string.detail_no_stops, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Timeline adapter: only the boarding stop expands the next three arrivals. */
class DetailStopAdapter(
    private val onClick: (StopOption) -> Unit
) : RecyclerView.Adapter<DetailStopAdapter.VH>() {

    private var stops: List<StopOption> = emptyList()
    private var selectedId = ""
    private var trips: List<Trip> = emptyList()

    fun submit(list: List<StopOption>, selected: String, next3: List<Trip>) {
        stops = list
        selectedId = selected
        trips = next3
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: View = v.findViewById(R.id.detailStopRoot)
        val title: TextView = v.findViewById(R.id.tvStopTitle)
        val fare: TextView = v.findViewById(R.id.tvFare)
        val dot: View = v.findViewById(R.id.detailDot)
        val alight: TextView = v.findViewById(R.id.tvAlight)
        val alightTime: TextView = v.findViewById(R.id.tvAlightTime)
        val container: View = v.findViewById(R.id.tripContainer)
        val noEta: TextView = v.findViewById(R.id.tvNoEta)
        val rows: List<View> = listOf(
            v.findViewById(R.id.tripRow1),
            v.findViewById(R.id.tripRow2),
            v.findViewById(R.id.tripRow3)
        )
        val mins: List<TextView> = listOf(
            v.findViewById(R.id.tripMin1),
            v.findViewById(R.id.tripMin2),
            v.findViewById(R.id.tripMin3)
        )
        val clocks: List<TextView> = listOf(
            v.findViewById(R.id.tripClock1),
            v.findViewById(R.id.tripClock2),
            v.findViewById(R.id.tripClock3)
        )
        val tags: List<TextView> = listOf(
            v.findViewById(R.id.tripTag1),
            v.findViewById(R.id.tripTag2),
            v.findViewById(R.id.tripTag3)
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detail_stop, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = stops.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val stop = stops[position]
        val selected = stop.stopId == selectedId
        holder.title.text = "${position + 1}. " +
                App1933Format.stopLabel(stop.stopName, stop.stopId)
        holder.fare.text = holder.itemView.context.getString(R.string.detail_fare_na)
        holder.dot.setBackgroundResource(
            if (selected) R.drawable.bg_detail_dot_on else R.drawable.bg_detail_dot_off
        )
        holder.alight.visibility = if (selected) View.GONE else View.VISIBLE
        holder.alightTime.visibility = if (selected) View.GONE else View.VISIBLE

        if (selected) {
            holder.container.visibility = View.VISIBLE
            if (trips.isEmpty()) {
                holder.noEta.visibility = View.VISIBLE
                for (row in holder.rows) row.visibility = View.GONE
            } else {
                holder.noEta.visibility = View.GONE
                for (i in holder.rows.indices) {
                    if (i < trips.size) {
                        val trip = trips[i]
                        holder.rows[i].visibility = View.VISIBLE
                        holder.mins[i].text = trip.label
                        holder.clocks[i].text = trip.clock.removeSuffix(" HKT")
                        holder.tags[i].text = listOf(trip.tag, trip.operator)
                            .filter { it.isNotEmpty() }.joinToString(" ")
                    } else {
                        holder.rows[i].visibility = View.GONE
                    }
                }
            }
        } else {
            holder.container.visibility = View.GONE
        }
        holder.root.setOnClickListener { onClick(stop) }
    }
}
