package hk.eta.app.ui

import hk.eta.app.api.CircularRoutes
import hk.eta.app.api.EtaModels.EtaItem
import hk.eta.app.api.EtaRepository
import hk.eta.app.api.JointRoutes

/**
 * SEPARATE_OPERATORS — 「同路線號碼不同巴士營運商分開顯示」。
 *
 * 之前 `App1933Format.cardsFrom()` 嘅分組 key 係
 * `route | groupStop | destTc`（**刻意唔帶 company**，為咗聯營合併）。
 * 副作用：同一站、同一路線號、同一目的地，若由**唔同公司各自營運**
 * （例如九巴 12 同 嶼巴 12、城巴 1 同 綠小巴 1），會被塞入同一張卡；
 * `joint` 只認 KMB+CTB，其餘組合會揀 `first.company` 做卡名，但三班次
 * 仍混埋另一間公司，望落「一張卡夾咗兩間公司」。
 *
 * 呢個純 Kotlin 決策層（無 Android／無網絡／可離線測）定死：
 *  - **只有**「剛好 KMB + CTB 兩家、兩邊都非循環」→ 合併成既有
 *    `城巴/九巴` 聯營卡，逐班 `該班次：城巴／九巴`（用戶之前明確要求，唔推翻）；
 *  - **其餘全部按公司拆卡**：各自 `cardCompany = 自己公司`，三班次只屬自己，
 *    `RouteCardAdapter` 嘅 meta 而家由 `Brand.label(company, route)` 只顯示
 *    `九巴`／`龍運`／`城巴`／`嶼巴`／`綠色小巴`**自己一間**，唔使改 UI、
 *    唔使加色值、唔使加掣；
 *  - KMB+CTB+第三家、KMB+NLB、CTB+GMB、任一邊係循環線 → 一律拆；
 *  - 循環線維持上一輪「各自顯示九巴／城巴方式」，唔會被聯營合併吞走。
 *
 * **唔郁** 8 營運商資料層、`JointRoutes.augment()`、`RouteSearch.markJoint()`
 * （搜尋頁本來就只合併 KMB/CTB 聯營、其餘每公司一列），唔改任何顏色。
 */
data class OperatorGroup(
    val company: String,          // 單一公司，或 JointRoutes.JOINT
    val etas: List<EtaItem>,      // 只屬呢張卡嘅班次
    val circular: Boolean         // 用自己嘅班次判定，唔再用整個 bucket
)

object SameRouteOperators {

    /** 只有呢一對先係既有「聯營」定義；其它組合一律分開。 */
    fun isJointPair(a: String, b: String): Boolean =
        (a == EtaRepository.KMB && b == EtaRepository.CTB) ||
                (a == EtaRepository.CTB && b == EtaRepository.KMB)

    fun isCircular(etas: List<EtaItem>): Boolean =
        etas.any { CircularRoutes.isCircularDest(it.destTc) }

    /**
     * @param etas 已經係同一 `(route, groupStop, destTc)` 嘅全部班次。
     * @param circularHint 由 `fetchCards` bucket 傳入；只喺「單公司」時套用，
     *        避免一個混合 bucket 把非循環卡都標成循環線。
     */
    fun partition(etas: List<EtaItem>, circularHint: Boolean): List<OperatorGroup> {
        val byCompany = LinkedHashMap<String, MutableList<EtaItem>>()
        for (e in etas) {
            byCompany.getOrPut(e.company) { ArrayList() }.add(e)
        }
        val single = byCompany.size == 1
        val circular = LinkedHashMap<String, Boolean>()
        for ((company, list) in byCompany) {
            circular[company] = (circularHint && single) || isCircular(list)
        }

        val names = byCompany.keys.toList()
        if (names.size == 2
            && isJointPair(names[0], names[1])
            && circular[names[0]] != true
            && circular[names[1]] != true
        ) {
            return listOf(OperatorGroup(JointRoutes.JOINT, etas, false))
        }

        // SINGLE_OPERATOR_DISPLAY: 唔係聯營就每間公司各自一張卡、一個 label。
        // byCompany 得 1 間 -> 1 張卡顯示 1 間；KMB+NLB／CTB+GMB／三家／
        // 循環線 -> 逐間拆卡（各自 1 個 label）；只有上面 exact KMB+CTB 先
        // 顯示「城巴/九巴」兩間。絕不把單公司路線升級做兩間服務商。
        val out = ArrayList<OperatorGroup>()
        for ((company, list) in byCompany) {
            out.add(OperatorGroup(company, list, circular[company] == true))
        }
        return out
    }
}
