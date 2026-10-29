package com.hketa.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object Http {

    private const val UA = "HKATE-Android/1.0"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} @ $url")
            resp.body?.string() ?: error("empty body @ $url")
        }
    }

    suspend fun postForm(url: String, pairs: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply {
                pairs.forEach { add(it.first, it.second) }
            }.build()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code} @ $url")
                resp.body?.string() ?: error("empty body @ $url")
            }
        }

    /**
     * POST 一個 **JSON body**（`Content-Type: application/json`）。
     *
     * 港鐵巴士個接口就係要呢款 —— 官方規格嘅 example request 係
     * `{"language":"zh","routeName":"K12"}`，**唔係** form-urlencoded。
     * 用 [postForm] 送會因為 Content-Type 唔啱而攞唔到資料。
     *
     * 自己砌 JSON 字串而唔用 kotlinx.serialization —— 淨係兩三個欄位，
     * 唔值得為咗佢引入 `@Serializable` data class 同埋額外嘅 plugin 設定。
     */
    suspend fun postJson(url: String, pairs: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder("{")
            var first = true
            pairs.forEach { (k, v) ->
                if (!first) sb.append(',')
                first = false
                sb.append('"').append(escape(k)).append("\":\"").append(escape(v)).append('"')
            }
            sb.append('}')
            val body = sb.toString().toRequestBody(JSON_TYPE)
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code} @ $url")
                resp.body?.string() ?: error("empty body @ $url")
            }
        }

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}
