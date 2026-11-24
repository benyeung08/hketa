package hk.eta.app.ui

/**
 * SEARCH_LOCAL_DATA — 「恢復本地資料在搜尋功能上」。
 *
 * `RouteSearch` 本來就係 local snapshot first、live API fallback，
 * `RouteDataStore.refresh()` 亦會 `invalidateDownstream()`。但取消「進階」
 * 之後，搜尋頁**冇任何地方睇到本機資料**，而且由「應用程式設置 →
 * 線路資料更新」更新／清除快照返嚟，`MainActivity.onResume()` 唔會重新
 * `searchRoutes()` → 畫面仍係舊一次嘅預載結果（可能係 live fallback），
 * 本地資料望落「冇咗」。
 *
 * 呢個純 Kotlin 層（無 Android／無網絡／可離線測）負責：
 *  1. 由 `RouteDataStore.meta()` ＋實際存在嘅快照檔數整理 [Info]；
 *  2. [hasSnapshot] 判斷搜尋而家係咪真係有本機目錄可用；
 *  3. [signature] 產生「更新時間／成功來源／筆數／檔數」指紋，
 *     [shouldReload] 決定返前景時要唔要重新預載；
 *  4. [sourceTag] 畀 UI 如實標「本機資料」／「即時 API」，唔捏造。
 *
 * **唔郁** `RouteDataStore`／`RouteSearch`／8 營運商資料層，唔新增色值，
 * 唔加返「進階」掣，唔改 ETA 永遠即時查嘅既有規則。
 */
data class LocalDataInfo(
    val okSources: Int,
    val totalSources: Int,
    val records: Int,
    val updatedAt: Long,
    val filesPresent: Int
)

object SearchLocalData {

    /** 有 meta 更新時間或任何一份快照檔，就算有本機資料。 */
    fun hasSnapshot(info: LocalDataInfo): Boolean =
        info.updatedAt > 0L || info.filesPresent > 0

    /** 指紋變咗 = 設定頁更新過／清除過，要重新預載先用到新快照。 */
    fun signature(info: LocalDataInfo): String =
        "${info.updatedAt}|${info.okSources}|${info.records}|${info.filesPresent}"

    fun shouldReload(previous: String, current: String): Boolean =
        previous.isNotEmpty() && previous != current

    /** 只如一實標示，唔會把 live fallback 扮成本機資料。 */
    fun sourceTag(info: LocalDataInfo, local: String, live: String): String =
        if (hasSnapshot(info)) local else live

    /** 離線可用：本機目錄存在時即使冇網絡都出到預載列。 */
    fun offlineCapable(info: LocalDataInfo): Boolean = hasSnapshot(info)
}
