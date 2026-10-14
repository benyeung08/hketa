package com.hketa.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hketa.app.R
import com.hketa.app.data.Operator
import com.hketa.app.data.FavoriteStop
import com.hketa.app.data.RailLine
import com.hketa.app.data.StopDef
import com.hketa.app.util.AppLocale

/**
 * 營辦商顯示名。用 stringResource 而唔係讀 enum 欄位，
 * 語言切換時（configuration change）會自動 recompose 成另一種語言。
 */
@Composable
fun Operator.labelText(): String = stringResource(
    when (this) {
        Operator.KMB -> R.string.op_kmb
        Operator.CTB -> R.string.op_ctb
        Operator.NLB -> R.string.op_nlb
        Operator.GMB -> R.string.op_gmb
        Operator.MTR_BUS -> R.string.op_mtr_bus
        Operator.MTR_HR -> R.string.op_mtr_hr
        Operator.LRT -> R.string.op_lrt
    }
)

/**
 * 車站顯示名：英文介面且官方有提供英文站名就用英文，否則用中文原名。
 *
 * 九巴、城巴、專線小巴接口都有 name_en；嶼巴同鐵路只提供中文，
 * 嗰啲會照舊顯示中文 —— 唔會自己硬翻，避免同官方站牌對唔上。
 */
@Composable
fun StopDef.displayName(): String {
    val en = AppLocale.isEnglish()
    return if (en && nameEn.isNotBlank()) nameEn else name
}

/**
 * 顯示用嘅「站號」。
 *
 * 有啲路徑（例如由路線–站對應兜底出嚟嘅站）會把 routeId（如 296E43213D6DB624）
 * 當咗做 stop id。呢啲唔係站號，顯示出嚟只會令畫面出現「一串數字」，
 * 而且攞去查 ETA 一定失敗。所以：
 *   - 長度 > 12（九巴站號最長約 8 位，如 SS667／HA03T）→ 當唔係站號，唔顯示
 *   - 其原樣顯示
 */
fun StopDef.codeLabel(): String {
    val c = id.trim()
    if (c.isBlank()) return ""
    if (c.length > 12) return ""          // 明顯係 routeId，唔係站號
    return c
}

/**
 * 站名（連站號一次過），唔會重複出現括號站號。
 *
 * 必須係 @Composable —— 因為入面叫咗 [StopDef.displayName]，
 * 而 displayName 要讀 AppLocale.current()（一個 Composable State）。
 */
@Composable
fun StopDef.titleWithCode(): String {
    val name0 = displayName()
    val code = codeLabel()
    if (code.isBlank()) return name0
    // 站名本身已經帶咗站號（…(SS667)）就唔好再拼一次
    val already = name0.contains("($code)") || name0.contains("（$code）")
    return if (already) name0 else "$name0（$code）"
}

/**
 * 顯示用嘅「淨站名」—— 連括號站號都剝走。
 *
 * 「附近」嘅卡位得咁上下闊，官方站名有時本身已經包咗站號
 * （例如「海麗邨巴士總站 (SS667)」嗰個 SS667），多咗舊括號只會阻地方；
 * 而且同一個站唔同營辦商有唔同站號，顯示邊一個都唔啱。
 *
 * 淨係剝「括號入面正好係站號」嗰種；其他括號（例如「（循環線）」）
 * 一律保留，費事改錯官方站名。
 *
 * 必須係 @Composable —— 因為入面叫咗 [StopDef.displayName]。
 */
@Composable
fun StopDef.plainName(): String = stripBrackets(displayName())

/**
 * 剝走站名入面**全部**括號段（連括號一齊）。
 *
 * 「附近」嘅卡位得咁上下闊，站號（如 `海麗邨巴士總站 (SS667)` 嗰個 SS667）
 * 只會阻地方；而同一個站唔同營辦商有唔同站號，顯示邊一個都唔啱。
 *
 * 半角 `()`、全角 `（）`、`[]`、`［］` 四種都處理，可以剝多段。
 * 車站頁同 ETA 卡仍然顯示完整站名連站號 —— 想對返官方站牌嗰陣有得睇。
 */
private fun stripBrackets(name: String): String {
    if (name.isBlank()) return name
    val pairs = listOf("（" to "）", "(" to ")", "［" to "］", "[" to "]")
    var out = name.trim()
    // 逐段剝：由最細單位做起，重複到冇得剝為止（上限 6 段，避免 pathological 輸入）
    repeat(6) {
        val before = out
        for ((l, r) in pairs) {
            val i = out.indexOf(l)
            val j = if (i < 0) -1 else out.indexOf(r, i + l.length)
            if (i >= 0 && j > i) out = (out.substring(0, i) + out.substring(j + r.length)).trim()
        }
        if (out == before) return@repeat
    }
    return out.trim().ifBlank { name.trim() }
}

/** 非 Composable 場合（Widget、ViewModel）用嘅淨站名版本 */
fun StopDef.plainName(en: Boolean): String = stripBrackets(displayName(en))

/** 非 Composable 場合（Widget、ViewModel）用嘅版本 */
fun StopDef.displayName(en: Boolean): String =
    if (en && nameEn.isNotBlank()) nameEn else name

/** 鐵路線名：英文介面下顯示英文線名（如 Tuen Ma Line），否則中文 */
@Composable
fun RailLine.displayName(): String =
    if (AppLocale.isEnglish() && nameEn.isNotBlank()) nameEn else name

/** 鐵路總站名：英文介面下若只得中文就照顯示中文 */
@Composable
fun RailLine.displayTermini(): String =
    if (orig.isBlank() && dest.isBlank()) "" else "$orig ⇄ $dest"

/** 收藏站顯示：站名（站號），站號異常（routeId）時唔顯示 */
@Composable
fun FavoriteStop.titleWithCode(): String {
    val n = displayName()
    val c = if (id.length > 12) "" else id
    if (c.isBlank()) return n
    val already = n.contains("($c)") || n.contains("（$c）")
    return if (already) n else "$n（$c）"
}

/** 收藏車站嘅顯示名（英文介面用官方英文站名） */
@Composable
fun FavoriteStop.displayName(): String =
    if (AppLocale.isEnglish() && nameEn.isNotBlank()) nameEn else name
