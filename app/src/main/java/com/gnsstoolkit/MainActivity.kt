package com.gnsstoolkit

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gnsstoolkit.data.AppLanguage
import com.gnsstoolkit.ui.GnssToolkitApp

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // 应用内语言覆盖：设置里选了语言就用它，选“跟随系统”时原样返回。
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { result ->
                val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                viewModel.onPermissionResult(fine || coarse)
            }

            LaunchedEffect(Unit) { viewModel.refreshPermission() }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }

            GnssToolkitApp(
                viewModel = viewModel,
                permissionPrompt = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
            )
        }
    }
}
