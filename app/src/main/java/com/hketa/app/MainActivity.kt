package com.hketa.app

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hketa.app.data.UserPrefs
import com.hketa.app.ui.AppRoot
import com.hketa.app.ui.theme.HketaTheme
import com.hketa.app.vm.AppViewModel

/**
 * 繼承 AppCompatActivity 而唔係 ComponentActivity：
 * AppCompatDelegate 會自動保存／還原用戶揀嘅介面語言。
 */
class MainActivity : AppCompatActivity() {

    private var pendingLocationCallback: ((Boolean) -> Unit)? = null

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingLocationCallback?.invoke(granted)
        pendingLocationCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 外觀設定（深色／字型）初始化一次；StateFlow 令設定頁改完即刻生效
        UserPrefs.init(applicationContext)
        setContent {
            val mode by UserPrefs.darkMode.collectAsState()
            val fontScale by UserPrefs.fontScale.collectAsState()
            HketaTheme(
                darkTheme = when (mode) {
                    UserPrefs.DARK_LIGHT -> false
                    UserPrefs.DARK_NIGHT -> true
                    else -> isSystemInDarkTheme()
                },
                fontScale = fontScale
            ) {
                val vm: AppViewModel = viewModel()
                AppRoot(
                    vm = vm,
                    onNeedLocation = { callback ->
                        pendingLocationCallback = callback
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
            }
        }
    }
}
