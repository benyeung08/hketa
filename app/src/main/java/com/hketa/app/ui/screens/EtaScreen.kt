package com.hketa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hketa.app.R
import com.hketa.app.ui.Muted
import com.hketa.app.ui.codeLabel
import com.hketa.app.ui.displayName
import com.hketa.app.ui.titleWithCode
import com.hketa.app.ui.labelText
import com.hketa.app.vm.AppViewModel
import kotlinx.coroutines.delay
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtaScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val stop by vm.selectedStop.collectAsState()
    val route by vm.selectedRoute.collectAsState()
    val etas by vm.etas.collectAsState()
    // 離線快取：冇網／官方接口冇回應嗰陣顯示嘅係上次嗰份
    val offline by vm.etaOffline.collectAsState()
    val cacheAt by vm.etaCacheAt.collectAsState()
    val busy by vm.busy.collectAsState()
    val favorites by vm.favorites.collectAsState()
    // 方向篩選（null = 自動：淨係顯示最快嗰班嘅方向）
    val direction by vm.etaDirection.collectAsState()
    val directions by vm.etaDirections.collectAsState()
    // 用局部 val 接住 —— delegated property（`by collectAsState()`）compiler 唔會幫你 smart cast
    val curStop = stop
    val isFav = if (curStop != null)
        favorites.any { it.op == curStop.op && it.id == curStop.id && it.route.isBlank() }
    else false
    val dash = stringResource(R.string.eta_dash)
    val soon = stringResource(R.string.eta_soon)

    /**
     * 淨係顯示一個方向 —— 解決「兩個方向嘅班次撈埋一齊」。
     * - 自動（預設）：用最快嗰班嘅 dest
     * - 手動揀咗：淨係顯示嗰個
     * - 「全部」：照舊全部顯示
     */
    val shownEtas = remember(etas, direction, directions) {
        val all = AppViewModel.ALL_DIRECTIONS
        when {
            direction == all -> etas
            direction != null -> etas.filter { it.dest == direction }
            else -> {
                // 自動：淨係顯示最快嗰班嘅方向
                val first = etas.firstOrNull { it.dest.isNotBlank() }?.dest
                if (first == null) etas else etas.filter { it.dest == first }
            }
        }
    }

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
        // ---- 車站卡片（同「沿途車站」列表嘅卡片風格一致：站名 / 站號 / 營辦商 分行）----
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = curStop?.displayName() ?: stringResource(R.string.eta_stop_default),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (curStop != null) {
                        IconButton(
                            onClick = { curStop?.let { vm.toggleFavorite(it, route) } },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (isFav) R.drawable.ic_fav_on else R.drawable.ic_fav_off),
                                contentDescription = stringResource(if (isFav) R.string.fav_remove else R.string.fav_add),
                                tint = if (isFav) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 站號（異常嘅 routeId 唔顯示）
                val code = curStop?.codeLabel().orEmpty()
                if (code.isNotBlank()) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // 營辦商
                val opLabel = stop?.op?.labelText() ?: ""
                if (opLabel.isNotBlank()) {
                    Muted(opLabel)
                    Spacer(Modifier.height(4.dp))
                }

                // 自動更新提示
                Muted(stringResource(R.string.eta_auto_refresh))
            }
        }

        // ---- 路線 + 目的地 ----
        if (route != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = route?.route ?: "",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                val d = route?.dest.orEmpty()
                if (d.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.route_bound_to, d),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // ---- 方向切換：一次過淨係睇一個方向 ----
        if (directions.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                directions.forEach { d ->
                    val on = direction == d
                    FilterChip(
                        selected = on,
                        onClick = { vm.setEtaDirection(if (on) null else d) },
                        label = { Text(stringResource(R.string.route_bound_to, d)) }
                    )
                }
                FilterChip(
                    selected = direction == AppViewModel.ALL_DIRECTIONS,
                    onClick = {
                        vm.setEtaDirection(
                            if (direction == AppViewModel.ALL_DIRECTIONS) null
                            else AppViewModel.ALL_DIRECTIONS
                        )
                    },
                    label = { Text(stringResource(R.string.eta_all_directions)) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ★ 離線提示：呢份係冇網時由本地快取顯示嘅，要講明唔係即時
        if (offline && cacheAt > 0L) {
            Text(
                stringResource(
                    R.string.eta_offline_hint,
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(cacheAt))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
        }

        when {
            busy && etas.isEmpty() -> Muted(stringResource(R.string.eta_loading))
            etas.isEmpty() -> Muted(stringResource(R.string.eta_empty))
            else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(shownEtas) { i, e ->
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
