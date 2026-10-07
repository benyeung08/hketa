package com.hketa.app.data

/**
 * 到站預報「備註」欄位嘅本地化字串模板。
 *
 * 備註係由資料層即場拼出嚟（月台、延誤、經馬場、卡數…），
 * 唔係單一資源字串，所以由 AppViewModel 依當前語言讀好資源再傳入嚟。
 */
data class EtaText(
    val platform: String = "月台 %1\$s",
    val delayed: String = "服務延誤",
    val viaRacecourse: String = "經馬場",
    val arriving: String = "到站",
    val departing: String = "開出",
    val special: String = "特別班次",
    val cars: String = "%1\$s 卡",
    val sep: String = "　"
) {
    fun platformText(value: String): String =
        if (value.isBlank()) "" else String.format(platform, value)

    fun carsText(value: String): String =
        if (value.isBlank()) "" else String.format(cars, value)

    /** 用目前語言嘅分隔符串起備註 */
    fun join(parts: List<String>): String = parts.filter { it.isNotBlank() }.joinToString(sep)
}
