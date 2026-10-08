package com.hketa.app.data

import kotlinx.serialization.Serializable

/** assets/rail.json：港鐵重鐵與輕鐵的官方路線／車站表（含座標） */

@Serializable
data class RailStop(
    val id: String = "",
    val name: String = "",
    val nameEn: String = "",  // 官方英文站名（如 Hong Kong / Tuen Mun Ferry Pier）
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

@Serializable
data class RailLine(
    val id: String = "",      // 重鐵：AEL / TML …；輕鐵：610 / 705 …
    val name: String = "",    // 重鐵：屯馬線；輕鐵：610
    val nameEn: String = "",  // 重鐵官方英文名（Tuen Ma Line…）；輕鐵係數字，同 name 一樣
    val orig: String = "",
    val dest: String = "",
    val stops: List<RailStop> = emptyList()
)

@Serializable
data class RailAsset(
    val hr: List<RailLine> = emptyList(),
    val lr: List<RailLine> = emptyList()
)

object RailData {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var cached: RailAsset? = null

    fun load(context: android.content.Context): RailAsset {
        cached?.let { return it }
        val asset = runCatching {
            context.assets.open("rail.json").use { it.readBytes().toString(Charsets.UTF_8) }
                .let { json.decodeFromString<RailAsset>(it) }
        }.getOrDefault(RailAsset())
        cached = asset
        return asset
    }

    /** 重鐵線（10 條） */
    fun heavyRail(context: android.content.Context): List<RailLine> = load(context).hr

    /** 輕鐵線（11 條常規線） */
    fun lightRail(context: android.content.Context): List<RailLine> = load(context).lr

    /** 全部鐵路線，重鐵先行 */
    fun allLines(context: android.content.Context): List<RailLine> =
        heavyRail(context) + lightRail(context)

    /**
     * 站碼（重鐵）或站號（輕鐵）→ 站名。
     * @param english true 時優先返官方英文站名，冇就退返中文
     */
    fun nameOf(
        context: android.content.Context,
        op: Operator,
        id: String,
        english: Boolean = false
    ): String {
        val lines = when (op) {
            Operator.MTR_HR -> heavyRail(context)
            Operator.LRT -> lightRail(context)
            else -> emptyList()
        }
        for (l in lines) for (s in l.stops) {
            if (s.id == id) {
                return if (english && s.nameEn.isNotBlank()) s.nameEn else s.name
            }
        }
        return id
    }

    /** 查站嘅官方英文名；冇就返空字串 */
    fun stationEn(context: android.content.Context, op: Operator, id: String): String {
        val lines = when (op) {
            Operator.MTR_HR -> heavyRail(context)
            Operator.LRT -> lightRail(context)
            else -> emptyList()
        }
        for (l in lines) for (s in l.stops) if (s.id == id) return s.nameEn
        return ""
    }

    /** 站碼 → 所屬線（重鐵一站可能跨線，回傳全部） */
    fun linesOf(context: android.content.Context, op: Operator, id: String): List<RailLine> {
        val lines = when (op) {
            Operator.MTR_HR -> heavyRail(context)
            Operator.LRT -> lightRail(context)
            else -> emptyList()
        }
        return lines.filter { l -> l.stops.any { it.id == id } }
    }
}
