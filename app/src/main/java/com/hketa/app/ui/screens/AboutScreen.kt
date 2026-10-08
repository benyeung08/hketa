package com.hketa.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hketa.app.R
import com.hketa.app.data.UpdateState
import com.hketa.app.ui.Muted
import com.hketa.app.vm.AppViewModel

/**
 * 關於頁面 —— 版面跟 code-to-app 嘅 AboutScreen 一致：
 * 左上角「About」大標題，下面係 app 名，右上角一個齒輪跳去設定。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    vm: AppViewModel,
    onOpenSettings: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    val updateState by vm.updateState.collectAsState()
    val updateInfo by vm.updateInfo.collectAsState()
    val currentVersion by vm.currentVersion.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.about_open_settings)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(88.dp)
            )
            Spacer(Modifier.height(14.dp))

            // App 名（大）
            Text(
                text = stringResource(R.string.about_app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))

            Muted(stringResource(R.string.about_desc))
            Spacer(Modifier.height(6.dp))
            Muted("v$currentVersion")

            Spacer(Modifier.height(24.dp))

            // ---- 版本更新 ----
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.update_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    val statusText = when (updateState) {
                        UpdateState.CHECKING -> stringResource(R.string.update_checking)
                        UpdateState.UP_TO_DATE -> stringResource(R.string.update_latest)
                        UpdateState.AVAILABLE ->
                            stringResource(R.string.update_available, updateInfo?.tag_name.orEmpty())
                        UpdateState.NO_RELEASE -> stringResource(R.string.update_no_release)
                        UpdateState.ERROR -> stringResource(R.string.update_failed, "")
                        UpdateState.IDLE -> stringResource(R.string.update_current, currentVersion)
                    }
                    Muted(statusText)

                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.update_check))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ---- 資料來源 ----
            InfoSection(
                title = stringResource(R.string.about_data_source),
                body = stringResource(R.string.about_data_source_desc)
            )

            InfoSection(
                title = stringResource(R.string.about_privacy),
                body = stringResource(R.string.about_privacy_desc)
            )

            InfoSection(
                title = stringResource(R.string.about_open_source),
                body = stringResource(R.string.about_open_source_desc)
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { openUrl(context, "https://github.com/benyeung08/hketa") }) {
                    Text(stringResource(R.string.about_view_source))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Muted(body)
        Spacer(Modifier.height(14.dp))
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
