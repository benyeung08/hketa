package com.hketa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.data.RouteDef
import com.hketa.app.data.StopDef
import com.hketa.app.data.StopHit
import com.hketa.app.ui.Muted
import com.hketa.app.ui.displayName
import com.hketa.app.ui.labelText
import com.hketa.app.vm.AppViewModel

/**
 * 搜尋頁：分成「路線」同「車站」兩個獨立分頁，各自有自己嘅關鍵字同結果。
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
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = stopQuery,
                onValueChange = vm::setStopQuery,
                label = { Text(stringResource(R.string.search_stops_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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

        if (tab == 0) {
            if (query.isNotBlank() && results.isEmpty() && !busy) {
                Muted(stringResource(R.string.search_empty))
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
            Muted("${hit.stop.op.labelText()}　${hit.stop.id}")

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteRow(r: RouteDef, onClick: (RouteDef) -> Unit) {
    Card(
        onClick = { onClick(r) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = r.route,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${r.op.labelText()}${if (r.dest.isBlank()) "" else " " + stringResource(R.string.route_bound_to, r.dest)}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (r.orig.isNotBlank()) {
                Muted(stringResource(R.string.route_bound_from, r.orig))
            }
        }
    }
}
