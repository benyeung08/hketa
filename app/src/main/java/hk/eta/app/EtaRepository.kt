package hk.eta.app

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * 資料來源：
 *  1) loadAssets()  —— 打包進 APK 的 app/src/main/assets/hketa.json（離線可用）
 *  2) loadRemote()  —— 同一份 JSON 放在 GitHub repo / gh-pages，改網址即可熱更新資料
 *
 * 兩者回傳同一個 HketaData，UI 完全不用改。
 */
object EtaRepository {

    /**
     * GitHub 座標統一放喺 AppConfig.kt（OWNER / REPO），
     * 同「設定 → 檢查更新」共用，改一次就夠。
     * 若改用 gh-pages，可直接改 AppConfig.REMOTE_JSON_URL。
     */
    val REMOTE_JSON_URL: String get() = AppConfig.REMOTE_JSON_URL

    fun loadAssets(context: Context): HketaData {
        val text = context.assets.open("hketa.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        return JsonParser.parse(text)
    }

    fun loadRemote(url: String = AppConfig.REMOTE_JSON_URL): HketaData {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val text = conn.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return JsonParser.parse(text)
        } finally {
            conn.disconnect()
        }
    }

    /** 依「站位 ID + 方向」取 ETA；找不到時回傳空列表，UI 顯示「暫無到站資料」。 */
    fun etaFor(data: HketaData, stopId: String): StopEta? = data.etas[stopId]
}
