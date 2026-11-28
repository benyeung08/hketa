package hk.eta.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import hk.eta.app.R
import hk.eta.app.api.EtaModels.EtaItem
import hk.eta.app.api.EtaModels.StopOption
import hk.eta.app.api.CircularRoutes
import hk.eta.app.api.GovServiceHours
import hk.eta.app.api.RouteDataStore
import hk.eta.app.api.JointRoutes
import hk.eta.app.api.LocalServiceHours
import hk.eta.app.api.TimeUtils

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
 * DETAIL_THREE_ICONS — all non-circular (one-way) routes show EXACTLY these
 * three red-vector circles in the header, matching the first reference image:
 *   ① calendar  -> 服務時間及班次 (from the LOCAL route-data snapshot,
 *                   LocalServiceHours.lookup — no live API, no network)
 *   ② notice     -> 服務通告 (official ETA remark_tc, never invented)
 *   ③ ⇅ direction -> vm.toggleDirection()
 * The four side chips keep their real actions (board / nearby / favourite /
 * Settings); favourite is still reachable from chipFav.
 *
 * CIRCULAR_TWO_ACTIONS: a circular route has NO inbound direction, so the
 * third circle (⇅ switch direction) is hidden and the red header shows
 * exactly TWO icons — calendar (STILL 服務時間及班次, same dialog as a
 * one-way route) + notice (服務通告) — second reference image.
 * Normal routes keep all three (first reference image). The single source of
 * truth is CircularRoutes.visibleHeaderActions(boolean).
 *
 * HONEST DATA: the LOCAL official route catalogue does NOT publish first/last
 * bus times, exact departure times or a headway. The calendar dialog therefore
 * lists ONLY the route rows actually present in the local snapshot (direction
 * x service type) and explicitly says the timetable is unavailable — no
 * invented numbers, same rule as the fare / alighting-time "—" placeholders.
 * Live ETA is still used for the three arrival chips and the notice dialog;
 * it is never cached into the local catalogue.
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
    private lateinit var tvNoMapLabel: TextView

    private var route = ""
    private var company = ""
    private var stopId = ""
    private var stopName = ""
    private var dest = ""
    private var bound = ""
    private var selectedId = ""
    private var autoSelected = false
    /** SECOND_STOP_PREDICTIONS：同一組站序／上車站／方向只查一次官方 ETA。 */
    private var predictKey = ""
    private var emptyNotified = false
    private var circular = false
    private var auditText = ""
    /** DIRECTION_TITLE_SYNC: 上一次用嚟渲染紅頂標題嘅方向，避免重複刷新。 */
    private var titleBound = ""
    /** DETAIL_THREE_ICONS: real ETA rows backing the calendar / notice dialogs. */
    private var latestEtas: List<EtaItem> = emptyList()

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
        tvNoMapLabel = findViewById(R.id.tvNoMapLabel)
        // CIRCULAR_ROUTES: recognised from the OFFICIAL destination suffix.
        circular = CircularRoutes.isCircularDest(dest)
                && CircularRoutes.supports(company)
        tvTitle.text = buildTitle()
        tvBubble.text = App1933Format.stopLabel(stopName, stopId).ifEmpty { route }
        // CIRCULAR_DISPLAY / JOINT_KMB_CTB: the caption names the exact way.
        tvNoMapLabel.text = buildCaption()

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
        titleBound = requestedBound
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
                EtaRefreshPrefs.statusLabel(this) +
                if (auditText.isEmpty()) "" else "\n" + auditText
    }

    /**
     * "12 往 尖沙咀東（麼地道）(YT927)" normally.
     * CIRCULAR_DISPLAY: KMB shows "213X 往 尖沙咀(循環線) (KT123)" the
     * App1933 way; Citybus shows "26 勵德邨 - 荷李活道(循環線) (002891)"
     * the Citybus way. The suffix always comes from the official dest.
     */
    /** DIRECTION_TITLE_SYNC: 抽出嚟，切換方向後可以同標題一齊重算。 */
    private fun buildCaption(): String = when {
        circular -> getString(R.string.detail_no_map_label) +
                " · " + CircularRoutes.styleLabel(company, route) +
                " · " + CircularRoutes.displayBadge(company, route, dest)
        JointRoutes.isJointCompany(company) ->
                getString(R.string.detail_no_map_label) + " · " +
                GovServiceHours.Brand.label(company, route)
        else -> getString(R.string.detail_no_map_label)
    }

    /**
     * DIRECTION_TITLE_SYNC — 之前 `toggleDirection()` 只改站序 filter，
     * 紅頂「1 往 尖沙咀碼頭」永遠停留喺入頁時嘅 intent dest。而家
     * ViewModel 已經計好該方向嘅官方目的地（目錄 dest_tc／站序終點站名），
     * 呢度把 `dest`、標題同 caption 一次過同步，零 IO、唔捏造。
     * 循環線冇 ⇅ 掣，直接跳過。
     */
    private fun syncDirectionTitle(state: UiState) {
        if (circular) return
        val b = state.activeBound
        if (b.isEmpty() || b == titleBound) return
        titleBound = b
        val next = state.activeDest
        if (next.isEmpty() || next == dest) return
        dest = next
        tvTitle.text = buildTitle()
        tvNoMapLabel.text = buildCaption()
    }

    private fun buildTitle(): String {
        val head = if (circular && dest.isNotEmpty()) {
            CircularRoutes.displayTitle(company, route, dest)
        } else if (dest.isNotEmpty()) {
            "$route 往 $dest"
        } else {
            route
        }
        return if (stopId.isNotEmpty()) "$head ($stopId)" else head
    }

    private fun wireActions() {
        findViewById<View>(R.id.btnDetailBack).setOnClickListener { finish() }
        // ① 日曆＝服務時間及班次；② 通知＝服務通告；③ ⇅＝切換方向。
        // 三顆全部有實際功能，收藏仍然由左側 chipFav 提供。
        findViewById<View>(R.id.detailActionCalendar)
            .setOnClickListener { showServiceHours() }
        findViewById<View>(R.id.detailActionNotice)
            .setOnClickListener { showNotices() }
        val directionAction = findViewById<View>(R.id.detailActionDirection)
        // CIRCULAR_TWO_ACTIONS: 由單一純決策列表顯式設定三顆 ——
        // 循環線 = 日曆（服務時間及班次）＋ 通知兩顆 VISIBLE、第三顆 ⇅ GONE；
        // 單向路線 = 三顆 VISIBLE。兩顆入面日曆嘅功能同單向完全一樣。
        val actions = CircularRoutes.visibleHeaderActions(circular)
        findViewById<View>(R.id.detailActionCalendar).visibility =
            if (actions.contains(CircularRoutes.ACTION_CALENDAR)) View.VISIBLE else View.GONE
        findViewById<View>(R.id.detailActionNotice).visibility =
            if (actions.contains(CircularRoutes.ACTION_NOTICE)) View.VISIBLE else View.GONE
        directionAction.visibility = if (CircularRoutes.showDirectionAction(circular)) {
            View.VISIBLE
        } else {
            View.GONE
        }
        directionAction.setOnClickListener {
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

    /**
     * ① 日曆圖標：服務時間及班次（GOV_SERVICE_HOURS）——
     * **由政府公開資料獲取**：運輸署「公共交通服務班次資料」GTFS 本機快取，
     * 提供服務日／頭班／尾班／班次間隔。未載入或冇呢條路線就如實降級去
     * LocalServiceHours 本機官方目錄列，兩者都唔捏造時間表、唔快取即時 ETA。
     * 循環線同單向路線共用呢個 dialog，內容完全一致。
     */
    private fun showServiceHours() {
        // GOV_SERVICE_HOURS: 服務時間及班次由政府公開資料（DATA.GOV.HK
        // 運輸署 GTFS）獲取；本機快取由開 App 更新一次／設定頁更新寫入。
        val gov = GovServiceHours.lookup(
            RouteDataStore.serviceHoursDir(), company, route, "")
        val body = StringBuilder()
        body.append(getString(R.string.detail_calendar_gov_header))
            .append('\n')
            .append(GovServiceHours.SOURCE_LABEL)
        if (gov.lastUpdated.isNotEmpty()) {
            body.append(" · ")
                .append(getString(R.string.detail_calendar_gov_updated))
                .append(" ").append(gov.lastUpdated)
        }
        body.append("\n").append(GovServiceHours.DATASET_PAGE).append("\n\n")

        if (gov.available) {
            if (gov.serviceDays.isNotEmpty()) {
                body.append(getString(R.string.detail_calendar_service_days))
                    .append(" ").append(gov.serviceDays).append('\n')
            }
            if (gov.firstTrip.isNotEmpty() || gov.lastTrip.isNotEmpty()) {
                body.append(getString(R.string.detail_calendar_first_last))
                    .append(" ").append(gov.firstTrip.ifEmpty { "—" })
                    .append(" / ").append(gov.lastTrip.ifEmpty { "—" })
                    .append('\n')
            }
            if (gov.serviceStart.isNotEmpty()) {
                body.append(getString(R.string.detail_calendar_service_span))
                    .append(" ").append(gov.serviceStart)
                    .append("–").append(gov.serviceEnd).append('\n')
            }
            if (gov.headways.isNotEmpty()) {
                body.append(getString(R.string.detail_calendar_headway)).append('\n')
                gov.headways.take(6).forEach { h ->
                    body.append("  • ").append(h.label()).append('\n')
                }
                if (gov.headways.size > 6) {
                    body.append("  … ").append(getString(R.string.detail_calendar_more))
                        .append(" ").append(gov.headways.size - 6).append('\n')
                }
            } else {
                body.append(getString(R.string.detail_calendar_no_headway)).append('\n')
            }
            if (gov.specialDates > 0) {
                body.append(getString(R.string.detail_calendar_special))
                    .append(" ").append(gov.specialDates).append('\n')
            }
        } else {
            // 政府資料未載入／冇呢條路線：如實講原因，後備用本機官方目錄列。
            body.append(getString(R.string.detail_calendar_no_gov))
                .append(" ").append(gov.reason).append("\n\n")
            val info = LocalServiceHours.lookup(company, route)
            body.append(getString(R.string.detail_calendar_local_header)).append('\n')
                .append(info.sourceFile.ifEmpty { "—" })
                .append(" · ").append(info.sourceLabel)
            if (info.updatedAt > 0L) {
                body.append(" · 更新於 ")
                    .append(TimeUtils.hhmm(info.updatedAt)).append(" HKT")
            }
            body.append('\n')
            when {
                !info.hasSnapshot ->
                    body.append(getString(R.string.detail_calendar_no_snapshot))
                info.csvOnly ->
                    body.append(getString(R.string.detail_calendar_csv_only))
                info.rows.isEmpty() ->
                    body.append(getString(R.string.detail_calendar_no_eta))
                else -> info.rows.forEachIndexed { k, r ->
                    body.append("  ").append(k + 1).append(". ")
                        .append(r.label()).append('\n')
                }
            }
        }
        body.append('\n').append(getString(R.string.detail_calendar_no_timetable))
        body.append('\n').append(getString(R.string.detail_calendar_gov_note))
        body.append('\n').append(getString(R.string.detail_calendar_eta_note))

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.detail_calendar_title) + " · " + route)
            .setMessage(body.toString())
            .setPositiveButton(R.string.detail_dialog_ok, null)
            .setNeutralButton(R.string.detail_calendar_refresh) { _, _ ->
                // 去設定頁更新本機資料（會同時更新政府班次資料快取）。
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .show()
    }

    /**
     * ② 通知圖標：服務通告。內容只係官方 ETA 附帶嘅 remark_tc（去重），
     * 冇備註就如實講「暫無」，唔自己作通告。
     */
    private fun showNotices() {
        val remarks = latestEtas
            .map { it.remarkTc.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val body = StringBuilder()
        if (remarks.isEmpty()) {
            body.append(getString(R.string.detail_notice_empty))
        } else {
            body.append(getString(R.string.detail_notice_from_eta)).append('\n')
            remarks.forEach { body.append("  • ").append(it).append('\n') }
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.detail_notice_title) + " · " + route)
            .setMessage(body.toString())
            .setPositiveButton(R.string.detail_dialog_ok, null)
            // 通告仍係官方即時 ETA 嘅 remark_tc，保留手動刷新；
            // 日曆 dialog 改讀本機資料，兩者來源清楚分開。
            .setNeutralButton(R.string.detail_notice_refresh) { _, _ ->
                // NO_NETWORK_GATE：唔預先擋低；失敗由 ViewModel 如實報。
                vm.refresh()
                Toast.makeText(this, R.string.detail_trips_refreshed, Toast.LENGTH_SHORT).show()
            }
            .show()
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
        // DIRECTION_TITLE_SYNC: 撳 ⇅ 之後紅頂標題要跟住方向轉。
        syncDirectionTitle(state)
        // LOCAL_SERVICE_HOURS: 日曆 dialog 改讀本機路線資料；只有通知 dialog
        // 仍用呢批即時 ETA 嘅 remark_tc，唔另查、唔快取、唔捏造。
        latestEtas = state.etas
        val visible = if (state.activeBound.isNotEmpty()) {
            state.stops.filter { it.bound == state.activeBound || it.bound.isEmpty() }
        } else {
            state.stops
        }
        // CIRCULAR_NAME_CHECK: show the honest stop-name audit on this line.
        if (circular && state.stopAudit.isNotEmpty()) {
            auditText = state.stopAudit
        }
        renderFooter()
        val cards = App1933Format.cardsFrom(
            state.etas, stopName, selectedId, "", circularHint = circular
        )
        val trips = (cards.firstOrNull { it.route == route } ?: cards.firstOrNull())
            ?.trips ?: emptyList()

        // SECOND_STOP_PREDICTIONS：第二站起逐站查官方即時 ETA（唔係推算），
        // 用 key 去重，避免 state patch 觸發嘅 render 又再打一次 API。
        val key = visible.joinToString("|") { it.stopId } +
                "#" + selectedId + "#" + route + "#" + state.activeBound
        if (key != predictKey) {
            predictKey = key
            vm.loadStopPredictions(visible, selectedId, route)
        }
        adapter.submit(visible, selectedId, trips, state.stopPredictions)
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
    /** SECOND_STOP_PREDICTIONS: stopId -> (分鐘字, HH:mm)。 */
    private var predictions: Map<String, Pair<String, String>> = emptyMap()

    fun submit(
        list: List<StopOption>,
        selected: String,
        next3: List<Trip>,
        predicted: Map<String, Pair<String, String>>
    ) {
        stops = list
        selectedId = selected
        trips = next3
        predictions = predicted
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
        // SECOND_STOP_PREDICTIONS：兩層「預計到站時間」
        val predictRoot: View = v.findViewById(R.id.predictContainer)
        val predictMin: TextView = v.findViewById(R.id.tvPredictMin)
        val predictClock: TextView = v.findViewById(R.id.tvPredictClock)
        val predictNa: TextView = v.findViewById(R.id.tvPredictNa)
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
        // API_AUDIT_A4: 同一方向可以有多套 service_type 站序；
        // 逐列標明班次，唔好將唔同班次嘅同名站混成一列。
        val serviceTag = if (stop.serviceType > 1) " · 特別班次 ${stop.serviceType}" else ""
        holder.title.text = "${position + 1}. " +
                App1933Format.stopLabel(stop.stopName, stop.stopId) + serviceTag
        holder.fare.text = holder.itemView.context.getString(R.string.detail_fare_na)
        holder.dot.setBackgroundResource(
            if (selected) R.drawable.bg_detail_dot_on else R.drawable.bg_detail_dot_off
        )
        // 左邊時間軸嘅舊「落車／—」placeholder 唔再顯示；
        // 第二站起改由右邊內容區嘅兩層「預計到站」表達。
        holder.alight.visibility = View.GONE
        holder.alightTime.visibility = View.GONE

        if (selected) {
            holder.predictRoot.visibility = View.GONE
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
                        // JOINT_EVERY_TRIP_OPERATOR: 「6 分鐘 11:27」後面
                        // 每一班次都加「該班次：九巴／城巴／龍運」，用 ·
                        // 分隔同主頁 chip 一致；聯營路線永遠唔留空。
                        holder.tags[i].text = listOf(
                            trip.tag, App1933Format.tripOperatorText(trip, route)
                        ).filter { it.isNotEmpty() }.joinToString(" · ")
                    } else {
                        holder.rows[i].visibility = View.GONE
                    }
                }
            }
        } else {
            holder.container.visibility = View.GONE
            // SECOND_STOP_PREDICTIONS：第一層「×× 分鐘」、第二層「××:××」。
            // 數字係官方即時 ETA；官方冇回報就如實顯示 detail_predict_na。
            holder.predictRoot.visibility = View.VISIBLE
            val pred = predictions[stop.stopId]
            if (pred != null) {
                holder.predictMin.visibility = View.VISIBLE
                holder.predictClock.visibility = View.VISIBLE
                holder.predictNa.visibility = View.GONE
                holder.predictMin.text = pred.first + " " +
                        holder.itemView.context.getString(R.string.detail_predict_unit)
                holder.predictClock.text = pred.second
            } else {
                holder.predictMin.visibility = View.GONE
                holder.predictClock.visibility = View.GONE
                holder.predictNa.visibility = View.VISIBLE
            }
        }
        holder.root.setOnClickListener { onClick(stop) }
    }
}
