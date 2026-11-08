package com.hketa.hybrid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import okhttp3.Callback
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * JS 呼叫：window.HKETA.fetchJson(url, method, body, cbId)
 * 結果回：window.__hketaCb.resolve(cbId, jsonString) / reject(cbId, message)
 *
 * 只允許 HTTPS + 官方網域白名單，避免 WebView 變成任意代理。
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
        .followRedirects(true)
        .build()

    private fun deliver(cb: String, ok: Boolean, payload: String) {
        val fn = if (ok) "resolve" else "reject"
        // JSONObject.quote 會正確逸出換行、引號與 Unicode，直接當 JS 字串字面量用
        val script = "window.__hketaCb.$fn(${JSONObject.quote(cb)}, ${JSONObject.quote(payload)})"
        web.post { web.evaluateJavascript(script, null) }
    }

    @JavascriptInterface
    fun fetchJson(url: String, method: String, body: String, cb: String) {
        val u = runCatching { Uri.parse(url) }.getOrNull()
        if (u == null || u.scheme != "https" || u.host !in ALLOWED_HOSTS) {
            deliver(cb, false, "host not allowed: ${u?.host}")
            return
        }
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "HKETA-Hybrid/1.0")
        when (method.uppercase()) {
            "POST" -> builder.post((body.ifBlank { "{}" }).toRequestBody(JSON_MEDIA))
            else -> builder.get()
        }
        client.newCall(builder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                deliver(cb, false, e.message ?: "network error")
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) deliver(cb, false, "HTTP ${it.code}")
                    else deliver(cb, true, it.body?.string() ?: "{}")
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
        val loc = lm?.let {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { p -> runCatching { it.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
        }
        if (loc == null) {
            deliver(cb, false, "no last known location")
            return
        }
        deliver(
            cb, true,
            JSONObject().put("lat", loc.latitude).put("lng", loc.longitude).toString()
        )
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
        }.getOrNull() ?: "1.0.0"
        deliver(cb, true, v)
    }

    /** 讀取 assets/web/ 下的 JSON：file:// 頁面 fetch 本機檔會被 CORS 擋，改由原生層讀。 */
    @JavascriptInterface
    fun readAsset(name: String, cb: String) {
        if (!Regex("^[A-Za-z0-9_.-]+\\.json$").matches(name)) {
            deliver(cb, false, "invalid asset name")
            return
        }
        val text = runCatching {
            activity.assets.open("web/$name").bufferedReader().use { it.readText() }
        }.getOrNull()
        if (text == null) deliver(cb, false, "asset not found: $name")
        else deliver(cb, true, text)
    }

    @JavascriptInterface
    fun openExternal(url: String, cb: String) {
        openExternal(activity, url)
        deliver(cb, true, "ok")
    }
}
