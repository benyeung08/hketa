package com.hketa.app.ui

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hketa.app.R
import com.hketa.app.vm.AppViewModel

/**
 * 底部狀態欄（編輯器風格）：
 *
 *   v1.0.0-mobilecode  ● 1          [同步] [時鐘] [方框]
 *
 * 左：版本名 + 一個計數徽章（有新版本顯示 1，否則顯示已收藏車站數）
 * 右：① 即時重新整理　② 自動刷新倒數（撳可以開關）　③ 檢查版本更新
 */
@Composable
fun StatusBar(
    vm: AppViewModel,
    onCheckUpdate: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val lastUpdateAt by vm.lastUpdateAt.collectAsState()
    val countdown by vm.refreshCountdown.collectAsState()
    val autoRefresh by vm.autoRefresh.collectAsState()
    val updateState by vm.updateState.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val busy by vm.busy.collectAsState()

    // 有新版本就顯示 1，否則顯示收藏車站數
    val hasUpdate = updateState == com.hketa.app.data.UpdateState.AVAILABLE
    val badge = if (hasUpdate) 1 else favorites.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF121212))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ---- 左：版本 + 徽章 ----
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { if (hasUpdate) onCheckUpdate() else onOpenFavorites() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = versionLabel(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
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

        // ---- 右：三個掣 ----
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            StatusIcon(
                res = R.drawable.ic_status_sync,
                desc = stringResource(R.string.status_refresh),
                enabled = !busy,
                onClick = { vm.refreshNow() }
            )
            StatusIcon(
                res = R.drawable.ic_status_clock,
                desc = stringResource(R.string.status_version_history),
                enabled = true,
                onClick = onOpenHistory
            )
            StatusIcon(
                res = R.drawable.ic_status_layers,
                desc = stringResource(R.string.status_check_update),
                enabled = true,
                onClick = onCheckUpdate
            )
        }

        // 自動刷新喺背景跑（倒數唔再顯示喺狀態欄），
        // lastUpdateAt 用嚟觸發重組、令圖示喺忙碌時正確變灰
        if (lastUpdateAt > 0L && busy) Unit
    }
}

@Composable
private fun StatusIcon(
    res: Int,
    desc: String,
    enabled: Boolean,
    badgeText: String? = null,
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
        if (badgeText != null) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = badgeText,
                color = Color(0xFF8A8A8A),
                fontSize = 11.sp
            )
        }
    }
}

/** 版本標籤：1.0.0 → v1.0.0-mobilecode */
@Composable
private fun versionLabel(): String {
    val base = runCatching {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        p.versionName ?: "1.0.0"
    }.getOrDefault("1.0.0")
    return "v$base-mobilecode"
}
