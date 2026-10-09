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
import androidx.compose.ui.unit.Dp
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
    val busy by vm.busy.collectAsState()

    val (versionName, versionCode) = versionInfo()

    // 有新版本 → versionCode 數字轉紅；冇新版維持灰
    // （唔再顯示圓點徽章 —— 版面跟 code-to-app 原版 VersionPill 一致）
    val hasUpdate = updateState == UpdateState.AVAILABLE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF121212))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VersionPill(
            versionName = versionName,
            versionCode = versionCode,
            hasUpdate = hasUpdate,
            busy = busy,
            onPillClick = { if (hasUpdate) onCheckUpdate() else onOpenFavorites() },
            onCheckUpdate = onCheckUpdate,
            onOpenHistory = onOpenHistory,
            onCopy = {
                copyToClipboard(context, "HKATE version", "HKATE v$versionName ($versionCode)")
                Toast.makeText(
                    context,
                    context.getString(R.string.status_version_copied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

/**
 * VersionPill —— 第二張圖嗰個「版本膠囊」。
 *
 *   v1.0.0-mobilecode · 1        ⟳   ◷   ▤
 *
 * 抽咗出嚟做共用元件，所以：
 *   - 底部狀態欄用佢
 *   - 「版本更新」對話框都用佢（全面改為呢個樣式）
 */
@Composable
fun VersionPill(
    versionName: String,
    versionCode: Long,
    hasUpdate: Boolean,
    busy: Boolean,
    onPillClick: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenHistory: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 版面數值全部跟足 code-to-app 原版 VersionPill：
    //   膠囊 padding  = 14 / 6 dp
    //   分隔符前後    = 6 / 6 dp
    //   版本→掣       = 8 dp
    //   掣與掣之間    = 10 dp
    //   icon 尺寸     = Sync 14 / History 15 / Copy 14
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f))
            .clickable(onClick = onPillClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ---- 左：v版本名 · versionCode ----
        //   原版冇圓點徽章；有新版本時 versionCode 數字轉紅（HKATE 增強）
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

        // ---- 右：三個掣（對應原版 Sync / History / ContentCopy）----
        // ① 檢查更新
        StatusIcon(
            res = R.drawable.ic_status_sync,
            desc = stringResource(R.string.status_check_update),
            enabled = !busy,
            size = 14.dp,
            tint = MaterialTheme.colorScheme.primary,
            onClick = onCheckUpdate
        )
        Spacer(Modifier.width(10.dp))
        // ② 版本歷史
        StatusIcon(
            res = R.drawable.ic_status_clock,
            desc = stringResource(R.string.status_version_history),
            enabled = true,
            size = 15.dp,
            tint = MaterialTheme.colorScheme.primary,
            onClick = onOpenHistory
        )
        Spacer(Modifier.width(10.dp))
        // ③ 複製版本號
        StatusIcon(
            res = R.drawable.ic_status_copy,
            desc = stringResource(R.string.status_copy_version),
            enabled = true,
            size = 14.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onCopy
        )
    }
}


@Composable
fun StatusIcon(
    res: Int,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit,
    /** 原版 VersionPill 三個 icon 尺寸唔同：Sync 14 / History 15 / Copy 14 */
    size: Dp = 16.dp,
    tint: Color = Color.Unspecified,
    /** 原版掣周圍冇 padding，撳擊區就係 icon 本身；畀 0 就跟足原版 */
    pad: Dp = 6.dp
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = pad, vertical = if (pad > 0.dp) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(res),
            contentDescription = desc,
            tint = if (tint != Color.Unspecified) tint
                   else if (enabled) Color.White else Color(0xFF6A6A6A),
            modifier = Modifier.size(size)
        )
    }
}

/**
 * 版本名 + versionCode（versionCode 對應原版 VersionPill 嗰個數字）。
 * 公開係因為 About 頁面嘅 VersionPill 都要用。
 */
@Composable
fun versionInfo(): Pair<String, Long> {
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

fun copyToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
