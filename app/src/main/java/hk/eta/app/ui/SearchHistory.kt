package hk.eta.app.ui

import android.content.Context
import hk.eta.app.api.RouteSearch
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * SEARCH_UI_2026-11-23 — 對照截圖嘅「搜尋紀錄」。
 * 只存使用者實際點過嘅官方目錄列；欄位逐個 URL-encode，
 * 所以中文站名、「/」同「·」都唔會撞分隔字元。
 */
data class SearchHistoryItem(
    val company: String,
    val route: String,
    val dest: String,
    val bound: String,
    val note: String,
    val joint: Boolean,
    val circular: Boolean = false,
    val style: String = "",
    val removable: Boolean = true,
    val preloaded: Boolean = false
) {
    /** 去重 key：同一路線＋同一方向＋同一目的地只留一列。 */
    fun key(): String = listOf(company, route, dest, bound).joinToString("|")
}

/** RouteSearch.Result -> 可持久化嘅一列（Java 資料層一個欄位都唔改）。 */
fun RouteSearch.Result.toHistoryItem(
    removable: Boolean = true,
    preloaded: Boolean = false
): SearchHistoryItem = SearchHistoryItem(
    company = company,
    route = route,
    dest = dest,
    bound = bound,
    note = note,
    joint = joint,
    circular = circular,
    style = style,
    removable = removable,
    preloaded = preloaded
)

object SearchHistoryPrefs {

    private const val FILE = "search_history"
    private const val KEY = "items"
    private const val MAX = 8

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")
    private fun bool(s: String): Boolean = s == "1"
    private fun bit(b: Boolean): String = if (b) "1" else "0"

    private fun encode(item: SearchHistoryItem): String = listOf(
        enc(item.company), enc(item.route), enc(item.dest), enc(item.bound),
        enc(item.note), bit(item.joint), bit(item.circular), enc(item.style),
        bit(item.removable), bit(item.preloaded)
    ).joinToString("|")

    private fun decode(line: String): SearchHistoryItem? {
        val p = line.split("|")
        if (p.size != 10) return null
        return try {
            SearchHistoryItem(
                company = dec(p[0]), route = dec(p[1]), dest = dec(p[2]),
                bound = dec(p[3]), note = dec(p[4]), joint = bool(p[5]),
                circular = bool(p[6]), style = dec(p[7]),
                removable = bool(p[8]), preloaded = bool(p[9])
            )
        } catch (e: Exception) {
            null
        }
    }

    fun list(context: Context): List<SearchHistoryItem> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("\n").mapNotNull { decode(it) }.take(MAX)
    }

    /** 最新一列排最前；同一 key 唔重複。 */
    fun add(context: Context, item: SearchHistoryItem) {
        val keep = list(context).filter { it.key() != item.key() }
        val next = (listOf(item.copy(removable = true, preloaded = false)) + keep).take(MAX)
        save(context, next)
    }

    fun remove(context: Context, item: SearchHistoryItem) {
        save(context, list(context).filter { it.key() != item.key() })
    }

    fun clear(context: Context) = save(context, emptyList())

    private fun save(context: Context, items: List<SearchHistoryItem>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, items.joinToString("\n") { encode(it) })
            .apply()
    }
}
