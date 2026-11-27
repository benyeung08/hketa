package hk.eta.app.ui

import android.content.Context
import hk.eta.app.api.RouteDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LAUNCH_CATALOGUE_REFRESH — Application 同 ViewModel 共用嘅單一入口。
 *
 * 兩邊都呼叫 [runOnce]，但 [LaunchCatalogueRefresh.claimOnce] 保證成個 process
 * 只會有一次真正下載。真實啟動順序永遠係 `HkEtaApp.onCreate` → Activity →
 * ViewModel，所以正常情況由 Application 攞到；ViewModel 只係安全後備。
 *
 * 唔改 `RouteDataStore.refresh()` 本身：原子寫入、`BUSY` 防重入、
 * `invalidateDownstream()` 全部沿用。成功後寫 `markChecked`（設定頁「上次更新」
 * 顯示正確）並通知搜尋頁重新預載。
 */
object CatalogueRefreshRunner {

    suspend fun runOnce(context: Context) {
        if (!LaunchCatalogueRefresh.claimOnce()) return
        val app = context.applicationContext
        if (!LaunchCatalogueRefresh.shouldRefresh(
                RouteDataPrefs.wifiOnly(app),
                RouteDataStore.onWifi(),
                RouteDataStore.refreshBusy()
            )
        ) return
        val report = withContext(Dispatchers.IO) { RouteDataStore.refresh(false) }
        if (LaunchCatalogueRefresh.shouldMarkChecked(
                report.busy, report.skipped, report.okCount
            )
        ) {
            RouteDataPrefs.markChecked(app)
            CatalogueRefreshNotifier.notifyCompleted(System.currentTimeMillis())
        }
    }
}
