package com.hketa.app.ui.screens

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
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
import com.hketa.app.location.LocationProvider
import com.hketa.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import com.hketa.app.util.AppLocale
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import com.hketa.app.data.PresetLocation
import com.hketa.app.data.HK_PRESET_LOCATIONS
import com.hketa.app.data.LocatePhase
import com.hketa.app.data.EtaEntry
import com.hketa.app.data.HomeItem
import com.hketa.app.data.StopDef
import com.hketa.app.ui.Muted
import com.hketa.app.ui.displayName
import com.hketa.app.ui.titleWithCode
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

    val autoLocate by vm.autoLocate.collectAsState()
    val locatePhase by vm.locatePhase.collectAsState()

    var trigger by remember { mutableIntStateOf(0) }

    // 自動：一入主頁就請求定位並開始定時重整（唔使撳掣）
    LaunchedEffect(Unit) {
        onNeedLocation { granted ->
            if (granted) {
                vm.startAutoLocate(context) {
                    LocationProvider.hasPermission(context)
                }
            }
        }
    }

    LaunchedEffect(trigger) {
        if (trigger > 0) vm.loadHome(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // 自動定位狀態（唔使撳掣，入頁即跑）
        AutoLocateBar(
            autoLocate = autoLocate,
            phase = locatePhase,
            onToggle = { vm.setAutoLocate(!autoLocate) },
            onRefreshNow = {
                onNeedLocation { granted ->
                    if (granted) {
                        vm.startAutoLocate(context) { LocationProvider.hasPermission(context) }
                        trigger++
                    }
                }
            }
        )

        // 手動選點：唔想授權定位／定位失敗時嘅兜底
        val manualLoc by vm.manualLocation.collectAsState()
        var showPicker by remember { mutableStateOf(false) }
        if (locatePhase == LocatePhase.NO_PERMISSION || manualLoc != null) {
            Spacer(Modifier.height(8.dp))
            ManualLocationBar(
                selected = manualLoc,
                expanded = showPicker,
                lang = AppLocale.current(),
                onToggleExpand = { showPicker = !showPicker },
                onPick = { loc ->
                    vm.setManualLocation(loc)
                    showPicker = false
                    vm.startAutoLocate(context) { LocationProvider.hasPermission(context) }
                    trigger++
                },
                onClear = {
                    vm.setManualLocation(null)
                    showPicker = false
                }
            )
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
                        text = item.stop.titleWithCode(),
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

/**
 * 自動定位狀態列：
 *   ● 自動定位開／關（撳圓點切換）
 *   定位中… / 已定位 · N 個站 / 搵唔到定位 / 未授權
 *   右邊一個「即刻重整」掣
 */
@Composable
private fun AutoLocateBar(
    autoLocate: Boolean,
    phase: LocatePhase,
    onToggle: () -> Unit,
    onRefreshNow: () -> Unit
) {
    val (dotColor, statusText) = when {
        !autoLocate -> MaterialTheme.colorScheme.outline to stringResource(R.string.auto_locate_off)
        phase == LocatePhase.LOCATING ->
            MaterialTheme.colorScheme.primary to stringResource(R.string.auto_locating)
        phase == LocatePhase.OK ->
            Color(0xFF43A047) to stringResource(R.string.auto_locate_ok)
        phase == LocatePhase.NO_PERMISSION ->
            Color(0xFFFFA000) to stringResource(R.string.auto_locate_no_perm)
        phase == LocatePhase.FAILED ->
            MaterialTheme.colorScheme.error to stringResource(R.string.auto_locate_failed)
        else -> MaterialTheme.colorScheme.outline to stringResource(R.string.auto_locate_idle)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 撳圓點 = 開關自動定位
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .clickable(onClick = onToggle)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.auto_locate_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onToggle)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRefreshNow) {
                Text(stringResource(R.string.home_refresh))
            }
        }
    }
}

/**
 * 手動選點：用戶唔想授權定位（或者定位失敗）嗰陣，
 * 自己揀一個區／交通樞紐，主頁照樣用嗰個座標搵附近車站。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualLocationBar(
    selected: PresetLocation?,
    expanded: Boolean,
    lang: String,
    onToggleExpand: () -> Unit,
    onPick: (PresetLocation) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_status_layers),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.manual_location_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (expanded) "▾" else "▸",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }

            val label = selected?.display(lang)
            if (label != null) {
                Spacer(Modifier.height(4.dp))
                Muted(stringResource(R.string.manual_location_current, label))
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HK_PRESET_LOCATIONS.forEach { loc ->
                        val isSel = selected?.lat == loc.lat && selected?.lon == loc.lon
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { onPick(loc) }
                        ) {
                            Text(
                                text = loc.display(lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                if (selected != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.manual_location_clear))
                    }
                }
            }
        }
    }
}
