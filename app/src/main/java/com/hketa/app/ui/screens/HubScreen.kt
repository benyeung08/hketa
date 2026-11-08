package com.hketa.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.data.FareRepository
import com.hketa.app.data.FavoriteStop
import com.hketa.app.data.Journey
import com.hketa.app.data.JourneyKind
import com.hketa.app.data.JourneyPlanner
import com.hketa.app.data.RouteChangeRepository
import com.hketa.app.data.RouteDef
import com.hketa.app.data.UserPrefs
import com.hketa.app.vm.AppViewModel
import kotlinx.coroutines.launch

/**
 * 「更多」分頁 —— 對齊 hkbus.app 補入嘅 6 類功能，一次過收埋喺度，
 * 避免大改 3,700 行嘅 AppViewModel 同 700 行嘅 SettingsScreen。
 *
 *  ① 規劃（點對點／起訖）  ② 瀏覽歷史  ③ 自訂收藏集
 *  ④ 路線改動公告          ⑤ 車費      ⑥ 外觀（深色／字型）
 *
 * 全部資料落本地 SharedPreferences / filesDir，無帳號、無追蹤、無廣告。
 * 任何網絡失敗都 graceful 顯示「暫無」，唔會拋 exception、唔會阻塞出包。
 */
@Composable
fun HubScreen(
    vm: AppViewModel,
    onOpenRoute: (RouteDef) -> Unit = {}
) {
    val tabs = listOf(
        R.string.hub_tab_plan,
        R.string.hub_tab_recent,
        R.string.hub_tab_groups,
        R.string.hub_tab_changes,
        R.string.hub_tab_fare,
        R.string.hub_tab_appearance
    )
    var tab by remember { mutableStateOf(0) }
    val index by vm.index.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val userData by UserPrefs.data.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        UserPrefs.init(context)
        UserPrefs.pruneGroups(favorites.map { it.key }.toSet())
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.hub_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, res ->
                Tab(selected = tab == i, onClick = { tab = i }, text = {
                    Text(stringResource(res), style = MaterialTheme.typography.labelMedium)
                })
            }
        }
        when (tab) {
            0 -> PlanTab(index, onOpenRoute)
            1 -> RecentTab(userData.recent)
            2 -> GroupsTab(favorites, userData.groups)
            3 -> ChangesTab()
            4 -> FareTab()
            5 -> AppearanceTab()
            else -> Unit
        }
    }
}

// ────────────────────────── ① 點對點規劃 ──────────────────────────
@Composable
private fun PlanTab(
    index: com.hketa.app.data.IndexData,
    onOpenRoute: (RouteDef) -> Unit
) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Journey>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.plan_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = from, onValueChange = { from = it },
            label = { Text(stringResource(R.string.plan_from)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = to, onValueChange = { to = it },
            label = { Text(stringResource(R.string.plan_to)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch {
                    results = JourneyPlanner.plan(index, from, to)
                    searched = true
                    if (from.isNotBlank() && to.isNotBlank()) {
                        UserPrefs.addRecent("journey", "$from→$to", "$from → $to")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.plan_search)) }

        if (index.routeStops.isEmpty()) {
            Text(stringResource(R.string.plan_index_empty), style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        if (searched && results.isEmpty()) {
            Text(stringResource(R.string.plan_no_route), style = MaterialTheme.typography.bodySmall)
        }
        results.forEach { j ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    index.routes.firstOrNull { it.route == j.firstRoute }?.let(onOpenRoute)
                }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(
                            if (j.kind == JourneyKind.DIRECT) R.string.plan_direct
                            else R.string.plan_transfer
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(j.label, style = MaterialTheme.typography.bodyMedium)
                    if (j.kind == JourneyKind.TRANSFER) {
                        Text(
                            stringResource(R.string.plan_via, j.interchange),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        stringResource(R.string.plan_stops, j.stops),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ────────────────────────── ② 瀏覽歷史 ──────────────────────────
@Composable
private fun RecentTab(recent: List<com.hketa.app.data.RecentVisit>) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.recent_title), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { UserPrefs.clearRecent() }) {
                Text(stringResource(R.string.recent_clear))
            }
        }
        if (recent.isEmpty()) {
            Text(stringResource(R.string.recent_empty), style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        recent.forEach { v ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(v.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when (v.kind) {
                            "route" -> stringResource(R.string.recent_route)
                            "stop" -> stringResource(R.string.recent_stop)
                            else -> v.kind
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

// ────────────────────────── ③ 自訂收藏集 ──────────────────────────
@Composable
private fun GroupsTab(
    favorites: List<FavoriteStop>,
    groups: List<com.hketa.app.data.CollectionGroup>
) {
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.groups_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(stringResource(R.string.groups_name_hint)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { if (UserPrefs.addGroup(name)) name = "" },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.groups_add)) }

        if (groups.isEmpty()) {
            Text(stringResource(R.string.groups_empty), style = MaterialTheme.typography.bodySmall)
        }
        groups.forEach { g ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(g.name, style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(onClick = { UserPrefs.removeGroup(g.name) }) {
                            Text(stringResource(R.string.groups_delete))
                        }
                    }
                    Text(
                        stringResource(R.string.groups_count, g.keys.size),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    if (favorites.isEmpty()) {
                        Text(stringResource(R.string.groups_toggle_hint),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    favorites.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = f.key in g.keys,
                                onCheckedChange = { UserPrefs.toggleInGroup(g.name, f.key) }
                            )
                            Text(f.name.ifBlank { f.id }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────── ④ 路線改動公告 ──────────────────────────
@Composable
private fun ChangesTab() {
    val context = LocalContext.current
    var cache by remember { mutableStateOf<RouteChangeRepository.Cache?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        cache = RouteChangeRepository.refresh(context)
        loading = false
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.changes_title), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = {
                scope.launch {
                    loading = true
                    cache = RouteChangeRepository.refresh(context, force = true)
                    loading = false
                }
            }) { Text(stringResource(R.string.changes_retry)) }
        }
        when {
            loading -> CircularProgressIndicator()
            cache == null || cache!!.items.isEmpty() -> Text(
                stringResource(R.string.changes_empty), style = MaterialTheme.typography.bodySmall
            )
            else -> {
                Text(
                    stringResource(R.string.changes_source, cache!!.source),
                    style = MaterialTheme.typography.labelSmall
                )
                cache!!.items.forEach { c ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.title, style = MaterialTheme.typography.bodyMedium)
                            if (c.body.isNotBlank() && c.body != c.title) {
                                Text(c.body, style = MaterialTheme.typography.bodySmall)
                            }
                            if (c.route.isNotBlank() || c.date.isNotBlank()) {
                                Text(
                                    listOf(c.route, c.date).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────── ⑤ 車費 ──────────────────────────
@Composable
private fun FareTab() {
    val context = LocalContext.current
    var route by remember { mutableStateOf("") }
    var fare by remember { mutableStateOf<FareRepository.Fare?>(null) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.fare_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.fare_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = route, onValueChange = { route = it },
            label = { Text(stringResource(R.string.fare_route)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch {
                    loading = true
                    FareRepository.refresh(context)
                    fare = FareRepository.forRoute(context, route)
                    searched = true
                    loading = false
                    if (route.isNotBlank()) UserPrefs.addRecent("fare", route, route)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.fare_search)) }

        when {
            loading -> CircularProgressIndicator()
            searched && fare == null -> Text(
                stringResource(R.string.fare_empty), style = MaterialTheme.typography.bodySmall
            )
            fare != null -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    if (fare!!.full.isNotBlank()) {
                        Text(stringResource(R.string.fare_full, fare!!.full),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (fare!!.half.isNotBlank()) {
                        Text(stringResource(R.string.fare_half, fare!!.half),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(R.string.fare_source),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ────────────────────────── ⑥ 外觀 ──────────────────────────
@Composable
private fun AppearanceTab() {
    val mode by UserPrefs.darkMode.collectAsState()
    val scale by UserPrefs.fontScale.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.appearance_dark), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val opts = listOf(
                UserPrefs.DARK_SYSTEM to R.string.appearance_dark_system,
                UserPrefs.DARK_LIGHT to R.string.appearance_dark_light,
                UserPrefs.DARK_NIGHT to R.string.appearance_dark_night
            )
            opts.forEach { (v, res) ->
                if (mode == v) Button(onClick = { UserPrefs.setDarkMode(v) }) {
                    Text(stringResource(res))
                } else OutlinedButton(onClick = { UserPrefs.setDarkMode(v) }) {
                    Text(stringResource(res))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.appearance_font), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.appearance_font_value, "%.2f".format(scale)),
            style = MaterialTheme.typography.labelSmall
        )
        Slider(
            value = scale,
            onValueChange = { UserPrefs.setFontScale(it) },
            valueRange = 0.85f..1.40f,
            modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.appearance_note), style = MaterialTheme.typography.bodySmall)
    }
}
