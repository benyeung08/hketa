package com.hketa.hybrid

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Kotlin 層只做四件事：載入內置前端、提供 JS 橋接、要定位權限、處理返回鍵。
 * 所有 UI 與 ETA 邏輯都在 assets/web/ 的 HTML + JavaScript。
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

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                // 只允許內置頁面；外部連結一律交給系統瀏覽器
                return if (url.startsWith("file:///android_asset/")) false else {
                    EtaBridge.openExternal(this@MainActivity, url)
                    true
                }
            }
        }

        web.addJavascriptInterface(EtaBridge(this, web), "HKETA")
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

    @Deprecated("Deprecated in Java", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
