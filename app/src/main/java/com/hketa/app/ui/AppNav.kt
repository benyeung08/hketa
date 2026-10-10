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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
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
import com.hketa.app.ui.UpdateDialog
import com.hketa.app.ui.VersionHistorySheet
import com.hketa.app.util.AppLocale
import com.hketa.app.ui.screens.AboutScreen
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
    const val ABOUT = "about"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    vm: AppViewModel,
    onNeedLocation: (callback: (Boolean) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    // ---- 開 App 全自動：索引 + ETA 資料修復 + 定位搵附近路線 ----
    // 以前要用戶自己撳（設定頁「ETA 資料修復」、主頁「用定位搵附近路線」），
    // 而家一開 App 就自己做；bootstrap() 內部有「淨係行一次」嘅守衛。
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.bootstrap(context, onNeedLocation)
    }

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

    var showHistory by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }

    // 版本歷史（移植自 code-to-app）：線上 Releases + 本地內置 changelog
    if (showHistory) {
        VersionHistorySheet(
            releases = vm.historyReleases.collectAsState().value,
            loading = vm.historyLoading.collectAsState().value,
            error = null,
            currentVersion = vm.currentVersion.collectAsState().value,
            localEntries = com.hketa.app.data.Changelog.forLang(AppLocale.current()),
            // ★ 由「開瀏覽器下載」改為「App 內下載 + 校驗 + 安裝」
            onDownload = { release -> vm.downloadAndInstall(release) },
            onDismiss = { showHistory = false }
        )
    }

    // 檢查更新結果對話框
    if (showUpdate) {
        // 喺 @Composable 作用域讀版本資料 —— 唔可以喺普通 lambda 入面叫
        // @Composable 嘅 versionInfo()，否則會報
        // "@Composable invocations can only happen from the context of a @Composable function"
        val (navVersionName, navVersionCode) = com.hketa.app.ui.versionInfo()

        UpdateDialog(
            state = vm.updateState.collectAsState().value,
            latestVersion = vm.updateInfo.collectAsState().value?.tag_name.orEmpty(),
            currentVersion = vm.currentVersion.collectAsState().value,
            sizeMb = com.hketa.app.data.UpdateChecker.sizeMb(
                vm.updateInfo.collectAsState().value?.let {
                    com.hketa.app.data.UpdateChecker.apkAsset(it)
                }
            ),
            notes = vm.updateInfo.collectAsState().value?.body.orEmpty(),
            errorMessage = vm.message.collectAsState().value.orEmpty(),
            onDownload = {
                // App 內下載 + 校驗 + 開安裝器（移植自 code-to-app）
                vm.updateInfo.value?.let { vm.downloadAndInstall(it) }
            },
            downloadState = vm.downloadState.collectAsState().value,
            onCancelDownload = { vm.cancelDownload() },
            onInstall = {
                val st = vm.downloadState.value
                if (st is com.hketa.app.data.ApkInstaller.State.Done) {
                    vm.installDownloaded(st.file)
                    vm.resetDownload()
                }
            },
            onOpenHistory = {
                showUpdate = false
                showHistory = true
            },
            onCopyVersion = {
                com.hketa.app.ui.copyToClipboard(
                    context,
                    "HKATE version",
                    "HKATE v$navVersionName ($navVersionCode)"
                )
            },
            onDismiss = { showUpdate = false }
        )
    }

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
                        showUpdate = true
                    },
                    onOpenFavorites = {
                        navController.navigate(Dest.FAVORITES) {
                            launchSingleTop = true
                            popUpTo(Dest.HOME) { inclusive = false }
                        }
                    },
                    onOpenHistory = {
                        vm.loadVersionHistory()
                        showHistory = true
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
                    },
                    // 撳一條班次 → 打開嗰條路線嘅沿途車站
                    onOpenRoute = { rd ->
                        vm.openRoute(rd)
                        navController.navigate(Dest.ROUTE) { launchSingleTop = true }
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
            composable(Dest.ABOUT) {
                AboutScreen(
                    vm = vm,
                    onOpenSettings = {
                        navController.navigate(Dest.SETTINGS) { launchSingleTop = true }
                    },
                    onCheckUpdate = {
                        vm.checkUpdate(true)
                        showUpdate = true
                    }
                )
            }
            composable(Dest.SETTINGS) {
                SettingsScreen(
                    vm = vm,
                    onOpenAbout = { navController.navigate(Dest.ABOUT) },
                    onOpenHistory = {
                        vm.loadVersionHistory()
                        showHistory = true
                    }
                )
            }
        }
    }
}

@Composable
fun Muted(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    fontSize: Int = 0
) {
    Text(
        text = text,
        style = if (fontSize > 0)
            MaterialTheme.typography.bodySmall.copy(fontSize = fontSize.sp)
        else MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier
    )
}

