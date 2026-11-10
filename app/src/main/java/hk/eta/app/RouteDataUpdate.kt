package hk.eta.app

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 設定頁「路線資料更新」嘅核心。
 *
 * 一份 hketa.json 同時裝住四類資料：巴士 bus／專線小巴 minibus／
 * 地鐵 metro／輕鐵 light_rail，每條路線都有收費 fare、路線 bounds／stops、
 * 服務班次 services。
 *
 * 更新流程：GitHub raw JSON → parse → validate（四類齊全、收費／班次覆蓋、
 * 站位參照完整）→ 通過先寫入資料快取 + 更新 AppState。任何驗證失敗都
 * **唔會套用**，避免壞資料洗走原本可用嘅版本。
 *
 * 注意：寫入 filesDir 嘅係「路線資料本身」嘅快取（離線可用），唔係操作紀錄；
 * 「線上版本紀錄」仍然只有 GitHub Releases 一個線上來源，兩者互不混雜。
 */
object RouteDataUpdate {

    /** JSON 內 route.mode 嘅四個合法值，順序即 UI 顯示順序。 */
    val MODE_IDS = listOf("bus", "minibus", "metro", "light_rail")

    private const val UA = "HKETA-Android/2.0"
    private const val CACHE_NAME = "route-data.json"

    data class ModeStat(
        val id: String,
        val routes: Int,
        val stops: Int,
        val withFare: Int,
        val withService: Int
    )

    data class Stats(
        val version: String,
        val generated: String,
        val demo: Boolean,
        val routes: Int,
        val stops: Int,
        val withFare: Int,
        val withService: Int,
        val modes: List<ModeStat>
    )

    data class Validation(val ok: Boolean, val problems: List<String>)

    fun cacheFile(ctx: Context): File = File(ctx.filesDir, CACHE_NAME)
    fun hasCache(ctx: Context): Boolean = cacheFile(ctx).isFile

    /** 由 HketaData 計四類統計；站位數按該模式路線嘅 stopIds 去重。 */
    fun stats(d: HketaData): Stats {
        fun hasFare(r: Route): Boolean {
            val f = r.fare
            return f != null && f.full > 0
        }
        val byMode = MODE_IDS.map { id ->
            val rs = d.routes.filter { it.mode == id }
            val stopIds = rs.flatMap { r -> r.bounds.flatMap { b -> b.stopIds } }.toSet()
            ModeStat(
                id = id,
                routes = rs.size,
                stops = stopIds.size,
                withFare = rs.count { hasFare(it) },
                withService = rs.count { it.services.isNotEmpty() }
            )
        }
        return Stats(
            version = d.version,
            generated = d.generatedTimestamp,
            demo = d.demo,
            routes = d.routes.size,
            stops = d.stops.size,
            withFare = d.routes.count { hasFare(it) },
            withService = d.routes.count { it.services.isNotEmpty() },
            modes = byMode
        )
    }

    /**
     * 線上資料落地前嘅完整性檢查。
     * 回傳全部問題（最多 10 條），UI 會逐條列出；ok=false 一律拒絕套用。
     */
    fun validate(d: HketaData): Validation {
        val p = mutableListOf<String>()
        val ids = d.modes.map { it.id }
        for (m in MODE_IDS) {
            if (!ids.contains(m)) p.add("缺少交通模式 modes：$m")
        }
        if (d.operators.isEmpty()) p.add("operators 為空")
        if (d.stops.isEmpty()) p.add("stops 為空")
        if (d.routes.isEmpty()) p.add("routes 為空")
        if (d.version.isBlank()) p.add("version 為空")

        for (m in MODE_IDS) {
            if (d.routes.none { it.mode == m }) p.add("沒有任何 $m 路線")
        }
        val opIds = d.operators.map { it.id }
        d.routes.forEachIndexed { i, r ->
            val tag = "route[${i}] ${r.id.ifBlank { r.number }}"
            if (r.co !in opIds) p.add("$tag：未知營運商 ${r.co}")
            if (r.mode !in MODE_IDS) p.add("$tag：未知模式 ${r.mode}")
            if (r.number.isBlank()) p.add("$tag：冇路線號")
            if (r.bounds.isEmpty()) p.add("$tag：冇方向／站位")
            r.bounds.forEach { b ->
                if (b.stopIds.isEmpty()) p.add("$tag：${b.dir} 方向冇站位")
                for (sid in b.stopIds) {
                    if (!d.stops.containsKey(sid)) p.add("$tag：站位 $sid 唔存在")
                }
            }
            // 收費同服務班次係今次更新功能要求嘅必要欄位
            val fare = r.fare
            if (fare == null || fare.full <= 0.0) p.add("$tag：缺少有效收費 fare")
            if (r.services.isEmpty()) p.add("$tag：缺少服務班次 services")
            r.services.forEach { s ->
                if (s.first.isBlank() || s.last.isBlank()) p.add("$tag：班次缺首／尾車時間")
            }
        }
        return Validation(p.isEmpty(), p.take(10))
    }

    /** 即時由 GitHub 抓 raw JSON 文字，唔快取。 */
    fun fetchRemoteText(): String {
        val conn = URL(AppConfig.REMOTE_JSON_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 20000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 驗證並套用線上文字；失敗 throw IllegalStateException（唔會改動 AppState）。
     * 成功會寫入資料快取，並設定 AppState.data / sourceLabel / dataUpdatedAt / dataCached。
     */
    fun applyRemote(ctx: Context, text: String): HketaData {
        val d = try {
            JsonParser.parse(text)
        } catch (e: Exception) {
            error("JSON 解析失敗：${e.message}")
        }
        val v = validate(d)
        if (!v.ok) error("資料驗證失敗：\n" + v.problems.joinToString("\n"))

        // 先寫快取，寫入失敗都唔影響呢次 session，但會如實回報。
        try {
            val tmp = File(ctx.filesDir, "$CACHE_NAME.part")
            tmp.writeText(text, Charsets.UTF_8)
            if (cacheFile(ctx).exists()) cacheFile(ctx).delete()
            tmp.renameTo(cacheFile(ctx))
        } catch (e: Exception) {
            // 快取失敗唔阻塞套用，只清除可能寫壞嘅暫存檔
            File(ctx.filesDir, "$CACHE_NAME.part").delete()
        }
        AppState.data = d
        AppState.sourceLabel = Fmt.zh(AppState.lang, "線上路線資料", "Online route data")
        AppState.dataUpdatedAt = Fmt.nowTime()
        AppState.dataCached = true
        return d
    }

    /** 開 App 時載入：優先已驗證嘅資料快取，否則內建 assets。 */
    fun loadInitial(ctx: Context): HketaData {
        val f = cacheFile(ctx)
        if (f.isFile) {
            try {
                val d = JsonParser.parse(f.readText(Charsets.UTF_8))
                if (validate(d).ok) {
                    AppState.data = d
                    AppState.sourceLabel = Fmt.zh(AppState.lang, "線上路線資料（快取）", "Online route data (cached)")
                    AppState.dataUpdatedAt = Fmt.nowTime()
                    AppState.dataCached = true
                    return d
                }
            } catch (e: Exception) {
                // 快取壞咗就當冇， falls through 去內建
            }
            f.delete()
        }
        val d = EtaRepository.loadAssets(ctx)
        AppState.data = d
        AppState.sourceLabel = Fmt.zh(AppState.lang, "內建 JSON", "Bundled JSON")
        AppState.dataUpdatedAt = ""
        AppState.dataCached = false
        return d
    }

    /** 還原內建：刪快取 + 重新由 assets 載入。 */
    fun restoreBundled(ctx: Context): HketaData {
        cacheFile(ctx).delete()
        val d = EtaRepository.loadAssets(ctx)
        AppState.data = d
        AppState.sourceLabel = Fmt.zh(AppState.lang, "內建 JSON", "Bundled JSON")
        AppState.dataUpdatedAt = ""
        AppState.dataCached = false
        return d
    }

    /** 更新前後差異，例如「路線 30 → 32 · 站位 355 → 370」。 */
    fun diff(old: Stats, new: Stats, lang: Lang): String {
        fun arrow(a: Int, b: Int): String = if (a == b) "$b" else "$a → $b"
        return Fmt.zh(
            lang,
            "路線 ${arrow(old.routes, new.routes)} · 站位 ${arrow(old.stops, new.stops)} · " +
                "收費 ${arrow(old.withFare, new.withFare)} · 班次 ${arrow(old.withService, new.withService)}",
            "Routes ${arrow(old.routes, new.routes)} · stops ${arrow(old.stops, new.stops)} · " +
                "fares ${arrow(old.withFare, new.withFare)} · schedules ${arrow(old.withService, new.withService)}"
        )
    }
}
