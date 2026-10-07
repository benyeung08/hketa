package com.hketa.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hketa.app.data.StopWithSeq
import kotlin.math.max
import kotlin.math.min

/**
 * 路線地圖（輕量版）：用車站經緯度喺 Canvas 上畫出走線。
 *
 * 唔使引入 OSMDroid / Google Maps SDK —— 零依賴、零 API key，
 * 純粹按座標投影成一條折線同車站圓點，用嚟睇路線走向同相對位置已經夠。
 */
@Composable
fun RouteMapView(
    stops: List<StopWithSeq>,
    modifier: Modifier = Modifier
) {
    val pts = stops.map { it.stop }.filter { it.lat != 0.0 || it.lon != 0.0 }
    if (pts.size < 2) {
        androidx.compose.material3.Text(
            text = androidx.compose.ui.res.stringResource(com.hketa.app.R.string.map_no_coords),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

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

        // 經緯度 → 畫布座標（保持長闊比靠各自縮放，避免細範圍俾 padding 食晒）
        val spanLat = (maxLat - minLat).let { if (it < 1e-9) 1e-9 else it }
        val spanLon = (maxLon - minLon).let { if (it < 1e-9) 1e-9 else it }
        fun project(lat: Double, lon: Double): Offset = Offset(
            x = padX + ((lon - minLon) / spanLon * w).toFloat(),
            // 緯度越大越北 → 放上面，所以要用 maxLat - lat
            y = padY + ((maxLat - lat) / spanLat * h).toFloat()
        )

        val points = pts.map { project(it.lat, it.lon) }

        // 走線
        for (i in 0 until points.lastIndex) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // 車站圓點（頭尾站放大兼換色）
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
