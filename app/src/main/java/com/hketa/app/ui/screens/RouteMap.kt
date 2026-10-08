package com.hketa.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hketa.app.R
import com.hketa.app.data.StopWithSeq
import kotlin.math.max
import kotlin.math.min

/**
 * 路線地圖。
 *
 * 兩種模式：
 *   ① 真實地圖（[useOsm] = true）—— WebView 載入 Leaflet + CARTO 深色瓦片，
 *      有底圖、有街道、可縮放撳站名。唔使 API key，唔使引入 Maps SDK。
 *   ② 示意圖（[useOsm] = false）—— Canvas 按座標畫折線，零依賴、離線可用。
 *
 * 真實地圖載入失敗（冇網／CDN 唔通）會自動 fallback 落示意圖，
 * 唔會留低一塊空白。
 */
@Composable
fun RouteMapView(
    stops: List<StopWithSeq>,
    modifier: Modifier = Modifier,
    useOsm: Boolean = true
) {
    val pts = stops.map { it.stop }.filter { it.lat != 0.0 || it.lon != 0.0 }
    if (pts.size < 2) {
        Text(
            text = stringResource(R.string.map_no_coords),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    if (!useOsm) {
        SchematicMap(stops, modifier)
        return
    }

    // 真實地圖；失敗自動退回示意圖
    var failed by remember(stops) { mutableStateOf(false) }
    if (failed) {
        SchematicMap(stops, modifier)
        return
    }
    OsmRouteMap(
        stops = stops,
        modifier = modifier,
        onFailed = { failed = true }
    )
}

/**
 * 真實地圖：WebView + Leaflet。
 * 用 CARTO dark_all 瓦片（免費、免 key、深色底，配深色介面）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OsmRouteMap(
    stops: List<StopWithSeq>,
    modifier: Modifier = Modifier,
    onFailed: () -> Unit
) {
    // HTML 只喺 stops 變動時重建，避免每次重組都重新載入地圖
    val html = remember(stops) { buildOsmHtml(stops) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    // 唔好把地圖瓦片寫入磁碟快取之外的嘢
                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                }
                setBackgroundColor(AndroidColor.parseColor("#121212"))
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        // 主頁或 Leaflet CDN 出錯 → 交返示意圖
                        if (failingUrl == null ||
                            failingUrl.startsWith("about:") ||
                            failingUrl.contains("unpkg.com")
                        ) {
                            onFailed()
                        }
                    }
                }
                loadDataWithBaseURL(
                    "https://localhost/", html, "text/html", "UTF-8", null
                )
            }
        },
        update = { /* stops 變咗會經 remember 重建 HTML，由 factory 重新觸發 */ }
    )
}

/** 生成 Leaflet HTML；站名做基本跳脫，唔會因為引號穿咗個 JS 陣列 */
private fun buildOsmHtml(stops: List<StopWithSeq>): String {
    val pts = stops.map { it.stop }.filter { it.lat != 0.0 || it.lon != 0.0 }
    fun esc(s: String) = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
        .replace("'", "\\'")

    val js = pts.joinToString(",") { s -> "[${s.lat},${s.lon},\"${esc(s.name)}\"]" }

    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  html,body{margin:0;padding:0;width:100%;height:100%;background:#121212}
  #map{width:100%;height:100%;background:#121212}
  .leaflet-container{background:#121212}
  .leaflet-control-attribution{background:rgba(0,0,0,.5)!important;color:#9aa0a6!important;font-size:9px}
  .leaflet-control-attribution a{color:#9aa0a6!important}
  .leaflet-popup-content-wrapper{background:#1e1e1e;color:#e8e8e8}
  .leaflet-popup-tip{background:#1e1e1e}
</style>
</head>
<body>
<div id="map"></div>
<script>
try {
  var pts = [$js];
  var map = L.map('map', {zoomControl:true, attributionControl:true});
  L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
    maxZoom: 19,
    subdomains: 'abcd',
    attribution: '&copy; OpenStreetMap contributors &copy; CARTO'
  }).addTo(map);
  var ll = pts.map(function(p){ return [p[0], p[1]]; });
  L.polyline(ll, {color:'#7C9CFF', weight:5, opacity:0.9, lineCap:'round'}).addTo(map);
  pts.forEach(function(p, i){
    var isEnd = (i === 0 || i === pts.length - 1);
    L.circleMarker([p[0], p[1]], {
      radius: isEnd ? 7 : 5,
      color: '#ffffff',
      weight: 2,
      fillColor: isEnd ? '#FFB74D' : '#7C9CFF',
      fillOpacity: 1
    }).addTo(map).bindPopup(p[2] || '');
  });
  map.fitBounds(L.latLngBounds(ll).pad(0.18));
  window.__mapOk = true;
} catch (e) {
  window.__mapOk = false;
}
</script>
</body>
</html>
""".trimIndent()
}

/** 示意圖（離線／fallback 用）：Canvas 按座標畫折線 */
@Composable
private fun SchematicMap(
    stops: List<StopWithSeq>,
    modifier: Modifier = Modifier
) {
    val pts = stops.map { it.stop }.filter { it.lat != 0.0 || it.lon != 0.0 }
    val lats = pts.map { it.lat }
    val lons = pts.map { it.lon }
    val minLat = lats.min(); val maxLat = lats.max()
    val minLon = lons.min(); val maxLon = lons.max()

    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.onSurface
    val endColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        val padX = size.width * 0.10f
        val padY = size.height * 0.12f
        val w = size.width - padX * 2
        val h = size.height - padY * 2

        val spanLat = (maxLat - minLat).let { if (it < 1e-9) 1e-9 else it }
        val spanLon = (maxLon - minLon).let { if (it < 1e-9) 1e-9 else it }
        fun project(lat: Double, lon: Double): Offset = Offset(
            x = padX + ((lon - minLon) / spanLon * w).toFloat(),
            y = padY + ((maxLat - lat) / spanLat * h).toFloat()
        )

        val points = pts.map { project(it.lat, it.lon) }

        for (i in 0 until points.lastIndex) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        points.forEachIndexed { i, p ->
            val isEnd = i == 0 || i == points.lastIndex
            drawCircle(
                color = if (isEnd) endColor else dotColor,
                radius = if (isEnd) 8f else 5f,
                center = p
            )
            drawCircle(
                color = Color.White,
                radius = if (isEnd) 4f else 2.5f,
                center = p
            )
        }
    }
}

/** 頭尾站名，畀地圖下面嘅圖例用 */
fun routeEndpoints(stops: List<StopWithSeq>): Pair<String, String> {
    val pts = stops.map { it.stop }.filter { it.lat != 0.0 || it.lon != 0.0 }
    return (pts.firstOrNull()?.name.orEmpty()) to (pts.lastOrNull()?.name.orEmpty())
}
