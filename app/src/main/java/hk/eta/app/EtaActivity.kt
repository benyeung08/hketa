package hk.eta.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView

/** 單站 ETA：班次時間、目的地、分鐘數、備註／月台、重新整理。 */
class EtaActivity : Activity() {

    private lateinit var etaTitle: TextView
    private lateinit var etaLangBtn: Button
    private lateinit var etaStopName: TextView
    private lateinit var etaStopSub: TextView
    private lateinit var etaList: ListView
    private lateinit var etaEmpty: TextView
    private lateinit var etaUpdated: TextView
    private lateinit var etaRefreshBtn: Button

    private var stopId = ""
    private var routeId = ""
    private var dir = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eta)

        etaTitle = findViewById(R.id.etaTitle)
        etaLangBtn = findViewById(R.id.etaLangBtn)
        etaStopName = findViewById(R.id.etaStopName)
        etaStopSub = findViewById(R.id.etaStopSub)
        etaList = findViewById(R.id.etaList)
        etaEmpty = findViewById(R.id.etaEmpty)
        etaUpdated = findViewById(R.id.etaUpdated)
        etaRefreshBtn = findViewById(R.id.etaRefreshBtn)

        stopId = intent.getStringExtra("stopId") ?: ""
        routeId = intent.getStringExtra("routeId") ?: ""
        dir = intent.getStringExtra("dir") ?: "O"

        etaList.emptyView = etaEmpty
        findViewById<Button>(R.id.etaBackBtn).setOnClickListener { finish() }
        etaLangBtn.setOnClickListener {
            AppState.lang = if (AppState.lang == Lang.ZH) Lang.EN else Lang.ZH
            rebuild()
        }
        etaRefreshBtn.setOnClickListener { rebuild() }

        rebuild()
    }

    private fun rebuild() {
        val data = AppState.get()
        val lang = AppState.lang
        val stop = data.stops[stopId]
        val route = data.routes.find { it.id == routeId }
        val bound = route?.bounds?.find { it.dir == dir } ?: route?.bounds?.firstOrNull()
        val name = stop?.name

        etaTitle.text = Fmt.zh(lang, "到站時間", "Arrival times")
        etaLangBtn.text = if (lang == Lang.ZH) "EN" else "中"
        etaStopName.text = name?.of(lang) ?: stopId
        etaStopSub.text = buildString {
            if (route != null) append("${data.operatorName(route.co, lang)} ${route.number}")
            if (bound != null) append(" · ${bound.dest.of(lang)}")
            if (name != null) {
                append("\n")
                append(name.of(if (lang == Lang.ZH) Lang.EN else Lang.ZH))
            }
        }
        etaRefreshBtn.text = Fmt.zh(lang, "重新整理", "Refresh")

        val etas = if (route != null && bound != null) {
            DemoEtaProvider.fetch(data, route, bound, stopId, lang)
        } else {
            data.etas[stopId]?.etas ?: emptyList()
        }

        etaList.adapter = EtaAdapter(this, etas)
        etaEmpty.text = Fmt.zh(lang, "暫無到站資料", "No arrival data")
        etaUpdated.text = Fmt.zh(
            lang,
            "最後更新：${data.etas[stopId]?.generatedTimestamp?.take(16) ?: data.generatedTimestamp.take(16)} · ${data.note.of(lang)}",
            "Updated: ${data.etas[stopId]?.generatedTimestamp?.take(16) ?: data.generatedTimestamp.take(16)} · ${data.note.of(lang)}"
        )
    }

    private class EtaAdapter(
        private val ctx: Activity,
        private val items: List<Eta>
    ) : BaseAdapter() {

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Eta = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: View.inflate(ctx, R.layout.item_eta, null)
            val e = items[position]
            val lang = AppState.lang

            val minutes = v.findViewById<TextView>(R.id.etaMinutes)
            minutes.text = Fmt.minutesText(lang, e.minutes)
            minutes.setTextColor(if ((e.minutes ?: 99) <= 3) Fmt.green else Fmt.primary)

            v.findViewById<TextView>(R.id.etaTime).text =
                e.time ?: Fmt.zh(lang, "—", "—")
            v.findViewById<TextView>(R.id.etaDest).text =
                e.dest?.of(lang) ?: Fmt.zh(lang, "往總站", "To terminus")

            val bits = mutableListOf<String>()
            if (e.scheduled) bits.add(Fmt.zh(lang, "原定班次", "Scheduled Bus"))
            e.remark?.of(lang)?.takeIf { it.isNotBlank() }?.let { bits.add(it) }
            e.platform?.let { bits.add(Fmt.zh(lang, "月台 $it", "Platform $it")) }
            v.findViewById<TextView>(R.id.etaRemark).text = bits.joinToString(" · ")

            return v
        }
    }
}
