package com.hketa.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.ui.Muted
import com.hketa.app.ui.labelText
import com.hketa.app.util.AppLocale
import com.hketa.app.vm.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val status by vm.indexStatus.collectAsState()
    val busy by vm.busy.collectAsState()
    val index by vm.index.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = stringResource(R.string.settings_logo_desc),
            modifier = Modifier
                .size(96.dp)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(12.dp))

        // ---- 語言 ----
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // 語言選擇：四個選項分兩行，揀完即時生效
        val context = LocalContext.current
        var langTag by remember { mutableStateOf(AppLocale.choice(context)) }
        AppLocale.options(context).chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { (tag, text) ->
                    val selected = langTag == tag
                    val onClick: () -> Unit = {
                        langTag = tag
                        AppLocale.apply(context, tag)
                    }
                    if (selected) {
                        Button(
                            onClick = onClick,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp, vertical = 3.dp)
                        ) { Text(text) }
                    } else {
                        OutlinedButton(
                            onClick = onClick,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp, vertical = 3.dp)
                        ) { Text(text) }
                    }
                }
            }
        }
        Muted(stringResource(R.string.settings_language_hint))

        Spacer(Modifier.height(16.dp))

        // ---- 索引 ----
        Text(stringResource(R.string.settings_index), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Muted(status)

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { vm.rebuildIndex() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.settings_rebuild)) }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { vm.deepIndexCtb() },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.settings_deep_ctb)) }
            Spacer(Modifier.height(1.dp))
            OutlinedButton(
                onClick = { vm.deepIndexGmb() },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.settings_deep_gmb)) }
        }
        Muted(stringResource(R.string.settings_deep_hint))

        Spacer(Modifier.height(16.dp))

        // ---- 統計 ----
        Text(stringResource(R.string.settings_stats), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        vm.indexStats().forEach { s ->
            Muted(stringResource(R.string.settings_stats_routes, s.op.labelText(), s.routes, s.stops))
        }
        Muted(stringResource(R.string.settings_routestops, index.routeStops.size))

        Spacer(Modifier.height(16.dp))

        // ---- 資料來源 ----
        Text(stringResource(R.string.settings_sources), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Muted(stringResource(R.string.src_kmb))
        Muted(stringResource(R.string.src_ctb))
        Muted(stringResource(R.string.src_nlb))
        Muted(stringResource(R.string.src_gmb))
        Muted(stringResource(R.string.src_mtr_bus))
        Muted(stringResource(R.string.src_mtr_hr))
        Muted(stringResource(R.string.src_lrt))

        Spacer(Modifier.height(16.dp))

        Muted(stringResource(R.string.settings_rail_builtin))
        Muted(stringResource(R.string.settings_version, "1.0.0"))
        Muted(stringResource(R.string.settings_privacy))
    }
}
