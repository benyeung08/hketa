package com.hketa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.data.EtaEntry
import com.hketa.app.data.FavoriteStop
import com.hketa.app.data.StopDef
import com.hketa.app.ui.Muted
import com.hketa.app.ui.displayName
import com.hketa.app.ui.labelText
import com.hketa.app.vm.AppViewModel

/**
 * 收藏車站（App1933「收藏」分頁）：
 * 列出已收藏車站同佢哋嘅即時到站預報，撳一下即睇。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    vm: AppViewModel,
    onOpenStop: (StopDef) -> Unit
) {
    val favorites by vm.favorites.collectAsState()
    val etasMap by vm.favoriteEtas.collectAsState()
    val loading by vm.favoritesLoading.collectAsState()

    LaunchedEffect(Unit) { vm.loadFavoriteEtas() }

    if (favorites.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.fav_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Muted(stringResource(R.string.fav_empty))
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        items(items = favorites, key = { it.key }) { fav ->
            FavoriteRow(
                fav = fav,
                etas = etasMap[fav.key].orEmpty(),
                loading = loading,
                onOpen = {
                    onOpenStop(
                        StopDef(op = fav.op, id = fav.id, name = fav.name, lat = fav.lat, lon = fav.lon)
                    )
                },
                onRemove = { vm.removeFavorite(fav.key) },
                onRefresh = {
                    onOpenStop(
                        StopDef(op = fav.op, id = fav.id, name = fav.name, lat = fav.lat, lon = fav.lon)
                    )
                }
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteRow(
    fav: FavoriteStop,
    etas: List<EtaEntry>,
    loading: Boolean = false,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onRefresh: () -> Unit
) {
    var confirmRemove by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${fav.displayName()}（${fav.id}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Muted(
                        buildString {
                            append(fav.op.labelText())
                            if (fav.route.isNotBlank()) {
                                append("　${fav.route}")
                                if (fav.dest.isNotBlank()) append(" ${stringResource(R.string.route_bound_to, fav.dest)}")
                            }
                        }
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.fav_refresh)
                    )
                }
                IconButton(onClick = { confirmRemove = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fav_on),
                        contentDescription = stringResource(R.string.fav_remove),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (loading && etas.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Muted(stringResource(R.string.eta_loading))
            }

            if (etas.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                etas.take(3).forEach { e ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${e.route}　${stringResource(R.string.route_bound_to, e.dest)}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = when {
                                e.minutes == null -> stringResource(R.string.eta_dash)
                                e.minutes!! <= 0 -> stringResource(R.string.eta_soon)
                                else -> stringResource(R.string.eta_minutes, e.minutes!!)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (confirmRemove) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    androidx.compose.material3.TextButton(onClick = { confirmRemove = false }) {
                        Text(stringResource(R.string.fav_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.TextButton(onClick = {
                        confirmRemove = false
                        onRemove()
                    }) {
                        Text(
                            stringResource(R.string.fav_remove),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
