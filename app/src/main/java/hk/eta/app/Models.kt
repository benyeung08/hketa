package hk.eta.app

/** 介面語言；全 App 以 AppState.lang 即時切換，不需重建 Activity。 */
enum class Lang { ZH, EN }

/** 中英雙語文字。HKETA 原版所有站位／起終點皆為中英並列。 */
data class BiText(val zh: String, val en: String) {
    fun of(lang: Lang): String = if (lang == Lang.ZH) zh else en
}

/** 交通模式：巴士／專線小巴／地鐵／輕鐵。id 與 JSON 內 route.mode 對應。 */
data class TransportMode(val id: String, val zh: String, val en: String) {
    fun name(lang: Lang): String = if (lang == Lang.ZH) zh else en
}

/** 營運商：KMB、CTB、NLB、MTR、GMB、LRT。id 與 JSON 內 route.co 對應。 */
data class Operator(val id: String, val zh: String, val en: String) {
    fun name(lang: Lang): String = if (lang == Lang.ZH) zh else en
}

/** 站位。lat/lng 為 WGS84 約略座標，供「地圖」按鈕開啟外部地圖 App。 */
data class Stop(
    val id: String,
    val name: BiText,
    val lat: Double,
    val lng: Double
)

/**
 * 一個方向（去程 / 回程）。
 * dir 沿用 DATA.GOV.HK 習慣：O = Outbound、I = Inbound；
 * MTR 亦可用 UT / DT 表示上行、下行。
 */
data class Bound(
    val dir: String,
    val label: BiText,
    val orig: BiText,
    val dest: BiText,
    val stopIds: List<String>
)

/** 分段收費：由 from 至 to 嘅成人票價。 */
data class FareSection(val from: BiText, val to: BiText, val full: Double)

/**
 * 收費（示範約值，以官方為準）。
 * full / child / senior 為 HKD 金額，null 代表無此類別。
 */
data class Fare(
    val currency: String,
    val full: Double,
    val child: Double?,
    val senior: Double?,
    val method: BiText,
    val note: BiText,
    val sections: List<FareSection>
)

/**
 * 服務班次（一段日子嘅頭尾車、繁忙／非繁忙間隔、全程行車時間）。
 * 一條路線可以有多筆（平日／週末假日）。
 */
data class Service(
    val days: BiText,
    val first: String,
    val last: String,
    val peakMin: Int?,
    val offPeakMin: Int?,
    val journeyMin: Int?,
    val note: BiText
)

/**
 * 一條路線。
 * mode  : bus / minibus / metro / light_rail
 * fare  : 收費（可為 null，舊版 JSON 相容）
 * services : 服務班次（可為空，舊版 JSON 相容）
 */
data class Route(
    val id: String,
    val co: String,
    val mode: String,
    val number: String,
    val name: BiText,
    val bounds: List<Bound>,
    val fare: Fare?,
    val services: List<Service>
)

/**
 * 一班到站時間。
 * time     : ISO8601 或 HH:mm（示範資料用 HH:mm）
 * minutes  : 距到站分鐘數，null 代表「暫無資料」，<=0 視為即將到站
 * platform : 港鐵／輕鐵月台編號
 * scheduled: true 顯示「原定班次 / Scheduled Bus」
 */
data class Eta(
    val seq: Int,
    val time: String?,
    val minutes: Int?,
    val dest: BiText?,
    val remark: BiText?,
    val platform: String?,
    val scheduled: Boolean
)

data class StopEta(
    val stopId: String,
    val generatedTimestamp: String,
    val etas: List<Eta>
)

/** assets/hketa.json 的完整結構。改資料只需改這份 JSON，不用動 Kotlin。 */
data class HketaData(
    val version: String,
    val generatedTimestamp: String,
    val demo: Boolean,
    val note: BiText,
    val fareDisclaimer: BiText,
    val apiDocs: Map<String, String>,
    val modes: List<TransportMode>,
    val operators: List<Operator>,
    val stops: Map<String, Stop>,
    val routes: List<Route>,
    val etas: Map<String, StopEta>
) {
    fun operatorName(id: String, lang: Lang): String =
        operators.find { it.id == id }?.name(lang) ?: id

    fun modeName(id: String, lang: Lang): String =
        modes.find { it.id == id }?.name(lang) ?: id

    fun modeCount(id: String): Int = routes.count { it.mode == id }
}

/** 跨 Activity 共用的載入結果與語系。 */
object AppState {
    var lang: Lang = Lang.ZH
    var data: HketaData? = null
    var sourceLabel: String = "內建 JSON"
    /** 最近一次由設定頁更新資料嘅時間（只存記憶體，唔寫入本機）。 */
    var dataUpdatedAt: String = ""
    /** 目前用緊嘅係咪「線上路線資料快取」（區分內建 JSON）。 */
    var dataCached: Boolean = false

    fun get(): HketaData =
        data ?: error("HKETA 資料尚未載入，請先呼叫 EtaRepository.loadAssets()")
}
