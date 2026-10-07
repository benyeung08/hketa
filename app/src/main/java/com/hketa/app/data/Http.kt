package com.hketa.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Http {

    private const val UA = "HKETA-Android/1.0"

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
}
