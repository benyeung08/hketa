package com.hketa.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 靜態資源離線包 —— 專登為 Leaflet 而設。
 *
 * **解決嘅限制**：真實地圖以前要靠 `unpkg.com` 攞 Leaflet 嘅 JS／CSS，
 * 所以就算瓦片已經有離線包（[TileCache]），**每次開地圖都仍然要上網攞 JS** ——
 * 一冇網就連 JS 都載唔到，直接退回示意圖。
 *
 * 做法：第一次成功攞到就寫落 `filesDir/hketa_assets/`，之後
 * `shouldInterceptRequest` 攔截 `unpkg.com` 嘅請求、直接用本地副本。
 * 所以**睇過一次地圖之後，連 JS 都唔使再上網** —— 配合瓦片包就係真正離線。
 *
 * 儲存時會校驗內容（CSS 要有 `.leaflet-`、JS 要有 `L.map`），
 * 避免攞到錯誤頁／空檔當正貨。
 */
class AssetCache(context: Context) {

    private val dir = File(context.filesDir, "hketa_assets").apply { mkdirs() }

    /** 防並發：幾個 WebView 同時開都只會下載一次 */
    private val mutex = Mutex()

    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 要快取嘅資源：url → (檔名, mime, 內容校驗)
     */
    private val assets = listOf(
        Spec(
            url = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css",
            file = "leaflet.css",
            mime = "text/css",
            check = { it.contains(".leaflet-") }
        ),
        Spec(
            url = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js",
            file = "leaflet.js",
            mime = "application/javascript",
            // Leaflet 一定有 `L.map`；錯誤頁／HTML 唔會有
            check = { it.contains("L.map") || it.contains("L.Map") }
        )
    )

    private data class Spec(
        val url: String,
        val file: String,
        val mime: String,
        val check: (String) -> Boolean
    )

    private fun localOf(url: String): File? =
        assets.firstOrNull { url.contains(it.file) }?.let { File(dir, it.file) }

    /**
     * 本地有就回傳檔案（離線唔使上網），冇就 null。
     * 由 WebView 嘅 `shouldInterceptRequest` 呼叫。
     */
    fun localFor(url: String): File? {
        val f = localOf(url) ?: return null
        return if (f.exists() && f.length() > 1024L) f else null
    }

    fun mimeFor(url: String): String =
        assets.firstOrNull { url.contains(it.file) }?.mime
            ?: "application/octet-stream"

    /** 係唔係我哋識得快取嘅資源（攔截器用） */
    fun handles(url: String): Boolean = localOf(url) != null

    /** 全部都已經有本地副本？（決定使唔使發請求） */
    fun isComplete(): Boolean = assets.all { File(dir, it.file).exists() }

    /**
     * 背景下載缺咗嘅資源。已經有就唔發請求；失敗靜默（下次開地圖會再試）。
     * 一定要喺 IO 線程叫。
     */
    suspend fun ensureDownloaded(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            var all = true
            for (spec in assets) {
                val f = File(dir, spec.file)
                if (f.exists() && f.length() > 1024L) continue
                val ok = runCatching {
                    val body = client.newCall(
                        Request.Builder().url(spec.url).build()
                    ).execute().use { r ->
                        if (!r.isSuccessful) return@runCatching false
                        r.body?.string() ?: return@runCatching false
                    }
                    // 校驗：唔可以係錯誤頁／空內容
                    if (!spec.check(body)) return@runCatching false
                    // 原子寫：寫到一半崩潰都唔會留低壞檔
                    val tmp = File(dir, spec.file + ".tmp")
                    tmp.writeText(body)
                    if (tmp.exists() && tmp.length() > 1024L) {
                        tmp.renameTo(f)
                        true
                    } else {
                        false
                    }
                }.getOrDefault(false)
                if (!ok) all = false
            }
            all
        }
    }
}
