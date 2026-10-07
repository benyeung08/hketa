package com.hketa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.data.StopDef
import com.hketa.app.ui.Muted
import com.hketa.app.ui.label
import com.hketa.app.vm.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    vm: AppViewModel,
    onNeedLocation: (callback: (Boolean) -> Unit) -> Unit,
    onOpenStop: (StopDef) -> Unit
) {
    val context = LocalContext.current
    val nearby by vm.nearby.collectAsState()
    val busy by vm.busy.collectAsState()
    val origin by vm.nearbyOrigin.collectAsState()

    var trigger by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            val ok = vm.requestNearby(context)
            denied = !ok
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = {
                denied = false
                onNeedLocation { granted -> if (granted) trigger++ else denied = true }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.nearby_button))
        }

        Spacer(Modifier.height(8.dp))

        if (origin.isNotBlank()) Muted(stringResource(R.string.nearby_origin, origin))
        if (denied) Muted(stringResource(R.string.nearby_denied))
        if (busy) Muted(stringResource(R.string.nearby_calculating))

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items = nearby, key = { "${it.stop.op.name}|${it.stop.id}" }) { item ->
                Card(
                    onClick = { onOpenStop(item.stop) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = item.stop.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Muted("${item.stop.op.label()}　${distanceText(item.distanceMeters, LocalContext.current)}")
                        if (item.routes.isNotEmpty()) {
                            Muted(item.routes.take(12).joinToString("　"))
                        }
                    }
                }
            }
        }
    }
}

private fun distanceText(m: Int, context: android.content.Context): String =
    if (m < 1000) context.getString(R.string.nearby_meters, m)
    else context.getString(R.string.nearby_km, m / 1000.0f)
