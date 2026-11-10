package hk.eta.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** 首頁：路線搜尋 + 營運商篩選 + 雙語切換 + 資料來源切換。 */
class MainActivity : Activity() {

    private lateinit var appSubtitle: TextView
    private lateinit var langBtn: Button
    private lateinit var searchEdit: EditText
    private lateinit var modeRow: LinearLayout
    private lateinit var filterRow: LinearLayout
    private lateinit var routeList: ListView
    private lateinit var statusText: TextView
    private lateinit var sourceBtn: Button
    private lateinit var refreshBtn: Button
    private lateinit var settingsBtn: Button

    private var currentMode = "ALL"
    private var currentCo = "ALL"
    private var query = ""
    private var adapter: RouteAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 永久簽名一致性：設定咗預期指紋就先核對，唔夾就唔載入資料。
        if (!SignatureVerifier.isConsistent(this)) {
            showSignatureMismatch()
            return
        }

        appSubtitle = findViewById(R.id.appSubtitle)
        langBtn = findViewById(R.id.langBtn)
        searchEdit = findViewById(R.id.searchEdit)
        modeRow = findViewById(R.id.modeRow)
        filterRow = findViewById(R.id.filterRow)
        routeList = findViewById(R.id.routeList)
        statusText = findViewById(R.id.statusText)
        sourceBtn = findViewById(R.id.sourceBtn)
        refreshBtn = findViewById(R.id.refreshBtn)
        settingsBtn = findViewById(R.id.settingsBtn)

        // 優先載入「設定 → 路線資料更新」已驗證並快取嘅資料；冇就落內建 JSON。
        if (AppState.data == null) RouteDataUpdate.loadInitial(this)

        val listAdapter = RouteAdapter(this, emptyList())
        adapter = listAdapter
        routeList.adapter = listAdapter
        routeList.emptyView = findViewById<View>(R.id.routeEmpty)
        routeList.setOnItemClickListener { _, _, pos, _ ->
            val r = adapter?.getItem(pos) ?: return@setOnItemClickListener
            val i = Intent(this, RouteDetailActivity::class.java)
            i.putExtra("routeId", r.id)
            startActivity(i)
        }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString() ?: ""
                refreshList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        langBtn.setOnClickListener { toggleLang() }
        // 「資料更新」集中喺設定頁，連同 APK 更新一齊管理
        sourceBtn.setOnClickListener { openDataSettings() }
        refreshBtn.setOnClickListener { refreshAll() }
        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        rebuild()
    }

    /** 由設定頁改咗語系返嚟，即時套用。 */
    override fun onResume() {
        super.onResume()
        if (AppState.data != null && ::appSubtitle.isInitialized) rebuild()
    }

    private fun rebuild() {
        applyTexts()
        buildModeRow()
        buildFilterRow()
        refreshList()
    }

    /** 開設定頁並捲去「資料更新」區。 */
    private fun openDataSettings() {
        val i = Intent(this, SettingsActivity::class.java)
        i.putExtra("focus", "data")
        startActivity(i)
    }

    private fun applyTexts() {
        val lang = AppState.lang
        appSubtitle.text = Fmt.zh(lang, "香港公共交通到站時間", "HK Public Transport ETA")
        langBtn.text = if (lang == Lang.ZH) "EN" else "中"
        searchEdit.hint = Fmt.zh(lang, "搜尋路線／起終點", "Search route / origin / dest")
        sourceBtn.text = Fmt.zh(lang, "資料更新", "Data update")
        refreshBtn.text = Fmt.zh(lang, "重新整理", "Refresh")
        settingsBtn.text = Fmt.zh(lang, "設定", "Settings")
        AppState.sourceLabel = when {
            AppState.dataCached -> Fmt.zh(lang, "線上路線資料", "Online route data")
            AppState.sourceLabel.startsWith("GitHub") -> "GitHub JSON"
            lang == Lang.ZH -> "內建 JSON"
            else -> "Bundled JSON"
        }
    }

    /** 交通模式 chips：全部／巴士／專線小巴／地鐵／輕鐵。 */
    private fun buildModeRow() {
        modeRow.removeAllViews()
        val data = AppState.get()
        val items = listOf(TransportMode("ALL", "全部", "All")) + data.modes
        for (m in items) {
            val count = if (m.id == "ALL") data.routes.size else data.modeCount(m.id)
            val b = Button(this)
            b.text = Fmt.zh(AppState.lang, "${m.name(AppState.lang)} $count", "${m.name(AppState.lang)} $count")
            b.textSize = 12f
            b.setPadding(22, 8, 22, 8)
            val active = m.id == currentMode
            b.setBackgroundResource(if (active) R.drawable.chip_active else R.drawable.chip)
            b.setTextColor(if (active) Fmt.white else Fmt.primary)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 12, 0)
            b.layoutParams = lp
            b.setOnClickListener {
                currentMode = m.id
                buildModeRow()
                refreshList()
            }
            modeRow.addView(b)
        }
    }

    private fun buildFilterRow() {
        filterRow.removeAllViews()
        val ops = listOf(Operator("ALL", "全部", "All")) + AppState.get().operators
        for (op in ops) {
            val b = Button(this)
            b.text = op.name(AppState.lang)
            b.textSize = 13f
            b.setPadding(26, 10, 26, 10)
            val active = op.id == currentCo
            b.setBackgroundResource(if (active) R.drawable.chip_active else R.drawable.chip)
            b.setTextColor(if (active) Fmt.white else Fmt.primary)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 12, 0)
            b.layoutParams = lp
            b.setOnClickListener {
                currentCo = op.id
                buildFilterRow()
                refreshList()
            }
            filterRow.addView(b)
        }
    }

    private fun refreshList() {
        val lang = AppState.lang
        val q = query.trim()
        val data = AppState.get()
        val filtered = data.routes.filter { r ->
            val modeOk = currentMode == "ALL" || r.mode == currentMode
            val coOk = currentCo == "ALL" || r.co == currentCo
            val textOk = q.isEmpty() || r.number.contains(q, true) ||
                r.name.zh.contains(q, true) || r.name.en.contains(q, true) ||
                r.bounds.any { b ->
                    b.orig.zh.contains(q, true) || b.orig.en.contains(q, true) ||
                        b.dest.zh.contains(q, true) || b.dest.en.contains(q, true)
                }
            modeOk && coOk && textOk
        }
        adapter?.update(filtered)
        val modes = listOf("bus", "minibus", "metro", "light_rail")
            .joinToString("/") { data.modeCount(it).toString() }
        statusText.text = Fmt.zh(
            lang,
            "共 ${filtered.size} 條路線 · 巴/小/鐵/輕 $modes · ${AppState.sourceLabel} · ${Fmt.nowTime()}",
            "${filtered.size} routes · bus/mini/metro/LR $modes · ${AppState.sourceLabel} · ${Fmt.nowTime()}"
        )
    }

    private fun toggleLang() {
        AppState.lang = if (AppState.lang == Lang.ZH) Lang.EN else Lang.ZH
        rebuild()
    }

    private fun refreshAll() {
        val fromGitHub = AppState.dataCached || AppState.sourceLabel.startsWith("GitHub")
        toast(
            Fmt.zh(
                AppState.lang,
                if (fromGitHub) "正在由 GitHub 更新資料…" else "正在重新整理…",
                if (fromGitHub) "Updating data from GitHub…" else "Refreshing…"
            )
        )
        DataUpdater.reload(this, fromGitHub) { ok, msg ->
            rebuild()
            toast(if (ok) msg else msg)
        }
    }

    /** 簽名唔夾 = 可能被重新打包，拒絕載入並清楚解釋。 */
    private fun showSignatureMismatch() {
        val lang = AppState.lang
        val got = SignatureVerifier.actual(this) ?: "—"
        AlertDialog.Builder(this)
            .setTitle(Fmt.zh(lang, "簽名不一致", "Signature mismatch"))
            .setMessage(
                Fmt.zh(
                    lang,
                    "此 APK 並非由官方永久金鑰簽署，可能被重新打包或竄改，已停止載入。\n\n實際憑證 SHA-256：\n$got",
                    "This APK was not signed with the official permanent key. It may have been repackaged or tampered with, so loading was stopped.\n\nActual cert SHA-256:\n$got"
                )
            )
            .setCancelable(false)
            .setPositiveButton(Fmt.zh(lang, "關閉", "Close")) { _, _ -> finish() }
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private class RouteAdapter(
        private val ctx: Activity,
        private var items: List<Route>
    ) : BaseAdapter() {

        fun update(newItems: List<Route>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Route = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: View.inflate(ctx, R.layout.item_route, null)
            val r = items[position]
            val lang = AppState.lang
            val b = r.bounds.firstOrNull()
            val data = AppState.get()
            v.findViewById<TextView>(R.id.routeNumber).text = r.number
            v.findViewById<TextView>(R.id.routeCo).text =
                data.operatorName(r.co, lang) + " · " + data.modeName(r.mode, lang)
            v.findViewById<TextView>(R.id.routeOrig).text = b?.orig?.of(lang) ?: ""
            v.findViewById<TextView>(R.id.routeDest).text =
                "↳ " + (b?.dest?.of(lang) ?: "")
            v.findViewById<TextView>(R.id.routeMeta).text =
                Fmt.fareShort(lang, r.fare) + " · " + Fmt.serviceShort(lang, r.services)
            return v
        }
    }
}
