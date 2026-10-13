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

    /**
     * 優先由 **Rust store** 讀；冇（或者 Rust 唔喺度）就用原本嘅 JSON 檔，
     * 而且讀到會**順手搬遷**落 Rust。
     */
    fun load(): List<SpecialRouteInfo> {
        if (RustStore.available) {
            val raw = RustStore.get(NS, KEY)
            if (raw != null) {
                return runCatching { json.decodeFromString<List<SpecialRouteInfo>>(raw) }
                    .getOrDefault(emptyList())
            }
            val old = runCatching {
                if (!file.exists()) return@runCatching null
                json.decodeFromString<List<SpecialRouteInfo>>(file.readText())
            }.getOrNull()
            if (!old.isNullOrEmpty()) {
                save(old)          // 搬遷
                return old
            }
            return emptyList()
        }
        return runCatching {
            if (!file.exists()) return emptyList()
            json.decodeFromString<List<SpecialRouteInfo>>(file.readText())
        }.getOrDefault(emptyList())
    }

    fun save(list: List<SpecialRouteInfo>) {
        if (RustStore.available) {
            // 掃描一次要發幾十個請求，所以一定要落碟，唔可以淨係放記憶體
            if (RustStore.put(NS, KEY, json.encodeToString(list))) RustStore.flush()
            return
        }
        runCatching { file.writeText(json.encodeToString(list)) }
    }

    /** 上次掃描嘅時間（epoch millis）；冇就 0 */
    fun lastScanAt(): Long {
        if (RustStore.available) {
            val t = RustStore.at(NS, KEY)
            if (t > 0L) return t
        }
        return runCatching { file.lastModified() }.getOrDefault(0L)
    }

    fun clear() {
        if (RustStore.available) RustStore.clear(NS)
        runCatching { file.delete() }
    }

    private companion object {
        const val NS = RustStore.NS_SPECIAL
        const val KEY = "list"
    }
}
