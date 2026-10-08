package com.hketa.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hketa.app.R
import com.hketa.app.data.UpdateState
import com.hketa.app.vm.AppViewModel

/**
 * 底部狀態欄 —— 移植自 code-to-app 嘅 `VersionPill`
 * （`com.webtoapp.ui.screens.AboutScreen` 第 386–457 行）。
 *
 *   v1.0.0-mobilecode · 1        ⟳   ◷   ▤
 *   └── 版本名 · versionCode ──┘   │    │    └─ 複製版本號
 *                                  │    └─ 版本歷史
 *                                  └─ 檢查更新（撳成個膠囊都得）
 *
 * 同原版嘅對應：
 *   原版 `Icons.Outlined.Sync`        → 檢查更新（成個膠囊 onClick）
 *   原版 `Icons.Outlined.History`     → 版本歷史
 *   原版 `Icons.Outlined.ContentCopy` → 複製版本號去剪貼簿
 */
@Composable
fun StatusBar(
    vm: AppViewModel,
    onCheckUpdate: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val updateState by vm.updateState.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val busy by vm.busy.collectAsState()

    val (versionName, versionCode) = versionInfo()

    // 有新版本就顯示紅色徽章 + 1，否則灰點 + 收藏車站數
    val hasUpdate = updateState == UpdateState.AVAILABLE
    val badge = if (hasUpdate) 1 else favorites.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF121212))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ---- 左：v版本名 · versionCode · 徽章 ----
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { if (hasUpdate) onCheckUpdate() else onOpenFavorites() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "v$versionName",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            // 原版係 "·" 分隔符
            Text(
                text = "·",
                color = Color(0xFF8A8A8A),
                fontSize = 12.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = versionCode.toString(),
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (hasUpdate) Color(0xFFFF5252) else Color(0xFF8A8A8A))
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = badge.toString(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.weight(1f))

        // ---- 右：三個掣（對應原版 Sync / History / ContentCopy）----
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // ① 檢查更新
            StatusIcon(
                res = R.drawable.ic_status_sync,
                desc = stringResource(R.string.status_check_update),
                enabled = !busy,
                onClick = onCheckUpdate
            )
            // ② 版本歷史
            StatusIcon(
                res = R.drawable.ic_status_clock,
                desc = stringResource(R.string.status_version_history),
                enabled = true,
                onClick = onOpenHistory
            )
            // ③ 複製版本號（原版係 ContentCopy）
            StatusIcon(
                res = R.drawable.ic_status_copy,
                desc = stringResource(R.string.status_copy_version),
                enabled = true,
                onClick = {
                    copyToClipboard(
                        context,
                        "HKETA version",
                        "HKETA v$versionName ($versionCode)"
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.status_version_copied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }
}

@Composable
private fun StatusIcon(
    res: Int,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(res),
            contentDescription = desc,
            tint = if (enabled) Color.White else Color(0xFF6A6A6A),
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 版本名 + versionCode（versionCode 對應原版 VersionPill 嗰個數字） */
@Composable
private fun versionInfo(): Pair<String, Long> {
    val ctx = LocalContext.current
    return runCatching {
        @Suppress("DEPRECATION")
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val name = p.versionName ?: "1.0.0"
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.longVersionCode
        } else {
            p.versionCode.toLong()
        }
        name to code
    }.getOrDefault("1.0.0" to 1L)
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
