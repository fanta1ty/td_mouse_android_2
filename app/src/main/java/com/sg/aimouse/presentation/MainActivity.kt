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
import com.sg.aimouse.presentation.screen.bletest.BLETestScreen
import com.sg.aimouse.presentation.screen.bletransfer.BLEFileTransferApp
import com.sg.aimouse.presentation.screen.connect.ConnectionScreen
import com.sg.aimouse.presentation.screen.home.HomeScreen
import com.sg.aimouse.presentation.screen.localfile.LocalFileScreen
import com.sg.aimouse.presentation.ui.theme.AiMouseTheme

@SuppressLint("SourceLockedOrientationActivity")
class MainActivity : ComponentActivity() {
    private val mainViewModel by viewModels<MainViewModel>()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

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
                            startDestination = Screen.TransferScreen.route
                        ) {
                            composable(Screen.ConnectionScreen.route) {
                                ConnectionScreen(navController = navController)
                            }
                            composable(Screen.HomeScreen.route) { HomeScreen(navController = navController) }
                            composable(Screen.LocalFileScreen.route) { LocalFileScreen(navController = navController) }
                            composable(Screen.BLETestScreen.route) { BLETestScreen(navController = navController) }
                            composable(Screen.TransferScreen.route) { BLEFileTransferApp(navController = navController) }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(mainViewModel.viewModelId.first, mainViewModel.viewModelId.second)
    }
}