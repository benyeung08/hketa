package com.hketa.app.ui.screens

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.data.EtaEntry
import com.hketa.app.data.HomeItem
import com.hketa.app.data.StopDef
import com.hketa.app.ui.Muted
import com.hketa.app.ui.displayName
import com.hketa.app.ui.labelText
import com.hketa.app.vm.AppViewModel

/**
 * 主頁（App1933 樣式）：
 * 定位 → 附近車站 → 每站列出停靠路線嘅即時到站預報。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onNeedLocation: (callback: (Boolean) -> Unit) -> Unit,
    onOpenStop: (StopDef) -> Unit
) {
    val context = LocalContext.current
    val items by vm.homeItems.collectAsState()
    val loading by vm.homeLoading.collectAsState()
    val busy by vm.busy.collectAsState()
    val origin by vm.nearbyOrigin.collectAsState()
    val favorites by vm.favorites.collectAsState()

    var trigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(trigger) {
        if (trigger > 0) vm.loadHome(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Button(
            onClick = {
                onNeedLocation { granted -> if (granted) trigger++ }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.home_refresh))
        }

        Spacer(Modifier.height(8.dp))

        if (origin.isNotBlank()) Muted(stringResource(R.string.nearby_origin, origin))
        if (loading || (busy && items.isEmpty())) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        Spacer(Modifier.height(8.dp))

        if (items.isEmpty() && !loading && !busy) {
            Muted(stringResource(R.string.home_hint))
        }

        items.forEach { item ->
            HomeStopCard(
                item = item,
                isFav = favorites.any { it.id == item.stop.id && it.op == item.stop.op },
                onToggleFav = { vm.toggleFavorite(item.stop) },
                onOpenStop = { onOpenStop(item.stop) }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeStopCard(
    item: HomeItem,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onOpenStop: () -> Unit
) {
    Card(
        onClick = onOpenStop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.stop.displayName()}（${item.stop.id}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Muted("${item.stop.op.labelText()}　${distanceText(item.distanceMeters)}")
                }
                IconButton(onClick = onToggleFav) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            if (isFav) R.drawable.ic_fav_on else R.drawable.ic_fav_off
                        ),
                        contentDescription = stringResource(
                            if (isFav) R.string.fav_remove else R.string.fav_add
                        ),
                        tint = if (isFav) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.etas.isEmpty()) {
                if (item.routes.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Muted(item.routes.take(12).joinToString("　"))
                }
            } else {
                Spacer(Modifier.height(4.dp))
                item.etas.forEach { e -> EtaRow(e) }
                if (item.routes.size > item.etas.size) {
                    Spacer(Modifier.height(4.dp))
                    val shown = item.etas.map { it.route }.toSet()
                    Muted(
                        stringResource(
                            R.string.home_more_routes,
                            item.routes.count { it !in shown }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun EtaRow(e: EtaEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${e.route}　${stringResource(R.string.route_bound_to, e.dest)}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (e.remark.isNotBlank()) Muted(e.remark)
        }
        Text(
            text = minutesText(e.minutes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if ((e.minutes ?: 99) <= 5) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun minutesText(m: Int?): String = when {
    m == null -> stringResource(R.string.eta_dash)
    m <= 0 -> stringResource(R.string.eta_soon)
    else -> stringResource(R.string.eta_minutes, m)
}

private fun distanceText(m: Int): String =
    if (m < 1000) "${m} m" else "%.1f km".format(m / 1000.0)
