package hk.eta.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlin.math.max

/**
 * LOCATION_AUTO_UPDATE — 喺「定位附近路線」嘅自動定位之上，加入**前景持續更新
 * 定位位置**。用法同既有 `autoLocateIfAllowed()` 一致：
 *
 *  - **先權限**：[start] 自己再 check 一次 `LocationHelper.hasPermission()`；
 *    MainActivity 同 EtaViewModel 亦各自有閘，未授權一律唔註冊、唔打 API、
 *    唔 fallback 中環。
 *  - **唔加按鈕**：上一輪已取消「📍 重新定位」掣，`permissionHint` 仍然只係
 *    「授予定位權限」提示；呢度係系統 LocationManager 自動回報，唔係用戶撳掣。
 *  - **前景 only**：Activity `onResume` 註冊／`onPause` + `onDestroy` 移除，
 *    唔用背景 Service、唔用 WorkManager、唔用 Google Play Services。
 *  - **唔狂打 API**：系統層 `minTime = 30s`／`minDistance = 半徑 1/4（最少 50m）`，
 *    再加一層 `deliver()` 距離＋時間去重，GPS jitter 唔會觸發重新載入。
 *  - 位置真係郁咗先 call `onFix` → `vm.loadNearbyRoutes()` 重新定位並重整
 *    附近路線；ETA 自動刷新照舊只重用已快取站點，兩條鏈唔互相打架。
 *
 * Android 34 把 `onProviderEnabled/Disabled` 嘅參數標咗 @NonNull，Kotlin 可
 * 覆寫簽名係 `String` 而唔係 `String?`；`onStatusChanged` 無 @NonNull 保持
 * `String?`。三個 legacy callback 全部保留（minSdk 24 仍係 abstract）。
 */
object LocationTracker {

    const val MIN_TIME_MS = 30_000L
    const val MIN_DISTANCE_M = 50f

    data class Fix(val lat: Double, val lng: Double, val source: String, val atMillis: Long)

    private var listener: LocationListener? = null
    private var lastAccepted: Fix? = null
    private var minDistanceM = MIN_DISTANCE_M
    private var onMoved: ((Fix) -> Unit)? = null

    /** 半徑越大，觸發重整所需嘅移動距離越大；最少 50 m 對應設定頁最小半徑。 */
    fun minDistanceForRadius(radiusMeters: Int): Float =
        max(MIN_DISTANCE_M, radiusMeters * 0.25f)

    fun isRunning(): Boolean = listener != null

    /** 每次返前景／改半徑重新建立基準，避免拎住舊位置做比較。 */
    fun reset() {
        lastAccepted = null
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun start(context: Context, radiusMeters: Int, onFix: (Fix) -> Unit) {
        // 雙重閘門之一：就算 caller 誤 call，未授權都即刻收工。
        if (!LocationHelper.hasPermission(context)) {
            stop(context)
            return
        }
        // 冪等：重複 start 唔會累積 listener（onResume 會頻繁呼叫）。
        stop(context)
        val app = context.applicationContext
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return

        minDistanceM = minDistanceForRadius(radiusMeters)
        onMoved = onFix
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                deliver(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        listener = l
        for (p in providers) {
            runCatching {
                manager.requestLocationUpdates(
                    p, MIN_TIME_MS, minDistanceM, l, Looper.getMainLooper()
                )
            }
        }
    }

    /** 系統已經用 minTime/minDistance 篩過，呢度再防 GPS 抖動同重複回報。 */
    private fun deliver(location: Location) {
        val now = System.currentTimeMillis()
        val fix = Fix(location.latitude, location.longitude, "裝置定位更新", now)
        val prev = lastAccepted
        if (prev != null) {
            val moved = FloatArray(1)
            val measured = runCatching {
                Location.distanceBetween(prev.lat, prev.lng, fix.lat, fix.lng, moved)
            }.isSuccess
            if (!measured || moved[0] < minDistanceM) return
            if (now - prev.atMillis < MIN_TIME_MS) return
        }
        lastAccepted = fix
        onMoved?.invoke(fix)
    }

    @SuppressLint("MissingPermission")
    fun stop(context: Context) {
        val l = listener ?: return
        val app = context.applicationContext
        (app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.let { m ->
            runCatching { m.removeUpdates(l) }
        }
        // 清走 callback，避免 object 長期揸住 Activity 造成洩漏。
        listener = null
        onMoved = null
    }
}
