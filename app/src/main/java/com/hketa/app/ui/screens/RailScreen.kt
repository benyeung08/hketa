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
import com.hketa.app.data.Operator
import com.hketa.app.data.RailLine
import com.hketa.app.data.RouteDef
import com.hketa.app.ui.Muted
import com.hketa.app.ui.label
import com.hketa.app.vm.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RailScreen(
    vm: AppViewModel,
    onOpenLine: (RouteDef) -> Unit
) {
    val groups = vm.railGroups()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        groups.forEach { (op, lines) ->
            item {
                Text(
                    text = op.label(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(items = lines, key = { "${op.name}|${it.id}" }) { line ->
                RailLineRow(op, line) { onOpenLine(vm.routeOf(op, line)) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
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
            Text(
                text = line.name,
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
