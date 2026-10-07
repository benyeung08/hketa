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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.data.RouteDef
import com.hketa.app.data.StopDef
import com.hketa.app.ui.Muted
import com.hketa.app.ui.label
import com.hketa.app.vm.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: AppViewModel,
    onOpenRoute: (RouteDef) -> Unit,
    onOpenStop: (StopDef) -> Unit
) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val stopResults by vm.stopResults.collectAsState()
    val status by vm.indexStatus.collectAsState()
    val busy by vm.busy.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Muted(status)

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        Spacer(Modifier.height(8.dp))

        if (query.isNotBlank() && results.isEmpty() && stopResults.isEmpty() && !busy) {
            Muted(stringResource(R.string.search_empty))
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
                items(items = stopResults, key = { "${it.op.name}|${it.id}" }) { s ->
                    StopRow(s, onOpenStop)
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
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
                text = "${r.op.label()}${if (r.dest.isBlank()) "" else " " + stringResource(R.string.route_bound_to, r.dest)}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (r.orig.isNotBlank()) {
                Muted(stringResource(R.string.route_bound_from, r.orig))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopRow(s: StopDef, onClick: (StopDef) -> Unit) {
    Card(
        onClick = { onClick(s) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(text = s.name, style = MaterialTheme.typography.bodyLarge)
            Muted(s.op.label())
        }
    }
}
