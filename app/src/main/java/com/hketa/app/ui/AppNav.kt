package com.hketa.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hketa.app.ui.screens.EtaScreen
import com.hketa.app.ui.screens.FavoritesScreen
import com.hketa.app.ui.screens.HomeScreen
import com.hketa.app.ui.screens.NearbyScreen
import com.hketa.app.ui.screens.RailScreen
import com.hketa.app.ui.screens.RouteStopsScreen
import com.hketa.app.ui.screens.SearchScreen
import com.hketa.app.R
import com.hketa.app.ui.StatusBar
import com.hketa.app.ui.screens.SettingsScreen
import com.hketa.app.vm.AppViewModel

object Dest {
    const val HOME = "home"
    const val SEARCH = "search"
    const val RAIL = "rail"
    const val ROUTE = "route"
    const val ETA = "eta"
    const val NEARBY = "nearby"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    vm: AppViewModel,
    onNeedLocation: (callback: (Boolean) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Dest.SEARCH

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (currentRoute) {
                            Dest.ROUTE -> stringResource(R.string.title_route)
                            Dest.ETA -> stringResource(R.string.title_eta)
                            Dest.NEARBY -> stringResource(R.string.title_nearby)
                            Dest.SETTINGS -> stringResource(R.string.title_settings)
                            Dest.RAIL -> stringResource(R.string.title_rail)
                            Dest.FAVORITES -> stringResource(R.string.title_favorites)
                            Dest.HOME -> stringResource(R.string.app_name)
                            else -> stringResource(R.string.app_name)
                        }
                    )
                },
                actions = {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (currentRoute == Dest.HOME || currentRoute == Dest.SEARCH ||
                currentRoute == Dest.FAVORITES || currentRoute == Dest.SETTINGS
            ) {
                // 狀態欄放喺導覽列上面（圖嗰種編輯器風格：版本 · 計數 + 三個掣）
                StatusBar(
                    vm = vm,
                    onCheckUpdate = {
                        vm.checkUpdate(true)
                        navController.navigate(Dest.SETTINGS) {
                            launchSingleTop = true
                            popUpTo(Dest.HOME) { inclusive = false }
                        }
                    },
                    onOpenFavorites = {
                        navController.navigate(Dest.FAVORITES) {
                            launchSingleTop = true
                            popUpTo(Dest.HOME) { inclusive = false }
                        }
                    }
                )
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Dest.HOME,
                        onClick = {
                            navController.navigate(Dest.HOME) {
                                launchSingleTop = true
                                popUpTo(Dest.HOME) { inclusive = false }
                            }
                        },
                        label = { Text(stringResource(R.string.tab_home)) },
                        icon = { Box(Modifier.size(1.dp)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Dest.FAVORITES,
                        onClick = {
                            navController.navigate(Dest.FAVORITES) {
                                launchSingleTop = true
                                popUpTo(Dest.HOME) { inclusive = false }
                            }
                        },
                        label = { Text(stringResource(R.string.tab_favorites)) },
                        icon = { Box(Modifier.size(1.dp)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Dest.SEARCH,
                        onClick = {
                            navController.navigate(Dest.SEARCH) {
                                launchSingleTop = true
                                popUpTo(Dest.HOME) { inclusive = false }
                            }
                        },
                        label = { Text(stringResource(R.string.tab_search)) },
                        icon = { Box(Modifier.size(1.dp)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Dest.SETTINGS,
                        onClick = {
                            navController.navigate(Dest.SETTINGS) {
                                launchSingleTop = true
                                popUpTo(Dest.HOME) { inclusive = false }
                            }
                        },
                        label = { Text(stringResource(R.string.tab_settings)) },
                        icon = { Box(Modifier.size(1.dp)) }
                    )
                }
            }
        }
    ) { padding ->

        NavHost(
            navController = navController,
            startDestination = Dest.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.HOME) {
                HomeScreen(
                    vm = vm,
                    onNeedLocation = onNeedLocation,
                    onOpenStop = { stop ->
                        vm.openStop(stop, null)
                        navController.navigate(Dest.ETA)
                    }
                )
            }
            composable(Dest.FAVORITES) {
                FavoritesScreen(
                    vm = vm,
                    onOpenStop = { stop ->
                        vm.openStop(stop, null)
                        navController.navigate(Dest.ETA)
                    }
                )
            }
            composable(Dest.SEARCH) {
                SearchScreen(
                    vm = vm,
                    onOpenRoute = {
                        vm.openRoute(it)
                        navController.navigate(Dest.ROUTE)
                    },
                    onOpenStop = {
                        vm.openStop(it, null)
                        navController.navigate(Dest.ETA)
                    },
                    onOpenRail = { navController.navigate(Dest.RAIL) },
                    onOpenNearby = { navController.navigate(Dest.NEARBY) }
                )
            }
            composable(Dest.RAIL) {
                RailScreen(
                    vm = vm,
                    onOpenLine = {
                        vm.openRoute(it)
                        navController.navigate(Dest.ROUTE)
                    }
                )
            }
            composable(Dest.ROUTE) {
                RouteStopsScreen(
                    vm = vm,
                    onOpenStop = { stop ->
                        vm.openStop(stop)
                        navController.navigate(Dest.ETA)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Dest.ETA) {
                EtaScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Dest.NEARBY) {
                NearbyScreen(
                    vm = vm,
                    onNeedLocation = onNeedLocation,
                    onOpenStop = { stop ->
                        vm.openStop(stop)
                        navController.navigate(Dest.ETA)
                    }
                )
            }
            composable(Dest.SETTINGS) {
                SettingsScreen(vm = vm)
            }
        }
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
