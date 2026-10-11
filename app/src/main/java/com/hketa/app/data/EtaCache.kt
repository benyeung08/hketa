package com.hketa.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * ETA 離線快取 —— **解決「冇網就完全冇嘢睇」**。
 *
 * 官方 ETA 係即時資料，一向要聯網先攞到；一冇網（地鐵入面、飛機模式、
 * 或者官方接口抽風），主頁同 Widget 就淨係剩個空畫面。
 *
 * 呢度記低**每一次成功抓到嘅 ETA**（連時間戳），所以：
 *
 * - 有網 → 照舊抓新嘅，抓到就更新快取
 * - 冇網／失敗 → **顯示上次嗰份**，並標明幾耐之前（「離線 · 5 分鐘前」）
 *
 * 顯示時一定要標「離線」＋時間，因為 ETA 舊咗會誤導 ——
 * 寧願講明「呢個係 8 分鐘前嘅資料」，都唔好扮即時。
 */

/** 一筆快取嘅 ETA */
@Serializable
data class CachedEta(
    val route: String = "",
    val minutes: Int? = null,
    val clock: String = "",
    val dest: String = ""
)

/** 某個站（＋路線）嘅快取記錄 */
@Serializable
data class EtaCacheEntry(
    val key: String = "",
    val etas: List<CachedEta> = emptyList(),
    /** 抓取時間（epoch ms） */
    val atMs: Long = 0L
)

class EtaCache(context: Context) {

    private val dir = File(context.filesDir, "hketa_eta_cache").apply { mkdirs() }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 快取 key：站 id ＋（有指定路線就加埋路線）。
     * 唔同路線嘅 ETA 要分開記，否則會撈亂。
     */
    fun keyOf(stopId: String, op: Operator, route: String): String =
        "${op.name}|${stopId.trim()}|${route.trim()}"
            .replace(Regex("[^A-Za-z0-9_|.-]"), "_")
            .take(160)

    private fun fileOf(key: String) = File(dir, "$key.json")

    fun get(key: String): EtaCacheEntry? = runCatching {
        val f = fileOf(key)
        if (!f.exists()) return null
        json.decodeFromString<EtaCacheEntry>(f.readText())
    }.getOrNull()

    /**
     * 寫入快取。原子寫（.tmp → rename），寫到一半崩潰都唔會留低壞檔。
     * 一定要喺 IO 線程叫。
     */
    fun put(key: String, etas: List<CachedEta>) {
        if (etas.isEmpty()) return      // 空結果唔寫，費事覆蓋咗有用嘅舊資料
        runCatching {
            val f = fileOf(key)
            val tmp = File(dir, "$key.tmp")
            tmp.writeText(
                json.encodeToString(
                    EtaCacheEntry(key, etas, System.currentTimeMillis())
                )
            )
            if (tmp.exists() && tmp.length() > 0) tmp.renameTo(f)
        }
    }

    /**
     * 有冇快取（唔理過唔過期）—— Widget 用嚟決定使唔使顯示「離線」標籤。
     */
    fun has(key: String): Boolean = get(key)?.etas?.isNotEmpty() == true

    /**
     * 快取係唔係太舊（超過 [STALE_MS]）。
     * 舊咗都照顯示（好過冇），但 UI 要標得更明顯。
     */
    fun isStale(entry: EtaCacheEntry): Boolean =
        System.currentTimeMillis() - entry.atMs > STALE_MS

    /** 快取咗幾耐（分鐘） */
    fun ageMinutes(entry: EtaCacheEntry): Int =
        ((System.currentTimeMillis() - entry.atMs) / 60_000L).toInt().coerceAtLeast(0)

    /** 由官方 EtaEntry 轉做快取格式 */
    companion object {
        /** 超過 30 分鐘就算「太舊」 */
        const val STALE_MS = 30 * 60 * 1000L

        fun from(etas: List<EtaEntry>): List<CachedEta> =
            etas.take(6).map {
                CachedEta(
                    route = it.route,
                    minutes = it.minutes,
                    clock = it.clock,
                    dest = it.dest
                )
            }
    }
}
