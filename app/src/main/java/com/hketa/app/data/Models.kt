package com.hketa.app.data

import com.hketa.app.R
import kotlinx.serialization.Serializable

@Serializable
enum class Operator(
    val label: String,
    /** 官方全名，用喺「資料由 DATA.GOV.HK 提供」嗰行 */
    val officialName: String,
    /** 呢個營辦商嘅資料來源字串資源 id */
    val sourceRes: Int = 0
) {
    KMB("九巴", "九龍巴士", R.string.src_kmb_full),
    CTB("城巴", "城巴", R.string.src_ctb_full),
    NLB("嶼巴", "大嶼山巴士", R.string.src_nlb_full),
    GMB("專線小巴", "專線小巴", R.string.src_gmb_full),
    MTR_BUS("港鐵巴士", "港鐵巴士", R.string.src_mtr_bus_full),
    MTR_HR("港鐵", "港鐵", R.string.src_mtr_hr_full),
    LRT("輕鐵", "輕鐵", R.string.src_lrt_full);
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

/** 自動定位進行到邊一步 */
enum class LocatePhase { IDLE, LOCATING, OK, FAILED, NO_PERMISSION }

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

/**
 * 手動選點用嘅預設位置（香港各區中心／交通樞紐）。
 *
 * 用途：用戶唔想授權定位（或者定位失敗）嗰陣，可以自己揀一個地點，
 * 主頁照樣用呢個座標去搵附近車站，唔會淨係顯示「未授權定位」。
 */
data class PresetLocation(
    val zhHant: String = "",
    val zhHans: String = "",
    val en: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0
) {
    /** 按目前語言揀顯示名 */
    fun display(lang: String): String = when {
        lang.equals("en", ignoreCase = true) -> en
        lang.contains("CN", ignoreCase = true) || lang.contains("Hans", ignoreCase = true) -> zhHans
        else -> zhHant
    }
}

/** 香港常用地點（區中心／主要交通樞紐），畀「手動選點」用 */
val HK_PRESET_LOCATIONS: List<PresetLocation> = listOf(
    PresetLocation("中環", "中环", "Central", 22.2819, 114.1585),
    PresetLocation("銅鑼灣", "铜锣湾", "Causeway Bay", 22.2805, 114.1850),
    PresetLocation("旺角", "旺角", "Mong Kok", 22.3190, 114.1690),
    PresetLocation("觀塘", "观塘", "Kwun Tong", 22.3120, 114.2250),
    PresetLocation("將軍澳", "将军澳", "Tseung Kwan O", 22.3070, 114.2600),
    PresetLocation("沙田", "沙田", "Sha Tin", 22.3810, 114.1870),
    PresetLocation("大埔", "大埔", "Tai Po", 22.4510, 114.1700),
    PresetLocation("上水", "上水", "Sheung Shui", 22.5040, 114.1280),
    PresetLocation("荃灣", "荃湾", "Tsuen Wan", 22.3730, 114.1110),
    PresetLocation("屯門", "屯门", "Tuen Mun", 22.3910, 113.9770),
    PresetLocation("元朗", "元朗", "Yuen Long", 22.4430, 114.0220),
    PresetLocation("天水圍", "天水围", "Tin Shui Wai", 22.4610, 114.0030),
    PresetLocation("東涌", "东涌", "Tung Chung", 22.2890, 113.9430),
    PresetLocation("香港國際機場", "香港国际机场", "Hong Kong Int'l Airport", 22.3080, 113.9180),
    PresetLocation("香港仔", "香港仔", "Aberdeen", 22.2480, 114.1550),
    PresetLocation("九龍灣", "九龙湾", "Kowloon Bay", 22.3230, 114.2100)
)

/**
 * 輕鐵特別班次（例如 9xx 系列）。
 *
 * 呢啲班次唔喺常規 11 條線入面，所以路線列表搵唔到；
 * 但官方預報接口會返，所以可以掃描各站預報動態搵返出嚟。
 */
data class SpecialRouteInfo(
    val route: String = "",
    val stopIds: List<String> = emptyList(),
    val stopNames: List<String> = emptyList()
)
