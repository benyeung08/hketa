package hk.eta.app

import android.app.Application
import hk.eta.app.api.RouteDataStore
import hk.eta.app.ui.CatalogueRefreshRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Initialises the private route-data directory once, before any Activity starts.
 *
 * LAUNCH_CATALOGUE_REFRESH: 開 App 就自動更新一次線路資料。放喺 Application
 * 而唔係 Activity，因為 `onCreate` 每個 process 只行一次 —— 按 Home 再返嚟、
 * 轉螢幕、切分頁都唔會重複下載。失敗／冇 Wi-Fi 靜默跳過，唔干擾 ETA 查詢。
 */
class HkEtaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RouteDataStore.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CatalogueRefreshRunner.runOnce(this@HkEtaApp)
            } catch (e: Exception) {
                // Silent: a failed catalogue update must never disturb startup.
            }
        }
    }
}
