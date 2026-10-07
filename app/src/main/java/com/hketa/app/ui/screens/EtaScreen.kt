package com.hketa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.ui.Muted
import com.hketa.app.ui.label
import com.hketa.app.vm.AppViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtaScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val stop by vm.selectedStop.collectAsState()
    val route by vm.selectedRoute.collectAsState()
    val etas by vm.etas.collectAsState()
    val busy by vm.busy.collectAsState()
    val dash = stringResource(R.string.eta_dash)
    val soon = stringResource(R.string.eta_soon)

    // 每 20 秒自動刷新
    LaunchedEffect(stop?.op?.name, stop?.id) {
        while (true) {
            vm.refreshEta()
            delay(20_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stop?.name ?: stringResource(R.string.eta_stop_default),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        val opLabel = stop?.op?.label() ?: ""
        Muted("$opLabel　${stop?.id ?: ""}　${stringResource(R.string.eta_auto_refresh)}")

        if (route != null) {
            val d = if (route?.dest.isNullOrBlank()) "" else " " + stringResource(R.string.route_bound_to, route?.dest ?: "")
            Muted("${route?.route}$d")
        }

        Spacer(Modifier.height(12.dp))

        when {
            busy && etas.isEmpty() -> Muted(stringResource(R.string.eta_loading))
            etas.isEmpty() -> Muted(stringResource(R.string.eta_empty))
            else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(etas) { i, e ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${e.route}　" + stringResource(R.string.route_bound_to, e.dest),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (e.remark.isNotBlank()) Muted(e.remark)
                                if (e.clock.isNotBlank()) Muted(stringResource(R.string.eta_scheduled, e.clock))
                            }
                            Text(
                                text = when {
                                    e.minutes == null -> dash
                                    e.minutes!! <= 0 -> soon
                                    else -> stringResource(R.string.eta_minutes, e.minutes!!)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
