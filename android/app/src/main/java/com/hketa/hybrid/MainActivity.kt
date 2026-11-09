package com.hketa.hybrid

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Kotlin 層：載入內置前端、提供 JS 橋接、要定位權限、處理返回鍵與生命週期。
 *
 * 修復重點：
 *  - 只在 ASSET 模式注入 JavascriptInterface；REMOTE 模式不把原生能力暴露給第三方頁面。
 *  - 使用 API 24+ 的 shouldOverrideUrlLoading(view, request)，POST／非 file 導覽也能攔。
 *  - onPause/onResume 暫停 WebView 計時器，避免背景每 20 秒繼續打 API。
 *  - 定位授權完成後通知前端重新整理主頁。
 *  - onDestroy 先移除 View 再 destroy，降低 WebView 崩潰機率。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    companion object {
        // 改成 "REMOTE" 即可直接開 hkbus.app/zh（請先讀 NOTICE.md 的 GPL-3.0 提醒）
        private const val LOAD_MODE = "ASSET"
        private const val REMOTE_URL = "https://hkbus.app/zh"
        private const val ASSET_URL = "file:///android_asset/web/index.html"
        private const val REQ_LOCATION = 1001
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).also { setContentView(it) }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setGeolocationEnabled(false) // 定位統一由 EtaBridge 提供
            userAgentString = "$userAgentString HKETA-Hybrid/1.0"
        }

        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("file:///android_asset/")) false else {
                    EtaBridge.openExternal(this@MainActivity, url)
                    true
                }
            }
        }

        // 絕不在 REMOTE 模式注入：第三方頁面不應取得定位／任意 API 代理能力
        if (LOAD_MODE == "ASSET") {
            web.addJavascriptInterface(EtaBridge(this, web), "HKETA")
        }

        requestLocationPermission()
        web.loadUrl(if (LOAD_MODE == "REMOTE") REMOTE_URL else ASSET_URL)
    }

    fun requestLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQ_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            web.evaluateJavascript("window.dispatchEvent(new Event('hketa:location'))", null)
        }
    }

    override fun onPause() {
        web.onPause()
        web.pauseTimers()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.resumeTimers()
        web.onResume()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.removeAllViews()
        web.destroy()
        super.onDestroy()
    }
}
