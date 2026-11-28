package hk.eta.app.ui

import java.util.concurrent.atomic.AtomicBoolean

/**
 * LAUNCH_CATALOGUE_REFRESH — 「開應用程式就更新線路資料一次」嘅純決策層。
 *
 * 無 Android／無網絡／可離線測。只負責三件事：
 *  1. [claimOnce]：每個 process 只放行一次。`Application.onCreate` 一定早過任何
 *     Activity／ViewModel，所以真實啟動永遠由 Application 攞到；EtaViewModel
 *     共用同一個 gate 做後備，唔會同 Application 同時下載兩次。
 *  2. [shouldRefresh]：任何網絡都放行，已經有 refresh 跑緊先讓路。
 *     **刻意唔理 `RouteDataPrefs.isDue` / intervalDays**，亦**冇 Wi-Fi 限制** ——
 *     用戶要求每次開 App 都更新一次，唔再等每日／每 3 日／每星期，連「0 = 手動」
 *     或者流動數據都照更新。
 *  3. [shouldMarkChecked]：只有真係成功寫入快照先更新「上次檢查」時間同通知 UI。
 *
 * 「一次」嘅定義：一次冷啟動（一個 process）一次自動更新；按 Home 再返前景、
 * 轉螢幕、切分頁都唔會再跑。設定頁嘅**手動**「線路資料更新」唔受呢個 gate
 * 限制，用戶隨時撳隨時更新。失敗唔自動重試，避免開 App 狂打 API。
 */
object LaunchCatalogueRefresh {

    private val claimed = AtomicBoolean(false)

    /** 每個 process 只返一次 true；其後一律 false。 */
    fun claimOnce(): Boolean = claimed.compareAndSet(false, true)

    /** 離線測試用；正式 code 唔會呼叫。 */
    fun resetForTest() {
        claimed.set(false)
    }

    /** 任何網絡都放行；只喺 busy 時讓路（Wi-Fi 限制已取消）。 */
    fun shouldRefresh(alreadyBusy: Boolean): Boolean = !alreadyBusy

    /** 只有真正更新成功先 markChecked + 通知 UI 重新預載。 */
    fun shouldMarkChecked(busy: Boolean, skipped: Boolean, okCount: Int): Boolean =
        !busy && !skipped && okCount > 0
}
