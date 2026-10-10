package com.hketa.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.hketa.app.R
import com.hketa.app.data.UpdateChecker
import com.hketa.app.data.UpdateState
import com.hketa.app.ui.Muted
import com.hketa.app.ui.labelText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.hketa.app.util.AppLocale
import com.hketa.app.vm.AppViewModel

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenAbout: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    val status by vm.indexStatus.collectAsState()
    val busy by vm.busy.collectAsState()
    val index by vm.index.collectAsState()
    val updateState by vm.updateState.collectAsState()
    val updateInfo by vm.updateInfo.collectAsState()
    val updateError by vm.updateError.collectAsState()

    // 一入設定頁就靜默檢查一次（節流；手動撳掣可以即時再查）
    LaunchedEffect(Unit) { vm.checkUpdate() }

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

        OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_title))
        }

        Spacer(Modifier.height(16.dp))

        // ---- 語言 ----
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // 語言選擇：四個選項分兩行，揀完即時生效
        val context = LocalContext.current
        val currentVersion = remember { UpdateChecker.currentVersion(context) }
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

        // ---- ETA 資料修復（原「路線索引」）----
        Text(stringResource(R.string.settings_eta_repair), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Muted(status)

        Spacer(Modifier.height(12.dp))

        Muted(stringResource(R.string.eta_repair_hint))

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { vm.rebuildIndex() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.eta_repair_main)) }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { vm.deepIndexCtb() },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.eta_repair_ctb)) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { vm.deepIndexGmb() },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.eta_repair_gmb)) }
        }

        Spacer(Modifier.height(8.dp))

        // ---- 逐個營辦商修復（九巴／城巴／嶼巴／專線小巴／港鐵巴士／港鐵／輕鐵）----
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_eta_repair),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Muted(stringResource(R.string.eta_repair_per_op_hint))

        Spacer(Modifier.height(8.dp))

        com.hketa.app.data.Operator.values().toList().chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { op ->
                    OutlinedButton(
                        onClick = { vm.repairOperator(op) },
                        enabled = !busy,
                        modifier = if (row.size == 1) Modifier.fillMaxWidth() else Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.eta_repair_op, op.labelText()))
                    }
                    if (op != row.last()) Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = { vm.clearEtaCache() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.eta_repair_clear)) }

        Spacer(Modifier.height(16.dp))

        // ---- 統計 ----
        Text(stringResource(R.string.settings_stats), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        vm.indexStats().forEach { s ->
            Muted(stringResource(R.string.settings_stats_routes, s.op.labelText(), s.routes, s.stops))
        }
        Muted(stringResource(R.string.settings_routestops, index.routeStops.size))

        // ---- 索引診斷：講清楚邊個營辦商點解係 0 ----
        val diag = vm.indexErrors.collectAsState().value
        if (diag.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_index_diag),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            diag.forEach { (op, msg) ->
                Muted("• ${op.labelText()}：${'$'}msg")
            }
        }

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

        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.map_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Muted(stringResource(R.string.settings_widget_hint))

        Spacer(Modifier.height(12.dp))

        Muted(stringResource(R.string.settings_rail_builtin))
        Muted(stringResource(R.string.settings_version, currentVersion))
        Muted(stringResource(R.string.settings_privacy))

        Spacer(Modifier.height(16.dp))

        // ---- 版本更新（改為 code-to-app 嘅 VersionPill 膠囊）----
        //   v1.0.4 · 5              ⟳   ◷   ▤
        //   撳膠囊 = 檢查更新；右邊三個掣 = 檢查更新 / 版本歷史 / 複製版本號
        Text(
            stringResource(R.string.update_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))

        val (pkgVersionName, pkgVersionCode) = runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val n = pi.versionName ?: "1.0.0"
            @Suppress("DEPRECATION")
            val c = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                pi.versionCode.toLong()
            }
            n to c
        }.getOrDefault("1.0.0" to 1L)
        val shownVersion = currentVersion.ifBlank { pkgVersionName }
        val hasUpdate = updateState == UpdateState.AVAILABLE

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            UpdatePill(
                versionName = shownVersion,
                versionCode = pkgVersionCode,
                hasUpdate = hasUpdate,
                checking = updateState == UpdateState.CHECKING,
                onCheckUpdate = { vm.checkUpdate(force = true) },
                onOpenHistory = onOpenHistory
            )
        }

        Spacer(Modifier.height(10.dp))

        // 狀態文字（放膠囊下面，居中）
        val statusText = when (updateState) {
            UpdateState.CHECKING -> stringResource(R.string.update_checking)
            UpdateState.UP_TO_DATE -> stringResource(R.string.update_latest)
            UpdateState.NO_RELEASE -> stringResource(R.string.update_no_release)
            UpdateState.ERROR -> stringResource(R.string.update_failed, updateError)
            UpdateState.IDLE -> stringResource(R.string.update_current, currentVersion)
            UpdateState.AVAILABLE -> stringResource(
                R.string.update_available,
                updateInfo?.tag_name.orEmpty()
            )
        }
        Text(
            text = statusText,
            color = if (hasUpdate) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            },
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // 有新版先至顯示下載掣（狀態文字已經喺膠囊下面講咗）
        when (updateState) {
            UpdateState.IDLE -> Unit
            UpdateState.CHECKING -> Unit
            UpdateState.UP_TO_DATE -> Unit
            UpdateState.NO_RELEASE -> Unit
            UpdateState.ERROR -> Unit
            UpdateState.AVAILABLE -> {
                val rel = updateInfo
                if (rel != null) {
                    Text(
                        text = stringResource(R.string.update_available, rel.tag_name),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val asset = UpdateChecker.apkAsset(rel)
                    val sizeMb = UpdateChecker.sizeMb(asset)
                    val dlUrl = asset?.browser_download_url.orEmpty()

                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { openUrl(context, dlUrl.ifBlank { rel.html_url }) },
                            enabled = dlUrl.isNotBlank() || rel.html_url.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.update_download)) }
                        Spacer(Modifier.height(1.dp))
                        OutlinedButton(
                            onClick = { openUrl(context, rel.html_url) },
                            enabled = rel.html_url.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.update_view_release)) }
                    }

                    if (sizeMb > 0f) Muted(stringResource(R.string.update_size, sizeMb))

                    if (rel.body.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.update_notes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Muted(rel.body.trim().take(500))
                    }
                }
            }
        }
    }
}

/**
 * 版本膠囊 —— 移植自 code-to-app 嘅 `VersionPill`
 * （`com.webtoapp.ui.screens.AboutScreen` 第 386–457 行）。
 *
 *   v1.0.3 · 4              ⟳   ◷   ▤
 *   └── 版本名 · versionCode ──┘   │    │    └─ 複製版本號
 *                                  │    └─ 版本歷史
 *                                  └─ 檢查更新（撳成個膠囊都得）
 *
 * **寫死喺呢個檔案入面**（唔共用 StatusBar 嗰份），係因為一次次上傳失敗
 * —— 自包含嘅話，淨係傳呢一個檔案就生效，唔使同時傳 StatusBar.kt。
 */
@Composable
private fun UpdatePill(
    versionName: String,
    versionCode: Long,
    hasUpdate: Boolean,
    checking: Boolean,
    onCheckUpdate: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            // 背景跟足原版：surfaceContainerHigh @ 80%
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f))
            .clickable(onClick = onCheckUpdate)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "\u00b7",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = versionCode.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (hasUpdate) Color(0xFFFF5252)
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))

        // ① 檢查更新
        PillIcon(
            res = R.drawable.ic_status_sync,
            desc = stringResource(R.string.status_check_update),
            enabled = !checking,
            size = 14.dp,
            tint = MaterialTheme.colorScheme.primary,
            onClick = onCheckUpdate
        )
        Spacer(Modifier.width(10.dp))
        // ② 版本歷史
        PillIcon(
            res = R.drawable.ic_status_clock,
            desc = stringResource(R.string.status_version_history),
            enabled = true,
            size = 15.dp,
            tint = MaterialTheme.colorScheme.primary,
            onClick = onOpenHistory
        )
        Spacer(Modifier.width(10.dp))
        // ③ 複製版本號
        PillIcon(
            res = R.drawable.ic_status_copy,
            desc = stringResource(R.string.status_copy_version),
            enabled = true,
            size = 14.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = {
                runCatching {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "HKATE version",
                            "HKATE v$versionName ($versionCode)"
                        )
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.status_version_copied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun PillIcon(
    res: Int,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit,
    /** 原版三個 icon 尺寸唔同：Sync 14 / History 15 / Copy 14 */
    size: Dp = 16.dp,
    tint: Color = Color.Unspecified
) {
    Icon(
        painter = androidx.compose.ui.res.painterResource(res),
        contentDescription = desc,
        tint = if (tint != Color.Unspecified) tint
               else if (enabled) Color.White else Color(0xFF6A6A6A),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .size(size)
            .padding(0.dp)
    )
}

/** 用瀏覽器打開下載／Release 頁面 */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
