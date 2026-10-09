package com.hketa.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 班次時間表。
 *
 * ⚠️ 重要前提：香港各營辦商（九巴／城巴／嶼巴／專線小巴／港鐵／輕鐵）
 * **都冇公開嘅班次時刻表 API** —— 官方淨係提供即時到站預報（ETA）。
 *
 * 所以呢度嘅「班次表」唔係官方紙本時刻表，而係由官方 ETA 即時構建出嚟嘅：
 *
 *   某一站嘅 ETA 清單本身已按到站先後排好，第 N 項就係「第 N 班車」。
 *   所以把沿線每個站嘅第 N 項抽埋一齊，就係第 N 班車行經各站嘅時間 ——
 *   呢個係真實資料，唔係估算。
 *
 * 至於「頭班車／尾班車」，官方冇得查，所以用觀察累積（見 [TimetableStore]），
 * 顯示時會講明係「觀察到」而唔係官方公佈。
 */

/** 一班車喺某個站嘅預計時間 */
@Serializable
data class TripCell(
    val stopName: String = "",
    /** 仲有幾多分鐘（null = 官方冇畀） */
    val minutes: Int? = null,
    /** 預計到站時刻 HH:mm（官方冇畀就係空字串） */
    val clock: String = ""
)

/** 第 N 班車：行經沿線各站嘅時間 */
@Serializable
data class Trip(
    /** 第幾班（1 起） */
    val seq: Int = 0,
    val cells: List<TripCell> = emptyList()
)

/** 營運狀態 */
enum class ServiceState { RUNNING, NOT_YET, ENDED, UNKNOWN }

@Serializable
data class RouteTimetable(
    val trips: List<Trip> = emptyList(),
    /** 班次間隔（分鐘），由相鄰兩班嘅分鐘差推算 */
    val headwayMin: Int? = null,
    /** 觀察到最早有班次嘅時刻（累積，唔係官方頭班車） */
    val firstObserved: String? = null,
    /** 觀察到最遲有班次嘅時刻（累積，唔係官方尾班車） */
    val lastObserved: String? = null,
    /** 構建呢份表嗰陣嘅營運狀態 */
    val state: String = ServiceState.UNKNOWN.name,
    /** 沿線站名（表頭用） */
    val stopNames: List<String> = emptyList()
) {
    val serviceState: ServiceState
        get() = runCatching { ServiceState.valueOf(state) }.getOrDefault(ServiceState.UNKNOWN)
}

/** 公告 */
data class Notice(
    val title: String,
    val body: String,
    val time: String,
    /** 來源標籤，例如「港鐵官方」「HKATE」 */
    val source: String,
    /** 有網址就顯示「查看詳情」 */
    val url: String = ""
)

/**
 * 觀察累積：記低每條路線「最早／最遲見過有班次」嘅時刻。
 *
 * 官方冇頭尾班車接口，所以靠用家日常開 App 慢慢累積 ——
 * 開得耐就愈準。顯示時要講明係觀察值。
 */
class TimetableStore(context: Context) {

    @Serializable
    data class Record(
        val routeKey: String = "",
        val firstSeen: String = "",   // HH:mm
        val lastSeen: String = "",    // HH:mm
        val samples: Int = 0
    )

    private val file = File(context.filesDir, "hketa_timetable.json")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private fun load(): MutableMap<String, Record> = runCatching {
        if (!file.exists()) return mutableMapOf()
        json.decodeFromString<Map<String, Record>>(file.readText()).toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun save(m: Map<String, Record>) {
        runCatching { file.writeText(json.encodeToString(m)) }
    }

    /** 記低今次見到嘅班次時刻，回傳更新後嘅累積記錄 */
    fun observe(routeKey: String, clocks: List<String>): Record {
        val valid = clocks.filter { it.matches(Regex("""\d{1,2}:\d{2}""")) }
        if (valid.isEmpty()) return load()[routeKey] ?: Record(routeKey)

        fun toMin(c: String): Int {
            val (h, m) = c.split(":")
            return h.toIntOrNull()?.times(60)?.plus(m.toIntOrNull() ?: 0) ?: 0
        }
        val lo = valid.minByOrNull { toMin(it) } ?: return Record(routeKey)
        val hi = valid.maxByOrNull { toMin(it) } ?: return Record(routeKey)

        val map = load()
        val old = map[routeKey]
        val rec = if (old == null) {
            Record(routeKey, lo, hi, 1)
        } else {
            val newFirst = if (old.firstSeen.isBlank() || toMin(lo) < toMin(old.firstSeen)) lo else old.firstSeen
            val newLast = if (old.lastSeen.isBlank() || toMin(hi) > toMin(old.lastSeen)) hi else old.lastSeen
            Record(routeKey, newFirst, newLast, old.samples + 1)
        }
        map[routeKey] = rec
        save(map)
        return rec
    }

    fun get(routeKey: String): Record? = load()[routeKey]
}
