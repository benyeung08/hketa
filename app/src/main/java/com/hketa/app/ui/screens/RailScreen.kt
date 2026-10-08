package com.hketa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.data.Operator
import com.hketa.app.data.RailLine
import com.hketa.app.data.RouteDef
import com.hketa.app.data.SpecialRouteInfo
import com.hketa.app.ui.Muted
import com.hketa.app.ui.displayName
import com.hketa.app.ui.labelText
import com.hketa.app.vm.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RailScreen(
    vm: AppViewModel,
    onOpenLine: (RouteDef) -> Unit
) {
    val groups = vm.railGroups()
    val scanning by vm.scanningSpecial.collectAsState()
    val special by vm.specialRoutes.collectAsState()

    // 載入上次嘅掃描結果（存落本地，唔使每次開頁都掃）
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadSpecialRoutes(context) }

    // 分清楚「仲未掃」同「掃咗但搵唔到」
    var hasScanned by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        groups.forEach { (op, lines) ->
            item {
                Text(
                    text = op.labelText(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(items = lines, key = { "${op.name}|${it.id}" }) { line ->
                RailLineRow(op, line) { onOpenLine(vm.routeOf(op, line)) }
            }
        }

        // 輕鐵特別班次（9xx 等）：官方冇清單，靠掃描各站預報搵返出嚟
        item {
            Spacer(Modifier.height(8.dp))
            SpecialRoutesSection(
                scanning = scanning,
                routes = special,
                hasScanned = hasScanned,
                onScan = {
                    hasScanned = true
                    vm.scanLrSpecialRoutes()
                }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * 輕鐵特別班次（例如 9xx 系列）。
 *
 * 呢啲班次唔喺常規 11 條線入面，路線列表搵唔到；
 * 但官方預報接口會返，所以撳掣掃描各站預報就可以動態搵返出嚟，
 * 唔使硬編碼 9xx 清單 —— 官方加減班次都跟到。
 */
@Composable
private fun SpecialRoutesSection(
    scanning: Boolean,
    routes: List<SpecialRouteInfo>,
    hasScanned: Boolean,
    onScan: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.rail_special_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Muted(stringResource(R.string.rail_special_hint))
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onScan,
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (scanning) stringResource(R.string.rail_special_scanning)
                else stringResource(R.string.rail_special_scan)
            )
        }

        if (scanning) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(8.dp))

        routes.forEach { r ->
            var expanded by remember(r.route) { mutableStateOf(false) }
            Card(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                colors = CardDefaults.cardColors()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = r.route,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Muted(stringResource(R.string.rail_special_stops, r.stopIds.size))
                    if (expanded && r.stopNames.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Muted(r.stopNames.joinToString("、"))
                    }
                }
            }
        }

        if (!scanning && hasScanned && routes.isEmpty()) {
            Muted(stringResource(R.string.rail_special_none))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailLineRow(op: Operator, line: RailLine, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            // 英文介面下顯示官方英文線名（如 Tuen Ma Line）
            Text(
                text = line.displayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (line.orig.isNotBlank() || line.dest.isNotBlank()) {
                Muted(stringResource(R.string.rail_termini, line.orig, line.dest, line.stops.size))
            } else {
                Muted(stringResource(R.string.rail_stops_count, line.stops.size))
            }
        }
    }
}
