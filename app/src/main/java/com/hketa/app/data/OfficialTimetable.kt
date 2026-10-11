package com.hketa.app.data

/**
 * 官方時刻表連結 —— **解決「官方紙本時刻表冇得顯示」**。
 *
 * ## 背景（查證過嘅事實）
 *
 * 政府公開數據平台（data.gov.hk）**冇發車時刻表**：
 *
 * - 運輸署「公共交通路線及收費資料」（KML／CSV）嘅官方 data spec
 *   淨係得 `serviceMode`（A/N/NT/R/T）同 `journeyTime`，
 *   **冇** serviceHours／firstBus／lastBus／headway／timetable 呢啲欄位
 * - 唯一相關嘅係 `hyperlinkC`／`hyperlinkS`／`hyperlinkE`
 *   —— 佢哋係「Headway hyperlink」，即係**指去官方時刻表網頁嘅連結**
 *
 * 各營辦商嘅官方 ETA 接口（KMB／CTB／NLB／GMB／MTR）**全部只提供即時 ETA**，
 * 同樣冇時刻表欄位。
 *
 * ## 所以呢度做咩
 *
 * 直接指去**官方時刻表網頁**，而唔係自己編數據或者抓網頁內文。
 *
 * **專線小巴**可以深鏈到具體路線 —— 運輸署嘅官方路線查詢頁
 * `get_gmb_detail.php?route_id={id}&lang={TC|SC|EN}` 本身就
 * 有完整官方時刻表（服務時間 ＋ 班次分鐘）。`route_id` 正係
 * `data.etagmb.gov.hk` 嗰個 id，所以 App 入面嘅 `routeId` 可以直接用。
 *
 * 其他營辦商嘅深鏈格式我**未驗證到**（九巴官網係 SPA、城巴／嶼巴
 * 嘅靜態頁面取唔到路線內容），所以一律去官方路線查詢首頁 ——
 * 唔會畀個「可能壞」嘅深鏈你。
 */

object OfficialTimetable {

    /** 運輸署官方專線小巴路線查詢（含完整時刻表） */
    private const val GMB_DETAIL = "https://h2-app-rr.hkemobility.gov.hk/ris_page/get_gmb_detail.php"

    /** 各營辦商嘅官方路線查詢／時刻表頁面 */
    private const val KMB_HOME = "https://www.kmb.hk/"
    private const val CTB_HOME = "https://www.citybus.com.hk/home/default.aspx?intLangID=1"
    private const val NLB_HOME = "https://www.nlb.com.hk/"
    private const val MTR_HOME = "https://www.mtr.com.hk/ch/customer/main/index.html"

    /**
     * 語言參數：TC=繁中、SC=簡中、EN=English。
     * 運輸署嗰頁三種都支援（實測 TC 同 EN 都出到內容）。
     */
    fun langParam(english: Boolean, simplified: Boolean): String =
        when {
            english -> "EN"
            simplified -> "SC"
            else -> "TC"
        }

    /**
     * 產生官方時刻表連結。
     *
     * @param op        營辦商
     * @param route     路線號（例如 "1A"）
     * @param routeId   NLB／GMB 嘅內部 id
     * @param english   係咪英文介面
     * @param simplified 係咪簡體介面
     * @return 連結；`null` = 呢個營辦商冇官方時刻表頁面
     */
    fun url(
        op: Operator,
        route: String,
        routeId: String = "",
        english: Boolean = false,
        simplified: Boolean = false
    ): String? = when (op) {
        Operator.GMB -> {
            // ★ 唯一可以深鏈到具體路線嘅營辦商
            val id = routeId.trim()
            if (id.isBlank()) null
            else "$GMB_DETAIL?route_id=$id&lang=${langParam(english, simplified)}"
        }
        Operator.KMB -> KMB_HOME
        Operator.CTB -> CTB_HOME
        Operator.NLB -> NLB_HOME
        Operator.MTR_HR, Operator.LRT, Operator.MTR_BUS -> MTR_HOME
        else -> null
    }

    /**
     * 係咪可以深鏈到具體路線（淨係小巴得）。
     * UI 用嚟決定掣上面寫「官方時刻表」定「去官網查時刻表」。
     */
    fun deepLinked(op: Operator, routeId: String = ""): Boolean =
        op == Operator.GMB && routeId.isNotBlank()
}
