package hk.eta.app

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * 資料重新載入嘅共用入口（首頁「重新整理」用；設定頁用 RouteDataUpdate 直接操作）。
 *
 * fromGitHub = true  → 由 GitHub raw 抓 hketa.json，經 RouteDataUpdate 驗證後套用
 * fromGitHub = false → 由資料快取（如有）／內建 assets 重新載入
 *
 * 兩條路都行同一份完整性驗證，唔會載入缺收費／缺班次／站位懸空嘅資料。
 * callback 一律喺主線程執行；成功會更新 AppState.data / sourceLabel / dataUpdatedAt。
 */
object DataUpdater {

    fun reload(ctx: Context, fromGitHub: Boolean, cb: (Boolean, String) -> Unit) {
        val lang = AppState.lang
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val d = if (fromGitHub) {
                    if (!AppConfig.isConfigured) {
                        error(Fmt.zh(lang, "尚未設定 GitHub 座標（AppConfig.kt）", "GitHub repo not configured (AppConfig.kt)"))
                    }
                    RouteDataUpdate.applyRemote(ctx, RouteDataUpdate.fetchRemoteText())
                } else {
                    RouteDataUpdate.loadInitial(ctx)
                }
                val s = RouteDataUpdate.stats(d)
                val byMode = s.modes.joinToString(" / ") { "${it.id}:${it.routes}" }
                val msg = Fmt.zh(
                    lang,
                    "資料已更新：v${d.version} · ${s.routes} 條路線 · ${s.stops} 個站位\n收費 ${s.withFare}／班次 ${s.withService} · $byMode",
                    "Data updated: v${d.version} · ${s.routes} routes · ${s.stops} stops\nfares ${s.withFare} / schedules ${s.withService} · $byMode"
                )
                handler.post { cb(true, msg) }
            } catch (e: Exception) {
                val msg = Fmt.zh(
                    lang,
                    "更新失敗，沿用原有資料：${e.message}",
                    "Update failed, keeping existing data: ${e.message}"
                )
                handler.post { cb(false, msg) }
            }
        }.start()
    }
}
