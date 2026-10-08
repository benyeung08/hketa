package com.hketa.app.widget

import com.hketa.app.util.AppLocale
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.hketa.app.MainActivity
import com.hketa.app.R
import com.hketa.app.data.suspendCatching
import com.hketa.app.data.EtaRepository
import com.hketa.app.data.FavoritesStore
import com.hketa.app.data.Operator
import com.hketa.app.data.RailData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 桌面小工具（Widget）：顯示已收藏車站嘅下一班到站時間。
 *
 * 用傳統 RemoteViews 而唔係 Glance —— 唔使加新依賴，唔會同 Compose 版本打架，
 * 構建最穩。撳個同步圖示可即時 refresh。
 */
class HketaWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.hketa.app.widget.REFRESH"
        private const val MAX_ROWS = 5
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // goAsync()：等協程拉完 ETA 先結束廣播，避免被系統提早回收
        val result = goAsync()
        scope.launch {
            try {
                val rows = buildRows(context)
                val views = render(context, rows)
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } finally {
                result.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, HketaWidgetProvider::class.java)
            )
            onUpdate(context, mgr, ids)
        }
    }

    /** 逐個收藏站拉 ETA，揀最快嗰班 */
    private suspend fun buildRows(context: Context): List<WidgetRow> {
        val favs = FavoritesStore(context).all().take(MAX_ROWS)
        if (favs.isEmpty()) return emptyList()

        val repo = EtaRepository()
        return coroutineScope {
            favs.map { fav ->
                async {
                    val eta = suspendCatching {
                        when (fav.op) {
                            Operator.KMB -> repo.kmbEta(fav.id).let { all ->
                                (if (fav.route.isBlank()) all else all.filter { it.route == fav.route })
                                    .minByOrNull { it.minutes ?: Int.MAX_VALUE }
                            }
                            Operator.LRT -> repo.lightRailEta(fav.id)
                                .minByOrNull { it.minutes ?: Int.MAX_VALUE }
                            Operator.MTR_HR -> {
                                val line = RailData.linesOf(context, Operator.MTR_HR, fav.id)
                                    .firstOrNull()?.id ?: return@suspendCatching null
                                repo.mtrHeavyRailEta(line, fav.id) {
                                    RailData.nameOf(context, Operator.MTR_HR, it, AppLocale.isEnglish())
                                }.first.minByOrNull { it.minutes ?: Int.MAX_VALUE }
                            }
                            // 城巴／嶼巴／小巴：收藏嗰陣如果記低咗路線，
                            // 就淨係查嗰一條（1 個請求），唔會嘈 —— 所以而家全部營辦商都支援
                            Operator.CTB -> {
                                if (fav.route.isBlank()) null
                                else repo.ctbEta(fav.id, fav.route)
                                    .minByOrNull { it.minutes ?: Int.MAX_VALUE }
                            }
                            Operator.NLB -> {
                                if (fav.routeId.isBlank()) null
                                else repo.nlbEta(fav.routeId, fav.id)
                                    .minByOrNull { it.minutes ?: Int.MAX_VALUE }
                            }
                            Operator.GMB -> {
                                if (fav.routeId.isBlank()) null
                                else repo.gmbEta(fav.id, fav.routeId)
                                    .minByOrNull { it.minutes ?: Int.MAX_VALUE }
                            }
                            else -> null
                        }
                    }.getOrNull()

                    WidgetRow(
                        stop = fav.name.ifBlank { fav.id },
                        route = eta?.route ?: fav.route,
                        minutes = eta?.minutes
                    )
                }
            }.awaitAll()
        }
    }

    private fun render(context: Context, rows: List<WidgetRow>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hketa)

        // 成個 Widget 撳落去 → 開 App
        views.setOnClickPendingIntent(
            R.id.widget_root,
            pendingActivity(context)
        )
        // 同步圖示 → 即時 refresh
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            pendingRefresh(context)
        )

        views.removeAllViews(R.id.widget_container)

        if (rows.isEmpty()) {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setTextViewText(R.id.row_stop, context.getString(R.string.widget_no_fav))
            row.setTextViewText(R.id.row_route, "")
            row.setTextViewText(R.id.row_min, "")
            views.addView(R.id.widget_container, row)
            return views
        }

        rows.forEach { r ->
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setTextViewText(R.id.row_stop, r.stop)
            row.setTextViewText(R.id.row_route, r.route)
            row.setTextViewText(
                R.id.row_min,
                when {
                    r.minutes == null -> "—"
                    r.minutes!! <= 0 -> context.getString(R.string.widget_now)
                    else -> context.getString(R.string.widget_minutes, r.minutes!!)
                }
            )
            views.addView(R.id.widget_container, row)
        }
        return views
    }

    private fun pendingActivity(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    private fun pendingRefresh(context: Context): PendingIntent {
        val intent = Intent(context, HketaWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        return PendingIntent.getBroadcast(context, 1, intent, flags)
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private data class WidgetRow(
        val stop: String,
        val route: String,
        val minutes: Int?
    )
}
