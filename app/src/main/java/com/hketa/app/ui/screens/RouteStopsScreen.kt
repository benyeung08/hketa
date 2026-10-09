package com.hketa.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hketa.app.R
import com.hketa.app.data.Notice
import com.hketa.app.data.StopDef
import com.hketa.app.ui.Muted
import com.hketa.app.ui.displayName
import com.hketa.app.ui.labelText
import com.hketa.app.vm.AppViewModel

/** 分頁：0=車站 1=班次 2=公告 */
private const val TAB_STOPS = 0
private const val TAB_TIMETABLE = 1
private const val TAB_NOTICES = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteStopsScreen(
    vm: AppViewModel,
    onOpenStop: (StopDef) -> Unit,
    onBack: () -> Unit
) {
    val route by vm.selectedRoute.collectAsState()
    val stops by vm.routeStops.collectAsState()
    val busy by vm.busy.collectAsState()
    val timetable by vm.routeTimetable.collectAsState()
    val notices by vm.routeNotices.collectAsState()
    val noticesBusy by vm.routeNoticesBusy.collectAsState()

    var tab by remember { mutableStateOf(TAB_STOPS) }
    var showMap by remember { mutableStateOf(false) }

    // 切去「班次」／「公告」嗰陣先抓，避免開頁就打一大輪請求
    LaunchedEffect(tab, route?.key) {
        val rd = route ?: return@LaunchedEffect
        when (tab) {
            TAB_TIMETABLE -> if (timetable == null) vm.loadRouteTimetable(rd)
            TAB_NOTICES -> if (notices.isEmpty() && !noticesBusy) vm.loadRouteNotices(rd)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ---- 標題（同之前一樣）----
        Text(
            text = route?.route ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        val destTxt =
            if (route?.dest.isNullOrBlank()) ""
            else " " + stringResource(R.string.route_bound_to, route?.dest ?: "")
        Muted("${route?.op?.labelText() ?: ""}$destTxt")

        Spacer(Modifier.height(12.dp))

        // ---- 分頁 ----
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == TAB_STOPS, onClick = { tab = TAB_STOPS }) {
                Text(stringResource(R.string.rs_tab_stops), modifier = Modifier.padding(12.dp))
            }
            Tab(selected = tab == TAB_TIMETABLE, onClick = { tab = TAB_TIMETABLE }) {
                Text(stringResource(R.string.rs_tab_timetable), modifier = Modifier.padding(12.dp))
            }
            Tab(selected = tab == TAB_NOTICES, onClick = { tab = TAB_NOTICES }) {
                Text(stringResource(R.string.rs_tab_notices), modifier = Modifier.padding(12.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        when (tab) {
            TAB_STOPS -> StopsTab(
                vm = vm,
                stops = stops,
                busy = busy,
                showMap = showMap,
                onToggleMap = { showMap = !showMap },
                onOpenStop = onOpenStop
            )
            TAB_TIMETABLE -> TimetableTab(
                vm = vm,
                timetable = timetable,
                busy = busy
            )
            TAB_NOTICES -> NoticesTab(
                notices = notices,
                busy = noticesBusy
            )
        }
    }
}

// ==================== 車站 ====================

@Composable
private fun StopsTab(
    vm: AppViewModel,
    stops: List<com.hketa.app.data.StopWithSeq>,
    busy: Boolean,
    showMap: Boolean,
    onToggleMap: () -> Unit,
    onOpenStop: (StopDef) -> Unit
) {
    val route by vm.selectedRoute.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        if (stops.isNotEmpty()) {
            OutlinedButton(onClick = onToggleMap, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (showMap) R.string.map_hide else R.string.map_show))
            }
            Spacer(Modifier.height(8.dp))
        }

        if (showMap && stops.isNotEmpty()) {
            Text(
                stringResource(R.string.map_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            RouteMapView(stops)
            val (firstStop, lastStop) = routeEndpoints(stops)
            if (firstStop.isNotBlank() || lastStop.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Muted("$firstStop ⇄ $lastStop")
            }
            Spacer(Modifier.height(8.dp))
        }

        when {
            busy && stops.isEmpty() -> Muted(stringResource(R.string.stops_loading))
            stops.isEmpty() -> Muted(stringResource(R.string.stops_empty))
            else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(items = stops, key = { _, s -> "${s.stop.op.name}|${s.stop.id}" }) { i, s ->
                    Card(
                        onClick = { onOpenStop(s.stop) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${i + 1}. ${s.stop.displayName()}", style = MaterialTheme.typography.bodyLarge)
                            Muted("${s.stop.op.labelText()}　${s.stop.id}")
                        }
                    }
                }
            }
        }
    }
}

// ==================== 班次時間表 ====================

/**
 * 班次時間表：打直顯示，**淨係顯示最快開出嗰班（第 1 班）**。
 *
 * 之前係打橫（每格一個站、可橫向捲），站多嗰陣要捾好耐；
 * 而家改為一張卡一個站，由上到下就係行車次序，同「車站」分頁一致。
 *
 * 構建方式冇變：官方 ETA 本身已按到站先後排好，第 N 項就係第 N 班車，
 * 所以「第 1 班」= 每個站 ETA 清單嘅第 1 項 —— 係真實資料，唔係估算。
 */
@Composable
private fun TimetableTab(
    vm: AppViewModel,
    timetable: com.hketa.app.data.RouteTimetable?,
    busy: Boolean
) {
    val route by vm.selectedRoute.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.tt_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { route?.let { vm.loadRouteTimetable(it) } }) {
                Text(stringResource(R.string.tt_reload), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        // 說明：講清楚呢份表係點嚟嘅，唔會令人以為係官方紙本時刻表
        Muted(stringResource(R.string.tt_note))

        Spacer(Modifier.height(10.dp))

        when {
            busy && timetable == null -> {
                Muted(stringResource(R.string.tt_loading))
            }
            timetable == null || timetable.trips.isEmpty() -> {
                Muted(stringResource(R.string.tt_empty))
            }
            else -> {
                // ★ 淨係顯示最快開出嗰班
                val trip = timetable.trips.first()

                // 標題：第1班（最快開出）
                Text(
                    stringResource(R.string.tt_next_trip),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))

                timetable.headwayMin?.let {
                    Text(
                        stringResource(R.string.tt_headway, it),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                }

                val f = timetable.firstObserved
                val l = timetable.lastObserved
                if (f != null && l != null) {
                    Muted(stringResource(R.string.tt_observed, f, l, timetable.trips.size))
                } else {
                    Muted(stringResource(R.string.tt_observed_none))
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ★ 打直：一張卡一個站，由上到下就係行車次序
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(
                        items = trip.cells,
                        key = { i, _ -> i }
                    ) { i, cell ->
                        val name = timetable.stopNames.getOrNull(i) ?: "#${i + 1}"
                        val mins = cell.minutes
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 左：站序
                                Text(
                                    text = "${i + 1}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(22.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                // 中：站名 + 預計到站時刻
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (cell.clock.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Muted(
                                            stringResource(
                                                R.string.tt_arrive_at,
                                                cell.clock
                                            ),
                                            fontSize = 12
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                // 右：仲有幾多分鐘
                                val near = (mins ?: 99) <= 5
                                Text(
                                    text = when {
                                        mins == null -> "\u2014"
                                        mins <= 0 -> stringResource(R.string.tt_now)
                                        else -> stringResource(R.string.tt_in_min, mins)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = if (near) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 公告 ====================

@Composable
private fun NoticesTab(
    notices: List<Notice>,
    busy: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.rs_tab_notices),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))

        // ★ 講清楚呢啲唔係官方公告（除咗港鐵系）
        Muted(stringResource(R.string.notice_disclaimer), fontSize = 11)

        Spacer(Modifier.height(8.dp))

        when {
            busy && notices.isEmpty() -> Muted(stringResource(R.string.tt_loading))
            notices.isEmpty() -> Muted(stringResource(R.string.notice_empty))
            else -> LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(items = notices, key = { i, _ -> i }) { _, n ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    n.source,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Muted(n.time, fontSize = 11)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                n.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (n.body.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Muted(n.body)
                            }
                            if (n.url.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(n.url)
                                            )
                                        )
                                    }
                                }) {
                                    Text(stringResource(R.string.notice_view), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
