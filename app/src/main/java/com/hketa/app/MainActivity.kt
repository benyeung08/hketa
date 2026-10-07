package com.hketa.app

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hketa.app.ui.AppRoot
import com.hketa.app.ui.theme.HketaTheme
import com.hketa.app.vm.AppViewModel

/**
 * 繼承 AppCompatActivity 而唔係 ComponentActivity：
 * AppCompatDelegate 會自動保存／還原用戶揀嘅介面語言
 * （Android 13+ 交畀系統，12 及以下由 AppCompat 自己保存）。
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
        setContent {
            HketaTheme {
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
