package hk.eta.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** 路線詳情：去／回程切換、完整站位列表、逐站 ETA 與地圖。 */
class RouteDetailActivity : Activity() {

    private lateinit var detailTitle: TextView
    private lateinit var detailLangBtn: Button
    private lateinit var detailRouteNo: TextView
    private lateinit var detailOrig: TextView
    private lateinit var detailDest: TextView
    private lateinit var boundBtn: Button
    private lateinit var detailFare: TextView
    private lateinit var detailService: TextView
    private lateinit var detailInfoBtn: Button
    private lateinit var detailList: ListView
    private lateinit var detailStatus: TextView

    private var route: Route? = null
    private var boundIndex = 0

    data class StopRow(val seq: Int, val id: String, val stop: Stop?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)

        detailTitle = findViewById(R.id.detailTitle)
        detailLangBtn = findViewById(R.id.detailLangBtn)
        detailRouteNo = findViewById(R.id.detailRouteNo)
        detailOrig = findViewById(R.id.detailOrig)
        detailDest = findViewById(R.id.detailDest)
        boundBtn = findViewById(R.id.boundBtn)
        detailFare = findViewById(R.id.detailFare)
        detailService = findViewById(R.id.detailService)
        detailInfoBtn = findViewById(R.id.detailInfoBtn)
        detailList = findViewById(R.id.detailList)
        detailStatus = findViewById(R.id.detailStatus)

        val routeId = intent.getStringExtra("routeId") ?: ""
        route = AppState.get().routes.find { it.id == routeId }
        if (route == null) {
            finish()
            return
        }

        findViewById<Button>(R.id.backBtn).setOnClickListener { finish() }
        detailLangBtn.setOnClickListener {
            AppState.lang = if (AppState.lang == Lang.ZH) Lang.EN else Lang.ZH
            rebuild()
        }
        boundBtn.setOnClickListener {
            val r = route ?: return@setOnClickListener
            if (r.bounds.size > 1) {
                boundIndex = (boundIndex + 1) % r.bounds.size
                rebuild()
            }
        }
        detailInfoBtn.setOnClickListener { showFareServiceDialog() }
        detailList.setOnItemClickListener { _, _, pos, _ ->
            val r = route ?: return@setOnItemClickListener
            val b = r.bounds[boundIndex]
            val row = detailList.adapter.getItem(pos) as StopRow
            openEta(this, r, b, row.id)
        }

        rebuild()
    }

    private fun rebuild() {
        val r = route ?: return
        val b = r.bounds[boundIndex]
        val lang = AppState.lang
        val data = AppState.get()

        detailTitle.text = Fmt.zh(lang, "路線詳情", "Route details")
        detailLangBtn.text = if (lang == Lang.ZH) "EN" else "中"
        detailRouteNo.text = r.number
        detailOrig.text = b.orig.of(lang)
        detailDest.text = b.dest.of(lang)
        boundBtn.text = if (r.bounds.size > 1) {
            Fmt.zh(lang, "${b.label.of(lang)}（點擊切換）", "${b.label.of(lang)} (tap to switch)")
        } else {
            b.label.of(lang)
        }

        detailFare.text = Fmt.fareShort(lang, r.fare)
        detailService.text = Fmt.serviceShort(lang, r.services)
        detailInfoBtn.text = Fmt.zh(lang, "收費／服務班次詳情", "Fares / service schedule")

        val rows = b.stopIds.mapIndexed { i, sid -> StopRow(i + 1, sid, data.stops[sid]) }
        detailList.adapter = StopAdapter(this, r, b, rows)
        detailStatus.text = Fmt.zh(
            lang,
            "${data.modeName(r.mode, lang)} · ${data.operatorName(r.co, lang)} · ${rows.size} 個站位 · ${data.note.of(lang)}",
            "${data.modeName(r.mode, lang)} · ${data.operatorName(r.co, lang)} · ${rows.size} stops · ${data.note.of(lang)}"
        )
    }

    /** 完整收費 + 逐段服務班次 + 免責說明。 */
    private fun showFareServiceDialog() {
        val r = route ?: return
        val lang = AppState.lang
        val data = AppState.get()
        val sb = StringBuilder()
        sb.append(r.number).append(" · ").append(r.name.of(lang)).append('\n')
        sb.append(data.modeName(r.mode, lang)).append(" / ")
            .append(data.operatorName(r.co, lang)).append("\n\n")
        sb.append(Fmt.zh(lang, "收費", "Fares")).append('\n')
        sb.append(Fmt.fareDetail(lang, r.fare)).append("\n\n")
        sb.append(Fmt.zh(lang, "服務班次", "Service schedule")).append('\n')
        if (r.services.isEmpty()) {
            sb.append(Fmt.zh(lang, "暫無班次資料", "Schedule unavailable"))
        } else {
            r.services.forEachIndexed { i, s ->
                if (i > 0) sb.append('\n')
                sb.append(Fmt.serviceLine(lang, s))
            }
        }
        sb.append("\n\n").append(data.fareDisclaimer.of(lang))

        val tv = TextView(this)
        tv.text = sb.toString()
        tv.setTextIsSelectable(true)
        tv.textSize = 12f
        tv.setTextColor(Fmt.muted)
        tv.setPadding(32, 20, 32, 12)
        AlertDialog.Builder(this)
            .setTitle(Fmt.zh(lang, "收費／服務班次", "Fares & service schedule"))
            .setView(tv)
            .setPositiveButton(Fmt.zh(lang, "關閉", "Close"), null)
            .show()
    }

    private class StopAdapter(
        private val ctx: Activity,
        private val route: Route,
        private val bound: Bound,
        private val rows: List<StopRow>
    ) : BaseAdapter() {

        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): StopRow = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: View.inflate(ctx, R.layout.item_stop, null)
            val row = rows[position]
            val lang = AppState.lang
            val name = row.stop?.name

            v.findViewById<TextView>(R.id.stopSeq).text = row.seq.toString().padStart(2, '0')
            v.findViewById<TextView>(R.id.stopName).text = name?.of(lang) ?: row.id
            v.findViewById<TextView>(R.id.stopSub).text = if (name == null) "" else
                name.of(if (lang == Lang.ZH) Lang.EN else Lang.ZH)

            val etaBtn = v.findViewById<Button>(R.id.etaBtn)
            etaBtn.text = Fmt.zh(lang, "到站時間", "ETA")
            etaBtn.setOnClickListener { openEta(ctx, route, bound, row.id) }

            val mapBtn = v.findViewById<Button>(R.id.mapBtn)
            mapBtn.text = Fmt.zh(lang, "地圖", "Map")
            mapBtn.setOnClickListener { openMap(ctx, row.stop) }

            return v
        }
    }
}

/** 開 ETA 頁 */
internal fun openEta(ctx: Activity, route: Route, bound: Bound, stopId: String) {
    val i = Intent(ctx, EtaActivity::class.java)
    i.putExtra("routeId", route.id)
    i.putExtra("dir", bound.dir)
    i.putExtra("stopId", stopId)
    ctx.startActivity(i)
}

/** 以 geo: 開啟手機內任何地圖 App；沒裝就提示，不閃退。 */
internal fun openMap(ctx: Activity, stop: Stop?) {
    if (stop == null) {
        Toast.makeText(ctx, "沒有座標資料 / No coordinates", Toast.LENGTH_SHORT).show()
        return
    }
    val label = Uri.encode(stop.name.zh)
    val uri = Uri.parse("geo:${stop.lat},${stop.lng}?q=${stop.lat},${stop.lng}($label)")
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        Toast.makeText(ctx, "找不到地圖 App / No map app", Toast.LENGTH_SHORT).show()
    }
}
