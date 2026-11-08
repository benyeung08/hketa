package com.hketa.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 車費（🔴 之前 HkbusIndex／HeadwayDb 都刻意「fare 一件都唔要」，所以 App 無車費）。
 *
 * 資料源同現有索引一樣：hkbus/hk-bus-crawling 每日更新嘅
 * `https://data.hkbus.app/routeFareList.min.json`（[HkbusIndex.URL]）。
 * 唔引入新依赖、唔另開 API；只係由同一份 raw 度**多抽一個 `fare` 欄位**，
 * 抽出嚟嘅細表存 `filesDir/hketa_fares.json`，唔會長駐記憶體。
 *
 * 容錯：唔同版本嘅 crawling `fare` 可能係
 *   - JsonArray：["6.0","3.0"]（全程／半價）
 *   - JsonObject：{ "full": "6.0", "half": "3.0" } / "normal" / "adult" / "child"
 *   - JsonPrimitive：單一票價
 * 三種都食到；搵唔到就當無票價，絕唔會亂砌一個數。
 */
object FareRepository {

    private const val FILE = "hketa_fares.json"
    private const val TTL_MS = 24L * 60 * 60 * 1000

    @Serializable
    data class Fare(
        val full: String = "",
        val half: String = "",
        val raw: String = ""
    ) {
        val hasValue: Boolean get() = full.isNotBlank() || half.isNotBlank()
    }

    @Serializable
    data class Cache(
        val atMs: Long = 0L,
        val count: Int = 0,
        /** key = "route+serviceType" 或純 route 號，兩邊都寫方便查 */
        val fares: Map<String, Fare> = emptyMap()
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

    /** 由 routeList 抽票價；純函式，方便單測 */
    fun parse(raw: String): Map<String, Fare> {
        val out = LinkedHashMap<String, Fare>()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return out
        val routeList = runCatching { root["routeList"]?.jsonObject }.getOrNull()
            ?: return out
        routeList.forEach { (_, value) ->
            val o = runCatching { value.jsonObject }.getOrNull() ?: return@forEach
            val route = str(o["route"]) ?: return@forEach
            val svc = (str(o["serviceType"]) ?: "01").trim().ifBlank { "01" }
            val el = o["fare"] ?: o["fares"] ?: return@forEach
            val (full, half) = extract(el)
            if (full.isBlank() && half.isBlank()) return@forEach
            val f = Fare(full, half, el.toString().take(120))
            // 三種 key 都寫：帶 serviceType／純號／補零前嘅 serviceType
            out["$route+$svc"] = f
            out.putIfAbsent(route, f)
            val short = svc.trimStart('0').ifBlank { "0" }
            if (short != svc) out.putIfAbsent("$route+$short", f)
        }
        return out
    }

    private fun extract(el: JsonElement): Pair<String, String> {
        // ① array：頭兩個數值當全程／優惠
        runCatching {
            val a = el.jsonArray
            val nums = a.mapNotNull { num(it) }
            if (nums.isNotEmpty()) return fmt(nums[0]) to (nums.getOrNull(1)?.let(::fmt) ?: "")
        }
        // ② object：常見欄位名
        runCatching {
            val o = el.jsonObject
            val full = firstNum(o, "full", "normal", "adult", "octopus", "fare", "price")
            val half = firstNum(o, "half", "child", "senior", "concession", "student")
            if (full != null || half != null) {
                return (full?.let(::fmt) ?: "") to (half?.let(::fmt) ?: "")
            }
        }
        // ③ primitive
        val one = num(el)
        if (one != null) return fmt(one) to ""
        return "" to ""
    }

    private fun firstNum(o: kotlinx.serialization.json.JsonObject, vararg names: String): Double? {
        for (n in names) {
            val v = num(o[n]) ?: continue
            return v
        }
        return null
    }

    private fun num(el: JsonElement?): Double? = runCatching {
        el?.jsonPrimitive?.content?.trim()?.replace("$", "")?.toDoubleOrNull()
    }.getOrNull()?.takeIf { it in 0.0..999.0 }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) "$${v.toLong()}" else "$${"%.1f".format(v)}"

    private fun str(el: JsonElement?): String? = runCatching {
        el?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * 下載 + 落碟。失敗保留舊 cache（唔會清空用戶已睇到嘅票價）。
     * 回傳抽出咗幾多條；0 = 今次冇更新。
     */
    suspend fun refresh(context: Context, force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val old = load(context)
        if (!force && old != null && ageMs(context) < TTL_MS && old.fares.isNotEmpty()) return@withContext 0
        val raw = runCatching { Http.get(HkbusIndex.URL) }.getOrNull() ?: return@withContext 0
        val fares = parse(raw)
        if (fares.isEmpty()) return@withContext 0
        val cache = Cache(System.currentTimeMillis(), fares.size, fares)
        runCatching {
            val f = file(context)
            val tmp = File(f.absolutePath + ".tmp")
            tmp.writeText(json.encodeToString(cache))
            if (!tmp.renameTo(f)) f.writeText(json.encodeToString(cache))
        }
        fares.size
    }

    /** 查一條路線嘅票價；搵唔到返 null（UI 要顯示「暫無票價」，唔好顯示 $0） */
    fun forRoute(context: Context, route: String, serviceType: String = "01"): Fare? {
        val c = load(context) ?: return null
        val r = route.trim()
        val s = serviceType.trim().ifBlank { "01" }
        return c.fares["$r+$s"]
            ?: c.fares["$r+${s.trimStart('0').ifBlank { "0" }}"]
            ?: c.fares[r]
    }
}
