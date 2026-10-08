package com.hketa.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hketa.app.R
import com.hketa.app.data.Changelog
import com.hketa.app.util.AppLocale
import com.hketa.app.vm.AppViewModel

/**
 * 版本歷史對話框。
 *
 * 顯示兩部分：
 *  ① 本地內置 changelog（離線一定睇到）
 *  ② GitHub Releases（拉到就顯示，標明「線上」）
 */
@Composable
fun VersionHistoryDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit
) {
    val loading by vm.historyLoading.collectAsState()
    val online by vm.historyReleases.collectAsState()
    val current by vm.currentVersion.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = 560.dp),
        title = {
            Column {
                Text(
                    stringResource(R.string.history_title),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.history_current, current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }

                // ---- 線上（GitHub Releases）----
                if (online.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.history_online))
                    online.forEach { r ->
                        EntryCard(Changelog.Resolved(r.tag_name.removePrefix("v"), r.published_at.take(10), r.name.ifBlank { r.tag_name }, releaseItems(r)), isCurrent = sameVersion(r.tag_name, current))
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ---- 本地內置 ----
                SectionLabel(stringResource(R.string.history_local))
                val lang = AppLocale.current()
                Changelog.forLang(lang).forEach { e ->
                    EntryCard(e, isCurrent = sameVersion(e.version, current))
                }

                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_close)) }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun EntryCard(e: Changelog.Resolved, isCurrent: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrent)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "v${e.version}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (e.date.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = e.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

            if (e.title.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = e.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (e.items.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                e.items.forEach { item ->
                    Row(Modifier.padding(vertical = 2.dp)) {
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
        }
    }
}

/** GitHub Release body → 要點列表 */
private fun releaseItems(r: com.hketa.app.data.GitHubRelease): List<String> =
    r.body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.removePrefix("- ").removePrefix("* ").trim() }
        .filter { it.isNotBlank() }
        .take(12)
        .toList()
        .ifEmpty { listOf(r.body.take(200).ifBlank { "—" }) }

/** 版本號比較：忽略 v 前綴同長度差異（1.0 vs 1.0.0） */
private fun sameVersion(a: String, b: String): Boolean {
    fun parts(s: String) = s.trim()
        .removePrefix("v").removePrefix("V")
        .split(".")
        .mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
    val x = parts(a); val y = parts(b)
    if (x.isEmpty() || y.isEmpty()) return false
    val n = maxOf(x.size, y.size)
    for (i in 0 until n) {
        if (x.getOrElse(i) { 0 } != y.getOrElse(i) { 0 }) return false
    }
    return true
}
