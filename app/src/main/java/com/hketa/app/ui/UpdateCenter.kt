package com.hketa.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.hketa.app.R
import com.hketa.app.data.GitHubRelease
import com.hketa.app.data.UpdateChecker
import com.hketa.app.data.UpdateState
import com.hketa.app.util.AppLocale

/**
 * 應用更新介面 —— 移植自 code-to-app（`com.webtoapp.ui.screens.AboutScreen`
 * 入面嘅 `UpdateDialog` + `VersionHistorySheet`），並改用 HKETA 嘅
 * UpdateChecker 同三語資源。
 *
 * 兩部分：
 *   ① [UpdateDialog]        —— 檢查更新結果（有新版本／已是最新／失敗）
 *   ② [VersionHistorySheet] —— 版本歷史，可展開睇 notes，可下載舊版本
 */

/**
 * 更新結果對話框。
 *
 * 移植重點（code-to-app 嘅做法）：
 *   - 四態齊全：檢查中 / 有新版本 / 已是最新 / 失敗
 *   - 顯示「新版本 → 目前版本 → 大小」，而唔係淨係一句「有新版本」
 *   - Release notes 會按目前語言自動揀（見 [UpdateChecker.localizeBody]）
 *   - 下載中會鎖住「撳外面關閉」，避免誤觸中斷
 */
/**
 * 版本更新 —— 全面改為「版本膠囊」樣式（即第二張圖嗰個 VersionPill）。
 *
 * 之前係普通 AlertDialog（標題「版本更新」+ 一句狀態 + 撳掣），
 * 而家主視覺直接係膠囊：
 *
 *   ┌──────────────────────────────────┐
 *   │ v1.0.3 · 4  ● 1      ⟳   ◷   ▤   │  ← VersionPill
 *   └──────────────────────────────────┘
 *   已經係最新版本
 *   [        檢查更新        ]            ← 淺紫膠囊掣
 *
 * 四態（檢查中 / 有新版本 / 已是最新 / 失敗）嘅說明文字放喺膠囊下面，
 * 有新版本嗰陣會多一段 Release notes。
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    latestVersion: String,
    currentVersion: String,
    sizeMb: Float,
    notes: String,
    errorMessage: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onCopyVersion: () -> Unit = {}
) {
    val context = LocalContext.current
    val lang = AppLocale.current()
    val localizedNotes = remember(notes, lang) { UpdateChecker.localizeBody(notes, lang) }

    // 由 PackageManager 讀版本名 / versionCode，同狀態欄完全一致
    val (versionName, versionCode) = com.hketa.app.ui.versionInfo()
    val hasUpdate = state == UpdateState.AVAILABLE

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161616),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ★ 主視覺：版本膠囊（第二張圖嘅樣式）
                com.hketa.app.ui.VersionPill(
                    versionName = versionName,
                    versionCode = versionCode,
                    badge = if (hasUpdate) 1 else 0,
                    hasUpdate = hasUpdate,
                    busy = state == UpdateState.CHECKING,
                    onPillClick = onDismiss,
                    onCheckUpdate = onDismiss,
                    onOpenHistory = onOpenHistory,
                    onCopy = onCopyVersion,
                    showBadge = true
                )

                Spacer(Modifier.height(14.dp))

                // 狀態說明（放喺膠囊下面）
                when (state) {
                    UpdateState.CHECKING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.update_checking),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    UpdateState.AVAILABLE -> {
                        Text(
                            stringResource(R.string.update_available, latestVersion),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.update_new_version, latestVersion),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            stringResource(R.string.update_current_version, currentVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (sizeMb > 0f) {
                            Text(
                                stringResource(R.string.update_size, sizeMb),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    UpdateState.UP_TO_DATE -> {
                        Text(
                            stringResource(R.string.update_latest),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    UpdateState.NO_RELEASE -> {
                        Text(
                            stringResource(R.string.update_no_release),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    UpdateState.ERROR -> {
                        Text(
                            stringResource(R.string.update_failed, errorMessage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    UpdateState.IDLE -> Unit
                }

                // Release notes（有新版本先顯示）
                if (state == UpdateState.AVAILABLE && localizedNotes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.update_notes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = localizedNotes,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                        modifier = Modifier.heightIn(max = 180.dp).verticalScroll(
                            rememberScrollState()
                        )
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 主掣：淺紫膠囊（同第一張圖嗰個掣一致）
                Button(
                    onClick = if (state == UpdateState.AVAILABLE) onDownload else onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        stringResource(
                            if (state == UpdateState.AVAILABLE) R.string.update_download
                            else R.string.update_check
                        )
                    )
                }

                Spacer(Modifier.height(6.dp))

                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.update_close),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 版本歷史 BottomSheet —— 移植自 code-to-app 嘅 `VersionHistorySheet`。
 *
 * 特色：
 *   - 撳一下展開該版本嘅 Release notes（唔係全部一次展開）
 *   - 目前安裝嘅版本會標「已安裝」並用主色
 *   - 每個版本都可以直接撳去下載 APK（有嘅話）
 *   - notes 一樣會按語言自動揀
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistorySheet(
    releases: List<GitHubRelease>,
    loading: Boolean,
    error: String?,
    currentVersion: String,
    onDownload: (GitHubRelease) -> Unit,
    onDismiss: () -> Unit,
    localEntries: List<com.hketa.app.data.Changelog.Resolved> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lang = AppLocale.current()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_status_clock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            when {
                loading && releases.isEmpty() && localEntries.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.history_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                error != null && releases.isEmpty() && localEntries.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.history_failed),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                releases.isEmpty() && localEntries.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(Modifier.fillMaxWidth()) {
                        if (releases.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.history_online),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                )
                            }
                            items(releases, key = { it.tag_name }) { release ->
                                HistoryItem(
                                    release = release,
                                    isCurrent = UpdateChecker.sameVersion(release.tag_name, currentVersion),
                                    lang = lang,
                                    onDownload = { onDownload(release) }
                                )
                            }
                        }
                        if (localEntries.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.history_local),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                )
                            }
                            items(localEntries, key = { "local-" + it.version }) { e ->
                                LocalHistoryItem(entry = e, isCurrent = UpdateChecker.sameVersion(e.version, currentVersion))
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    release: GitHubRelease,
    isCurrent: Boolean,
    lang: String,
    onDownload: () -> Unit
) {
    var expanded by remember(release.tag_name) { mutableStateOf(false) }
    val body = remember(release.body, lang) { UpdateChecker.localizeBody(release.body, lang) }
    val hasNotes = body.isNotBlank()
    val date = release.published_at.substringBefore('T').ifBlank { release.published_at }
    val apk = release.assets.firstOrNull {
        it.browser_download_url.endsWith(".apk", ignoreCase = true)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasNotes) { expanded = !expanded }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = release.tag_name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = stringResource(R.string.history_installed),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (date.isNotBlank()) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (release.name.isNotBlank()) {
                    Text(
                        text = release.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (hasNotes) {
                Text(
                    text = if (expanded) "▾" else "▸",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        }

        if (expanded && hasNotes) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        if (apk != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.update_download))
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}


/** 本地內置 changelog 嘅一項（唔使聯網都睇到） */
@Composable
private fun LocalHistoryItem(
    entry: com.hketa.app.data.Changelog.Resolved,
    isCurrent: Boolean
) {
    var expanded by remember(entry.version) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "v${entry.version}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.history_installed),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = entry.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = if (expanded) "▾" else "▸",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
        }
        if (expanded) {
            entry.items.forEach { item ->
                Row(Modifier.padding(horizontal = 24.dp, vertical = 2.dp)) {
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}
