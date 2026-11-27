package hk.eta.app.ui

import hk.eta.app.api.RouteSearch

/**
 * HKE_MOBILITY_CATEGORIES — 還原「香港出行易」式分類嘅純決策層。
 *
 * 無 Android／無網絡／可離線測。只做一件事：**每個類別／巴士公司按鈕，
 * 只顯示屬於佢嘅官方目錄列**，唔好幾間公司混埋一張清單。
 *
 * 之前嘅問題（三點其實同一個根因）：
 *  - 「所有巴士類別」chip 直接 `return true`，連小巴／港鐵／輕鐵／港鐵接駁
 *    巴士都撈埋入巴士頁 → 巴士公司按鈕冇分開、小巴路線唔喺小巴類別；
 *  - `RouteSearch.preload()` 一刀切 `subList(0, 24)`，官方 KMB／CTB 目錄
 *    幾百條排先，港鐵／輕鐵／小巴／嶼巴全部被 cut 走 → 港鐵類別望落空白。
 *
 * 配合資料層嘅「每個公司配額」預載，呢個規則令八個 tab ＋六個 chips
 * 各自都有屬於自己嘅官方列。渡輪／電車／的士／非專線小巴／居民巴士服務
 * 官方冇同類公開實時 ETA API，如實留空，唔捏造班次。
 */
object SearchCategoryRules {

    // 八個頂層類別（對應 activity_main.xml 嘅 cat* id 順序，唔改版面／色值）
    const val CAT_RAIL = 0        // 港鐵：重鐵 + 輕鐵 + 港鐵接駁巴士
    const val CAT_BUS = 1         // 巴士（下設六個公司 chips）
    const val CAT_MINIBUS = 2     // 小巴（專線小巴／綠色小巴）
    const val CAT_FERRY = 3
    const val CAT_TRAM = 4
    const val CAT_TAXI = 5
    const val CAT_NPLB = 6        // 非專線小巴
    const val CAT_RESIDENT = 7    // 居民巴士服務

    // 六個巴士公司按鈕（對應 chip* id 順序）
    const val CHIP_ALL = 0        // 所有巴士類別
    const val CHIP_CTB = 1        // 城巴
    const val CHIP_KMB = 2        // 九巴
    const val CHIP_LWB = 3        // 龍運巴士（與九巴共用官方目錄）
    const val CHIP_NLB = 4        // 新大嶼山巴士
    const val CHIP_DB = 5         // 愉景灣／馬灣巴士

    // 同 EtaRepository / JointRoutes 嘅 Java 常數一致；checker 會逐個對照。
    const val KMB = "KMB"
    const val CTB = "CTB"
    const val NLB = "NLB"
    const val GMB = "GMB"
    const val MTR = "MTR"
    const val LRT = "LRT"
    const val MTRB = "MTRB"
    const val JOINT = "JOINT"     // JointRoutes.JOINT =「城巴/九巴」聯營

    /** 專營巴士公司（唔包小巴／鐵路／接駁巴士）。 */
    fun isBusCompany(company: String): Boolean =
        company == KMB || company == CTB || company == NLB || company == JOINT

    /** 港鐵類別：重鐵、輕鐵、港鐵接駁巴士全部顯示返。 */
    fun isRail(company: String): Boolean =
        company == MTR || company == LRT || company == MTRB

    /**
     * 香港出行易式所屬判定。聯營 `JOINT` 同時屬於城巴同九巴兩個按鈕
     * （官方兩間都有營運），其餘一律一間公司一個按鈕，唔交叉。
     *
     * KMB_LWB_SPLIT: `brand` 由官方 GTFS `agency_id` 判定（"KMB"／"LWB"／
     * ""＝未能分辨）。九巴 chip 只收九巴路線，龍運 chip 只收龍運路線；
     * 判唔到嘅特別路線只留喺「所有巴士類別」，**唔會兩邊都出現**。
     */
    fun belongs(company: String, category: Int, busChip: Int,
                brand: String = ""): Boolean = when (category) {
        CAT_RAIL -> isRail(company)
        CAT_BUS -> when (busChip) {
            CHIP_ALL -> isBusCompany(company)
            // 聯營列喺兩間公司嘅按鈕都要出現，先算「分開喺所屬公司」。
            CHIP_CTB -> company == CTB || company == JOINT
            CHIP_KMB -> (company == KMB || company == JOINT) && brand == "KMB"
            CHIP_LWB -> (company == KMB || company == JOINT) && brand == "LWB"
            CHIP_NLB -> company == NLB
            else -> false         // 愉景灣／馬灣：官方冇同類實時 ETA API
        }
        CAT_MINIBUS -> company == GMB
        else -> false             // 其餘類別如實留空，唔捏造
    }

    /**
     * ALL_ROUTES: 單一來源仍然放喺 api 層（ui → api 依賴方向正確），但配額
     * 已由 3 改為 `Int.MAX_VALUE` = **無上限 = 顯示所有官方目錄路線**。
     * 想改返限量只需改 `RouteSearch.ALL_ROUTES_LIMIT` 一個位。
     */
    val ALL_ROUTES_LIMIT: Int = RouteSearch.ALL_ROUTES_LIMIT

    /**
     * CODE_OPTIMISATION: 由 MainActivity 真正呼叫，一份邏輯兩邊共用；
     * `filter` 保持輸入順序（官方 `rank()` 排序），唔似 groupBy 會重排。
     * ALL_ROUTES: 無上限模式直接返回全部列，唔做無謂嘅 HashMap 計數；
     * 限量模式仍保留原本嘅每公司配額邏輯，日後切換唔使改呼叫端。
     */
    fun <T> capPerCompany(rows: List<T>, companyOf: (T) -> String): List<T> {
        if (ALL_ROUTES_LIMIT == Int.MAX_VALUE) {
            return rows
        }
        val seen = HashMap<String, Int>()
        return rows.filter { row ->
            val company = companyOf(row)
            val n = seen[company] ?: 0
            if (n >= ALL_ROUTES_LIMIT) {
                false
            } else {
                seen[company] = n + 1
                true
            }
        }
    }
}
