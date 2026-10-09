package com.hketa.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * 地圖瓦片嘅**離線包**。
 *
 * 之前嘅限制：「真實地圖第一次要聯網」—— 睇過嘅路線靠 WebView 快取先出到圖，
 * 但 WebView 快取會被系統清、唔可靠，而且**完全冇得主動控制**。
 *
 * 而家改為自己管：
 *   ① 顯示地圖嗰陣**自動喺背景預取**呢條路線嘅瓦片（zoom 12–16）
 *   ② WebView 攞瓦片時由 `shouldInterceptRequest` 攔截，本地有就用本地
 *   ③ 所以睇過一次嘅路線，**完全離線（飛機模式）都出到真實地圖**
 *
 * 瓦片來源係 CARTO dark_all（免費、免 key），同地圖用嘅係同一個 URL，
 * 所以離線出嘅圖同在線嗰個一模一樣。
 */
class TileCache(context: Context) {

    private val dir = File(context.filesDir, "hketa_tiles").apply { mkdirs() }

    // CARTO 嘅 subdomain（a/b/c/d）—— 離線時邊個都無所謂，統一存 a
    private val host = "basemaps.cartocdn.com"

    /** 預取嘅 zoom 範圍：12（全港輪廓）到 16（街道級） */
    private val prefetchZooms = 12..16

    // ---------- 檔案路徑 ----------

    private fun fileOf(z: Int, x: Int, y: Int): File =
        File(dir, "$z/$x/$y.png")

    fun has(z: Int, x: Int, y: Int): Boolean {
        val f = fileOf(z, x, y)
        return f.exists() && f.length() > 0
    }

    fun bytesOf(z: Int, x: Int, y: Int): ByteArray? = runCatching {
        val f = fileOf(z, x, y)
        if (!f.exists() || f.length() == 0L) null else f.readBytes()
    }.getOrNull()

    private fun save(z: Int, x: Int, y: Int, bytes: ByteArray) {
        runCatching {
            val f = fileOf(z, x, y)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
    }

    // ---------- 下載 ----------

    /** 下載一枚瓦片（已存在就跳過）。失敗會靜默忽略 —— 離線包係錦上添花，唔可以拖垮主流程。 */
    suspend fun fetch(z: Int, x: Int, y: Int) {
        if (has(z, x, y)) return
        if (x < 0 || y < 0 || x >= 2.0.pow(z) || y >= 2.0.pow(z)) return
        runCatching {
            withContext(Dispatchers.IO) {
                val url = "https://a.$host/dark_all/$z/$x/$y.png"
                val req = Request.Builder().url(url).header("User-Agent", "HKETA-Android/1.0").build()
                Http.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    val bytes = body.bytes()
                    // CARTO 對超出範圍嘅瓦片會返一張 0 位元組或極細嘅圖，唔好存
                    if (bytes.size > 200) save(z, x, y, bytes)
                }
            }
        }
    }

    /**
     * 預取一條路線沿線嘅瓦片。
     *
     * 淨係預取「路線包住嗰個範圍」，唔係成個香港 —— 一條巴士線通常得
     * 幾十到百幾枚瓦片（每枚 10–30KB），幾秒就下載完，流量亦好少。
     */
    suspend fun prefetchRoute(lats: List<Double>, lons: List<Double>) {
        if (lats.size < 2) return
        val minLat = lats.min()
        val maxLat = lats.max()
        val minLon = lons.min()
        val maxLon = lons.max()
        for (z in prefetchZooms) {
            val (x0, x1) = lonToTileX(minLon, z) to lonToTileX(maxLon, z)
            val (y0, y1) = latToTileY(maxLat, z) to latToTileY(minLat, z)
            val xa = minOf(x0, x1)
            val xb = maxOf(x0, x1)
            val ya = minOf(y0, y1)
            val yb = maxOf(y0, y1)
            // 安全閥：zoom 16 嘅矩形可以好大，限制每層最多 400 枚
            var count = 0
            for (x in xa..xb) {
                for (y in ya..yb) {
                    if (count++ > 400) break
                    fetch(z, x, y)
                }
            }
        }
    }

    /** 已存咗幾多枚瓦片（診斷用） */
    fun tileCount(): Int = runCatching {
        dir.walkTopDown().count { it.isFile && it.extension == "png" }
    }.getOrDefault(0)

    /** 離線包大約幾大（bytes） */
    fun sizeBytes(): Long = runCatching {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    fun clear() = runCatching { dir.deleteRecursively(); dir.mkdirs() }

    // ---------- Web Mercator 換算 ----------

    fun lonToTileX(lon: Double, z: Int): Int =
        floor((lon + 180.0) / 360.0 * 2.0.pow(z)).toInt().coerceIn(0, 2.0.pow(z).toInt() - 1)

    fun latToTileY(lat: Double, z: Int): Int {
        val latRad = lat * PI / 180.0
        val n = 2.0.pow(z)
        val y = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n
        return floor(y).toInt().coerceIn(0, n.toInt() - 1)
    }

    @Suppress("unused")
    private fun tileXToLon(x: Int, z: Int): Double =
        x / 2.0.pow(z) * 360.0 - 180.0

    @Suppress("unused")
    private fun tileYToLat(y: Int, z: Int): Double {
        val n = PI - 2.0 * PI * y / 2.0.pow(z)
        return 180.0 / PI * atan(sinh(n))
    }
}
