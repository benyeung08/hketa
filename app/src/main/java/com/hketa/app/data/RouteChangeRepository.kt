package com.hketa.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 路線改動／服務公告列表（對齊 hkbus.app 嘅「Notice / RouteUpdateNotice」頁）。
 *
 * ⚠️ 同現有 `AppViewModel.loadRouteNotices()` **唔同**：
 *      現有嗰個係「單一路線嘅官方港鐵服務公告 + 由 ETA 數據推導嘅路況」；
 *      呢個係「全港路線改動列表」，兩者並存、唔互相取代。
 *
 * 來源用開源專案 hkbus/hk-independent-bus-eta 嘅 `public/notice.json`
 * （GPL-3.0，同 hkbus.app 同源）。**三個候選 URL 逐一試**，任何一個通就收；
 * 全部失敗就顯示「暫無改動公告」，絕唔會崩、唔會阻塞出包。
 * 若你 fork 咗或者想改來源，淨係改下面 [SOURCES] 一個陣列就得。
 */
object RouteChangeRepository {

    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/hkbus/hk-independent-bus-eta/master/public/notice.json",
        "https://hkbus.app/notice.json",
        "https://data.hkbus.app/notice.json"
    )

    private const val FILE = "hketa_route_changes.json"
    private const val TTL_MS = 6L * 60 * 60 * 1000

    @Serializable
    data class Change(
        val title: String = "",
        val body: String = "",
        val route: String = "",
        val date: String = "",
        val source: String = ""
    )

    @Serializable
    data class Cache(
        val atMs: Long = 0L,
        val source: String = "",
        val items: List<Change> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun load(context: Context): Cache? = runCatching {
        val f = file(context)
        if (!f.exists()) return null
        json.decodeFromString<Cache>(f.readText())
    }.getOrNull()

    fun ageMs(context: Context): Long =
        System.currentTimeMillis() - (load(context)?.atMs ?: 0L)

    /** 容錯 parse：array／object 包 array／{zh,en} 雙語／純字串全部食到 */
    fun parse(raw: String, source: String): List<Change> {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val arr: JsonArray = when {
            root is JsonArray -> root
            root is JsonObject -> runCatching {
                (root["notice"] ?: root["notices"] ?: root["data"] ?: root["items"])?.jsonArray
            }.getOrNull() ?: JsonArray(emptyList())
            else -> return emptyList()
        }
        return arr.mapNotNull { el ->
            when {
                el is JsonObject -> {
                    val t = pickText(el["title"] ?: el["name"] ?: el["subject"])
                    val b = pickText(el["body"] ?: el["content"] ?: el["description"] ?: el["message"])
                    if (t.isBlank() && b.isBlank()) return@mapNotNull null
                    Change(
                        title = t.ifBlank { b.take(40) },
                        body = b,
                        route = pickText(el["route"] ?: el["routeNo"] ?: el["routes"]),
                        date = pickText(el["date"] ?: el["effectiveDate"] ?: el["updatedAt"]),
                        source = source
                    )
                }
                else -> {
                    val s = runCatching { el.jsonPrimitive.content.trim() }.getOrNull().orEmpty()
                    if (s.isBlank()) null else Change(title = s.take(80), body = s, source = source)
                }
            }
        }.distinctBy { it.title + "|" + it.body }.take(60)
    }

    /** hkbus 嘅雙語欄位通常係 {"zh":"...","en":"..."}；跟系統語言揀繁中 */
    private fun pickText(el: JsonElement?): String {
        if (el == null) return ""
        runCatching {
            val o = el.jsonObject
            val zh = runCatching { o["zh"]?.jsonPrimitive?.content }.getOrNull()
            val en = runCatching { o["en"]?.jsonPrimitive?.content }.getOrNull()
            return (zh ?: en ?: "").trim()
        }
        return runCatching { el.jsonPrimitive.content.trim() }.getOrNull().orEmpty()
    }

    suspend fun refresh(context: Context, force: Boolean = false): Cache? = withContext(Dispatchers.IO) {
        val old = load(context)
        if (!force && old != null && ageMs(context) < TTL_MS) return@withContext old
        for (url in SOURCES) {
            val raw = runCatching { Http.get(url) }.getOrNull() ?: continue
            val items = parse(raw, url)
            if (items.isEmpty()) continue
            val c = Cache(System.currentTimeMillis(), url, items)
            runCatching {
                val f = file(context)
                val tmp = File(f.absolutePath + ".tmp")
                tmp.writeText(json.encodeToString(c))
                if (!tmp.renameTo(f)) f.writeText(json.encodeToString(c))
            }
            return@withContext c
        }
        // 全部失敗：保留舊 cache（可能過期，但好過空白），並如實回傳
        old
    }
}
