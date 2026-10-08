package com.hketa.app.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 輕鐵特別班次（9xx 等）嘅掃描結果快取。
 *
 * 官方冇提供特別班次清單，所以要掃描各站預報先搵到 —— 呢個動作要發幾十個請求，
 * 唔適合每次開鐵路頁都做一次。所以掃完存落本地，下次開頁即刻顯示，
 * 用戶想更新先再撳掣掃。
 */
class SpecialRoutesStore(context: Context) {

    private val file = File(context.filesDir, "hketa_lr_special.json")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun load(): List<SpecialRouteInfo> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString<List<SpecialRouteInfo>>(file.readText())
    }.getOrDefault(emptyList())

    fun save(list: List<SpecialRouteInfo>) {
        runCatching { file.writeText(json.encodeToString(list)) }
    }

    /** 上次掃描嘅時間（epoch millis）；冇就 0 */
    fun lastScanAt(): Long = runCatching { file.lastModified() }.getOrDefault(0L)

    fun clear() {
        runCatching { file.delete() }
    }
}
