package com.hketa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.data.StopDef
import com.hketa.app.ui.Muted
import com.hketa.app.ui.labelText
import com.hketa.app.ui.plainName
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
                        // 淨係顯示站名（＋距離）—— 唔再顯示營辦商：
                        // 一張卡而家可以橫跨幾個營辦商，顯示「九巴」會令人以為
                        // 淨係得九巴，亦即係睇唔到同城巴路線。
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.stop.plainName(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Muted(distanceText(item.distanceMeters, context))
                        }
                        // 一張卡可以横跨幾個營辦商（例如九巴 ＋ 城巴同一個站牌），
                        // 顯示返有邊啲，先至知下面啲路線係邊間嘅
                        if (item.ops.size > 1) {
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                item.ops.forEachIndexed { idx, op ->
                                    if (idx > 0) Muted(" · ")
                                    Muted(op.labelText())
                                }
                            }
                        }
                        if (item.etas.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            item.etas.forEach { e ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = e.route,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.widthIn(min = 44.dp)
                                    )
                                    if (e.dest.isNotBlank()) {
                                        Text(
                                            text = stringResource(R.string.nearby_eta_to, e.dest),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 4.dp)
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                    Text(
                                        text = if (e.minutes != null)
                                            stringResource(R.string.nearby_eta_min, e.minutes)
                                        else e.clock.ifBlank {
                                            stringResource(R.string.nearby_eta_soon)
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else if (item.routes.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.routes.joinToString("　"),
                                style = MaterialTheme.typography.bodyMedium
                            )
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
