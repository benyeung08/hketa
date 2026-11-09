package com.hketa.hybrid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * JS 呼叫：window.HKETA.fetchJson(url, method, body, cbId)
 *        window.HKETA.readIndex(cbId) / writeIndex(text, cbId) ...
 * 結果回：window.__hketaCb.resolve(cbId, jsonString) / reject(cbId, message)
 *
 * 安全修復重點（相對原版）：
 *  1. 用 OkHttp HttpUrl 統一解析與白名單校驗，避免 Uri.parse 與實際請求解析不一致。
 *  2. followRedirects(false) + followSslRedirects(false)：白名單域名不得把請求導去任意主機。
 *  3. POST 只允許港鐵巴士這一個明確路徑，其餘一律 GET。
 *  4. 回應體限制 2 MB，避免超大回應造成 OOM。
 *  5. 索引改存 filesDir（無 5MB localStorage 配額），並提供 readIndex/writeIndex 給前端。
 *  6. 定位拒絕回傳過期位置（>10 分鐘）。
 */
class EtaBridge(private val activity: MainActivity, private val web: WebView) {

    companion object {
        private val ALLOWED_HOSTS = setOf(
            "data.etabus.gov.hk",
            "rt.data.gov.hk",
            "data.etagmb.gov.hk",
            "api.github.com",
            "github.com",
            "raw.githubusercontent.com",
            "hkbus.app"
        )
        private val POST_ALLOWED_PATHS = setOf(
            "/v1/transport/mtr/bus/getSchedule"
        )
        private const val MAX_BODY_BYTES = 2L * 1024 * 1024
        private const val MAX_INDEX_CHARS = 16 * 1024 * 1024
        private const val MAX_LOCATION_AGE_MS = 10 * 60 * 1000L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun openExternal(ctx: Context, url: String) {
            val u = runCatching { Uri.parse(url) }.getOrNull() ?: return
            if (u.scheme != "https" || u.host !in ALLOWED_HOSTS) return
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val indexFile: File
        get() = File(activity.filesDir, "hketa_index.json")

    private fun deliver(cb: String, ok: Boolean, payload: String) {
        val fn = if (ok) "resolve" else "reject"
        // JSONObject.quote 會正確逸出換行、引號與 Unicode
        val script = "window.__hketaCb.$fn(${JSONObject.quote(cb)}, ${JSONObject.quote(payload)})"
        web.post { web.evaluateJavascript(script, null) }
    }

    /** 與實際發出請求使用同一個 HttpUrl 物件，避免解析歧義造成白名單繞過 */
    private fun allowedUrl(url: String): HttpUrl? {
        val h = runCatching { url.toHttpUrl() }.getOrNull() ?: return null
        if (h.scheme != "https" || h.host !in ALLOWED_HOSTS) return null
        return h
    }

    @JavascriptInterface
    fun fetchJson(url: String, method: String, body: String, cb: String) {
        val h = allowedUrl(url)
        if (h == null) {
            deliver(cb, false, "host not allowed")
            return
        }
        val isPost = method.equals("POST", ignoreCase = true)
        if (isPost && h.encodedPath !in POST_ALLOWED_PATHS) {
            deliver(cb, false, "POST not allowed for this path")
            return
        }
        val builder = Request.Builder()
            .url(h)
            .header("Accept", "application/json")
            .header("User-Agent", "HKETA-Hybrid/1.0")
        if (isPost) builder.post((body.ifBlank { "{}" }).toRequestBody(JSON_MEDIA))
        else builder.get()

        client.newCall(builder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                deliver(cb, false, e.message ?: "network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val text = runCatching {
                        val bodyObj = resp.body ?: return@runCatching "{}"
                        val buf = Buffer()
                        val src = bodyObj.source()
                        var total = 0L
                        var tooLarge = false
                        while (true) {
                            val n = src.read(buf, 8192L)
                            if (n == -1L) break
                            total += n
                            if (total > MAX_BODY_BYTES) { tooLarge = true; break }
                        }
                        if (tooLarge) null else buf.readUtf8()
                    }.getOrNull()

                    when {
                        text == null -> deliver(cb, false, "response too large or unreadable")
                        !resp.isSuccessful -> deliver(cb, false, "HTTP ${resp.code}")
                        else -> deliver(cb, true, text)
                    }
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun getLocation(cb: String) {
        val fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            activity.requestLocationPermission()
            deliver(cb, false, "location permission not granted yet")
            return
        }
        val lm = activity.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val candidates = mutableListOf<Location>()
        if (lm != null) {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { candidates.add(it) }
            }
        }
        val loc = candidates.maxByOrNull { it.time }
        if (loc == null) {
            deliver(cb, false, "no last known location")
            return
        }
        if (System.currentTimeMillis() - loc.time > MAX_LOCATION_AGE_MS) {
            deliver(cb, false, "location too old")
            return
        }
        deliver(cb, true, JSONObject()
            .put("lat", loc.latitude)
            .put("lng", loc.longitude)
            .toString())
    }

    @JavascriptInterface
    fun getVersion(cb: String) {
        val v = runCatching {
            val pm = activity.packageManager
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(activity.packageName, 0).versionName
            }
        }.getOrNull() ?: "0.0.0"
        deliver(cb, true, v)
    }

    /** 讀取 assets/web/ 下的 JSON；拒絕路徑分隔符與 ..，避免目錄穿越 */
    @JavascriptInterface
    fun readAsset(name: String, cb: String) {
        if (!Regex("^[A-Za-z0-9_.-]+\\.json$").matches(name) || name.contains("..")) {
            deliver(cb, false, "invalid asset name")
            return
        }
        val text = runCatching {
            activity.assets.open("web/$name").bufferedReader().use { it.readText() }
        }.getOrNull()
        if (text == null) deliver(cb, false, "asset not found: $name")
        else deliver(cb, true, text)
    }

    /** 索引存內部儲存，避開 WebView localStorage 約 5MB 配額 */
    @JavascriptInterface
    fun readIndex(cb: String) {
        val text = runCatching {
            if (indexFile.isFile) indexFile.readText() else ""
        }.getOrNull() ?: ""
        deliver(cb, true, text)
    }

    @JavascriptInterface
    fun writeIndex(text: String, cb: String) {
        if (text.length > MAX_INDEX_CHARS) {
            deliver(cb, false, "index too large")
            return
        }
        val ok = runCatching {
            val tmp = File(activity.filesDir, "hketa_index.json.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(indexFile)) indexFile.writeText(text)
            true
        }.getOrElse { false }
        deliver(cb, ok, if (ok) "ok" else "write failed")
    }

    @JavascriptInterface
    fun openExternal(url: String, cb: String) {
        openExternal(activity, url)
        deliver(cb, true, "ok")
    }
}
