package hk.eta.app

import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 顯示層的小工具：分鐘、時間、雙語標籤。 */
object Fmt {

    fun minutesText(lang: Lang, minutes: Int?): String = when {
        minutes == null -> if (lang == Lang.ZH) "—" else "—"
        minutes <= 0 -> if (lang == Lang.ZH) "即將到站" else "Arriving"
        else -> if (lang == Lang.ZH) "$minutes 分鐘" else "$minutes min"
    }

    fun nowTime(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    fun zh(lang: Lang, zh: String, en: String): String =
        if (lang == Lang.ZH) zh else en

    val primary: Int = Color.parseColor("#0B5FFF")
    val white: Int = Color.WHITE
    val muted: Int = Color.parseColor("#6B7686")
    val green: Int = Color.parseColor("#0E9F6E")

    /** `$6.7` / `$115`；用 Locale.US 避免部分語系把小數點轉成逗號。 */
    fun money(v: Double): String =
        if (v % 1.0 == 0.0) "$" + v.toLong()
        else "$" + String.format(Locale.US, "%.1f", v)

    /** 列表／詳情頁嘅簡短收費，例如「全程 $6.7 · 小童 $3.4」。 */
    fun fareShort(lang: Lang, f: Fare?): String {
        if (f == null) return zh(lang, "暫無收費資料", "Fare unavailable")
        val sb = StringBuilder()
        sb.append(zh(lang, "全程 ", "Full ")).append(money(f.full))
        if (f.child != null) sb.append(" · ").append(zh(lang, "小童 ", "Child ")).append(money(f.child))
        return sb.toString()
    }

    /** 完整收費說明（對話框用）。 */
    fun fareDetail(lang: Lang, f: Fare?): String {
        if (f == null) return zh(lang, "暫無收費資料", "Fare unavailable")
        val sb = StringBuilder()
        sb.append(zh(lang, "全程成人", "Adult full fare")).append("：").append(money(f.full)).append('\n')
        if (f.child != null) sb.append(zh(lang, "小童", "Child")).append("：").append(money(f.child)).append('\n')
        if (f.senior != null) sb.append(zh(lang, "長者", "Senior")).append("：").append(money(f.senior)).append('\n')
        sb.append(zh(lang, "付款方式", "Payment")).append("：").append(f.method.of(lang)).append('\n')
        if (f.note.zh.isNotBlank()) sb.append(f.note.of(lang)).append('\n')
        for (s in f.sections) {
            sb.append("· ").append(s.from.of(lang)).append(" → ").append(s.to.of(lang))
                .append("：").append(money(s.full)).append('\n')
        }
        return sb.toString().trimEnd()
    }

    /** 一列服務班次，例如「星期一至五 05:30–00:20 · 繁忙 5 分／非繁忙 10 分 · 全程 45 分」。 */
    fun serviceLine(lang: Lang, s: Service): String {
        val sb = StringBuilder()
        sb.append(s.days.of(lang)).append("  ").append(s.first).append("–").append(s.last)
        val parts = mutableListOf<String>()
        if (s.peakMin != null) parts.add(zh(lang, "繁忙 ${s.peakMin} 分", "peak ${s.peakMin} min"))
        if (s.offPeakMin != null) parts.add(zh(lang, "非繁忙 ${s.offPeakMin} 分", "off-peak ${s.offPeakMin} min"))
        if (s.journeyMin != null) parts.add(zh(lang, "全程 ${s.journeyMin} 分", "journey ${s.journeyMin} min"))
        if (parts.isNotEmpty()) sb.append(" · ").append(parts.joinToString(" / "))
        if (s.note.zh.isNotBlank()) sb.append('\n').append("   ").append(s.note.of(lang))
        return sb.toString()
    }

    /** 詳情頁頂部嘅一行摘要。 */
    fun serviceShort(lang: Lang, services: List<Service>): String {
        val s = services.firstOrNull() ?: return zh(lang, "暫無班次資料", "Schedule unavailable")
        return zh(
            lang,
            "${s.first}–${s.last} · ${s.peakMin ?: "—"} 分一班 · 全程 ${s.journeyMin ?: "—"} 分",
            "${s.first}–${s.last} · every ${s.peakMin ?: "—"} min · ${s.journeyMin ?: "—"} min trip"
        )
    }
}

/**
 * 接真實即時 ETA 的擴充點。
 *
 * 目前使用 DemoEtaProvider（讀 JSON 內的示範班次）。
 * 要上線時只需：
 *   1. 新增一個實作 LiveEtaProvider 的類別，用 HttpURLConnection 打各營運商 API；
 *   2. 把 EtaActivity / RouteDetailActivity 內的 DemoEtaProvider 換成你的實作。
 *
 * 各營運商的 API 欄位請以 DATA.GOV.HK 的資料字典為準，
 * 連結已整理在 assets/hketa.json 的 apiDocs 與 README。
 */
interface LiveEtaProvider {
    fun fetch(data: HketaData, route: Route, bound: Bound, stopId: String, lang: Lang): List<Eta>
}

object DemoEtaProvider : LiveEtaProvider {
    override fun fetch(
        data: HketaData,
        route: Route,
        bound: Bound,
        stopId: String,
        lang: Lang
    ): List<Eta> = data.etas[stopId]?.etas ?: emptyList()
}
