package com.hketa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.data.StopDef
import com.hketa.app.ui.Muted
import com.hketa.app.ui.label
import com.hketa.app.vm.AppViewModel

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = route?.route ?: "",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        val destTxt = if (route?.dest.isNullOrBlank()) "" else " " + stringResource(R.string.route_bound_to, route?.dest ?: "")
        Muted("${route?.op?.label() ?: ""}$destTxt")

        Spacer(Modifier.height(12.dp))

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
                            Text("${i + 1}. ${s.stop.name}", style = MaterialTheme.typography.bodyLarge)
                            Muted("${s.stop.op.label()}　${s.stop.id}")
                        }
                    }
                }
            }
        }
    }
}
