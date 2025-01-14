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
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.screen.home.HomeScreen
import com.sg.aimouse.presentation.ui.theme.AiMouseTheme

@SuppressLint("SourceLockedOrientationActivity")
class MainActivity : ComponentActivity() {
    private val mainViewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        // Restart activity on process death
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
                ) { CompositionLocalProvider(LocalActivity provides this) { HomeScreen() } }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(mainViewModel.viewModelId.first, mainViewModel.viewModelId.second)
    }
}