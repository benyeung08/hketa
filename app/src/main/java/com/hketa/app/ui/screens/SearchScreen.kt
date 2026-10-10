package com.hketa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hketa.app.R
import com.hketa.app.data.Operator
import com.hketa.app.data.RouteDef
import com.hketa.app.data.StopDef
import com.hketa.app.data.StopHit
import com.hketa.app.ui.Muted
import com.hketa.app.ui.RouteKeyboard
import com.hketa.app.ui.RouteSearchField
import com.hketa.app.ui.codeLabel
import com.hketa.app.ui.displayName
import com.hketa.app.ui.labelText
import com.hketa.app.vm.AppViewModel

/**
 * 搜尋頁：分成「路線」同「車站」兩個獨立分頁，各自有自己嘅關鍵字同結果。
 *
 * 版面：
 *   ① 頂部主色標題列「搜尋」
 *   ② 白色輸入框「輸入路線號碼」
 *   ③ 結果列表（一條路線一行，左邊路線號、右邊箭頭）
 *   ④ 底部自訂數字／字母鍵盤
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: AppViewModel,
    onOpenRoute: (RouteDef) -> Unit,
    onOpenStop: (StopDef) -> Unit = {},
    onOpenRail: () -> Unit = {},
    onOpenNearby: () -> Unit = {}
) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val stopQuery by vm.stopQuery.collectAsState()
    val stopResults by vm.stopResults.collectAsState()
    val status by vm.indexStatus.collectAsState()
    val busy by vm.busy.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    // 路線分頁用自訂數字／字母鍵盤（路線號只係數字+少量字母，唔使系統鍵盤）
    var showKeyboard by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- ① 頂部主色標題列 ----
        SearchTopBar()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.search_tab_routes)) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.search_tab_stops)) }
                )
            }

            Spacer(Modifier.height(12.dp))

            if (tab == 0) {
                // ---- ② 輸入框：白色底 + 「輸入路線號碼」----
                RouteSearchField(
                    value = query,
                    hint = stringResource(R.string.search_hint_route_no),
                    light = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showKeyboard = true }
                )
            } else {
                OutlinedTextField(
                    value = stopQuery,
                    onValueChange = vm::setStopQuery,
                    label = { Text(stringResource(R.string.search_stops_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onOpenRail,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                ) { Text(stringResource(R.string.tab_rail)) }
                OutlinedButton(
                    onClick = onOpenNearby,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                ) { Text(stringResource(R.string.tab_nearby)) }
            }

            Spacer(Modifier.height(8.dp))
            Muted(status)

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }

            Spacer(Modifier.height(8.dp))

            // ---- ③ 結果列表 ----
            if (tab == 0) {
                if (query.isNotBlank() && results.isEmpty() && !busy) {
                    Muted(stringResource(R.string.search_empty))
                }
                // ★ weight(1f)：列表食晒剩餘空間並可捲動，
                //   鍵盤先至有固定高度留喺底（唔加會被擠出螢幕外）
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (results.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.search_group_routes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        items(items = results, key = { it.key }) { r ->
                            RouteRow(r, onOpenRoute)
                        }
                    }
                }
            } else {
                if (stopQuery.isNotBlank() && stopResults.isEmpty() && !busy) {
                    Muted(stringResource(R.string.search_stops_empty))
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (stopResults.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.search_group_stops),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        items(items = stopResults, key = { "${it.stop.op.name}|${it.stop.id}" }) { hit ->
                            StopHitRow(hit, onOpenStop)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- ④ 鍵盤 ----
            // ---- ④ 鍵盤：固定喺底（上面嘅列表已用 weight(1f) 食晒剩餘空間）----
            if (tab == 0) {
                if (showKeyboard) {
                    RouteKeyboard(
                        // ★ 一定要經 ViewModel（appendQueryChar 入面讀 _query.value），
                        //   唔可以喺呢度寫 `query + it` —— 連撳會食字（詳見 AppViewModel 註解）
                        onKey = vm::appendQueryChar,
                        onDelete = vm::deleteQueryChar,
                        onDone = { showKeyboard = false }
                    )
                } else {
                    TextButton(onClick = { showKeyboard = true }) {
                        Text(stringResource(R.string.search_open_keyboard))
                    }
                }
            }
        }
    }
}

/** 頂部主色標題列（「搜尋」） */
@Composable
private fun SearchTopBar() {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = stringResource(R.string.tab_search),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopHitRow(hit: StopHit, onClick: (StopDef) -> Unit) {
    Card(
        onClick = { onClick(hit.stop) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = hit.stop.displayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Muted(
                listOf(hit.stop.op.labelText(), hit.stop.codeLabel())
                    .filter { it.isNotBlank() }
                    .joinToString("　")
            )

            Spacer(Modifier.height(4.dp))

            if (hit.routes.isEmpty()) {
                Muted(stringResource(R.string.search_stops_none))
            } else {
                val shown = hit.routes.take(8)
                val rest = hit.routes.size - shown.size
                val tail = if (rest > 0) " " + stringResource(R.string.search_stops_more, rest) else ""
                Text(
                    text = stringResource(R.string.search_stops_routes, shown.joinToString("　") + tail),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 路線列：左邊路線號、右邊箭頭，第二行係「往 XXX」。
 *
 * 非九巴嘅路線會喺目的地後面加「 - 營辦商簡稱」（例如「大澳 - 嶼巴」），
 * 再下一行顯示官方全名 + 「資料由 DATA.GOV.HK 提供」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteRow(r: RouteDef, onClick: (RouteDef) -> Unit) {
    val isKmb = r.op == Operator.KMB
    val destLine = if (r.dest.isBlank()) {
        stringResource(R.string.route_bound_to_plain)
    } else {
        stringResource(R.string.route_bound_to, r.dest)
    }
    val title = if (isKmb || r.dest.isBlank()) {
        destLine
    } else {
        stringResource(R.string.search_route_prefix, r.dest, r.op.label)
    }

    Card(
        onClick = { onClick(r) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：路線號
            Text(
                text = r.route,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.width(56.dp)
            )
            Spacer(Modifier.width(8.dp))
            // 中：往 XXX（+ 營辦商）+ 資料來源
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!isKmb) {
                    Spacer(Modifier.height(2.dp))
                    Muted(stringResource(r.op.sourceRes), fontSize = 11)
                }
            }
            Spacer(Modifier.width(8.dp))
            // 右：箭頭
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
