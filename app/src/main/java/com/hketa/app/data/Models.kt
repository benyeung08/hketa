package com.hketa.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class Operator(val label: String) {
    KMB("九巴"),
    CTB("城巴"),
    NLB("嶼巴"),
    GMB("專線小巴"),
    MTR_BUS("港鐵巴士"),
    MTR_HR("港鐵"),
    LRT("輕鐵");
}

/** 一條路線（含方向） */
@Serializable
data class RouteDef(
    val op: Operator = Operator.KMB,
    val route: String = "",
    val bound: String = "",        // KMB: 1/2；CTB: I/O
    val serviceType: String = "",  // KMB 特別班次
    val orig: String = "",
    val dest: String = "",
    val routeId: String = ""       // NLB / GMB 內部 id
) {
    val key: String get() = "${op.name}|$route|$bound|$serviceType|$routeId"
    val title: String get() = if (orig.isBlank() && dest.isBlank()) route else "$route　往 $dest"
}

/** 一個車站 */
@Serializable
data class StopDef(
    val op: Operator = Operator.KMB,
    val id: String = "",
    val name: String = "",
    val nameEn: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

/** 路線與車站的對應 */
@Serializable
data class RouteStopDef(
    val op: Operator = Operator.KMB,
    val route: String = "",
    val bound: String = "",
    val serviceType: String = "",
    val routeId: String = "",    // NLB / GMB 用
    val seq: Int = 0,
    val stopId: String = ""
)

/** 存於 filesDir/hketa_index.json 的離線索引 */
@Serializable
data class IndexData(
    val version: Int = 1,
    val builtAt: Long = 0L,
    val routes: List<RouteDef> = emptyList(),
    val stops: List<StopDef> = emptyList(),
    val routeStops: List<RouteStopDef> = emptyList()
)

/** 一筆到站預報 */
data class EtaEntry(
    val op: Operator = Operator.KMB,
    val route: String = "",
    val dest: String = "",
    val minutes: Int? = null,
    val clock: String = "",
    val remark: String = ""
)

/** 路線沿途車站（含順序） */
data class StopWithSeq(val stop: StopDef, val seq: Int)

/** 附近車站（含距離） */
data class NearbyStop(val stop: StopDef, val distanceMeters: Int, val routes: List<String> = emptyList())

/** 一間機構的索引完成度 */
data class IndexStat(val op: Operator, val routes: Int, val stops: Int)

/** 「搜尋車站」嘅一筆結果：一個車站 + 該站停靠嘅路線號 */
data class StopHit(
    val stop: StopDef = StopDef(),
    val routes: List<String> = emptyList()
)

/**
 * 主頁一筆：一個車站 + 該站嘅到站預報（App1933 主頁樣式）。
 * routes = 嗰個站停靠嘅路線號；etas = 即時預報。
 */
data class HomeItem(
    val stop: StopDef = StopDef(),
    val routes: List<String> = emptyList(),
    val etas: List<EtaEntry> = emptyList(),
    val distanceMeters: Int = 0
) {
    /** 最近一班嘅分鐘數（冇就 null） */
    val nextMinutes: Int? get() = etas.firstOrNull()?.minutes
}
