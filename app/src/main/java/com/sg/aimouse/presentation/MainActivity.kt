package com.sg.aimouse.presentation

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.navigation.Screen
import com.sg.aimouse.presentation.screen.connect.ConnectionScreen
import com.sg.aimouse.presentation.screen.home.HomeScreen
import com.sg.aimouse.presentation.screen.localfile.LocalFileScreen
import com.sg.aimouse.presentation.ui.theme.AiMouseTheme
import com.sg.aimouse.service.implementation.PermissionServiceImpl

@SuppressLint("SourceLockedOrientationActivity")
class MainActivity : ComponentActivity() {
    private val mainViewModel by viewModels<MainViewModel>()
    private val permissionService = PermissionServiceImpl()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        // Request Bluetooth permissions at app startup
        requestBluetoothPermissions()

        if (savedInstanceState != null) {
            val id = savedInstanceState.getInt(mainViewModel.viewModelId.first)
            if (id != mainViewModel.viewModelId.second) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

        setContent {
            AiMouseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompositionLocalProvider(LocalActivity provides this) {
                        val navController = rememberNavController()
                        NavHost(
                            navController = navController,
                            startDestination = Screen.LocalFileScreen.route
                        ) {
                            composable(Screen.ConnectionScreen.route) {
                                ConnectionScreen(navController = navController)
                            }
                            composable(Screen.HomeScreen.route) { HomeScreen(navController = navController) }
                            composable(Screen.LocalFileScreen.route) { LocalFileScreen(navController = navController) }
                        }
                    }
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        if (!permissionService.hasBluetoothPermission(this)) {
            permissionService.requestPermissions(
                context = this,
                permissions = permissionService.requiredBluetoothPermissions,
                permissionsGrantedListener = {
                    // Permissions granted, can proceed with Bluetooth operations
                },
                permissionsDeniedListener = {
                    // Handle denied permissions - maybe show a dialog explaining why permissions are needed
                }
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(mainViewModel.viewModelId.first, mainViewModel.viewModelId.second)
    }
}