package com.hketa.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 靜態資源離線包 —— Leaflet JS／CSS。
 *
 * 改動（安全）：Leaflet 已經打包喺 `assets/hketa_offline/`，
 * 所以 **永遠唔再向 unpkg.com 發請求** —— 之前會下載 JS 再存落 filesDir，
 * 但只靠「內容有冇 `L.map`」做校驗，無 hash／簽名，而 WebView 又開咗 JS，
 * 屬可避免嘅供應鏈風險。
 *
 * 而家 `ensureDownloaded()` 只係把 APK 內置資產複製去 filesDir，
 * `shouldInterceptRequest` 照樣攔 `unpkg.com` 嘅 URL 再用本地檔回應。
 * 結果：第一次開地圖已經完全離線，亦唔會再連 CDN。
 */
class AssetCache(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "hketa_assets").apply { mkdirs() }

    /** 防並發：幾個 WebView 同時開都只會複製一次 */
    private val mutex = Mutex()

    private val assets = listOf(
        Spec(
            url = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css",
            file = "leaflet.css",
            assetPath = "hketa_offline/leaflet.css",
            mime = "text/css",
            check = { it.contains(".leaflet-") }
        ),
        Spec(
            url = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js",
            file = "leaflet.js",
            assetPath = "hketa_offline/leaflet.js",
            mime = "application/javascript",
            check = { it.contains("L.map") || it.contains("L.Map") }
        )
    )

    private data class Spec(
        val url: String,
        val file: String,
        val assetPath: String,
        val mime: String,
        val check: (String) -> Boolean
    )

    private fun specOf(url: String): Spec? = assets.firstOrNull { url.contains(it.file) }

    private fun localOf(url: String): File? = specOf(url)?.let { File(dir, it.file) }

    /** 本地有就回傳檔案（離線唔使上網），冇就 null */
    fun localFor(url: String): File? {
        val f = localOf(url) ?: return null
        return if (f.exists() && f.length() > 1024L) f else null
    }

    fun mimeFor(url: String): String =
        specOf(url)?.mime ?: "application/octet-stream"

    /** 係唔係我哋識得快取嘅資源（攔截器用） */
    fun handles(url: String): Boolean = specOf(url) != null

    /** 全部都已經有本地副本？ */
    fun isComplete(): Boolean = assets.all { File(dir, it.file).exists() }

    /**
     * 由 APK 內置 assets 複製去 filesDir。**唔發任何網絡請求**。
     * 一定要喺 IO 線程叫；失敗靜默，下次開地圖會再試。
     */
    suspend fun ensureDownloaded(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            var all = true
            for (spec in assets) {
                val f = File(dir, spec.file)
                if (f.exists() && f.length() > 1024L) continue
                val ok = runCatching {
                    val body = appContext.assets.open(spec.assetPath)
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    if (!spec.check(body)) return@runCatching false
                    val tmp = File(dir, spec.file + ".tmp")
                    tmp.writeText(body)
                    tmp.exists() && tmp.length() > 1024L && tmp.renameTo(f)
                }.getOrDefault(false)
                if (!ok) all = false
            }
            all
        }
    }
}
