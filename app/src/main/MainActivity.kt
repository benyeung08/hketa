package com.hketa.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hketa.app.ui.AppRoot
import com.hketa.app.ui.theme.HketaTheme
import com.hketa.app.vm.AppViewModel

class MainActivity : ComponentActivity() {

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
