package com.sg.aimouse.presentation.screen.home.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sg.aimouse.presentation.screen.home.state.HomeStateHolder
import com.sg.aimouse.service.implementation.SMBState

@Composable
fun HomeLifecycleEffect(stateHolder: HomeStateHolder) {
    val lifecycleOwner = stateHolder.lifecycleOwner

    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> stateHolder.requestStoragePermission()
                Lifecycle.Event.ON_STOP -> stateHolder.viewModel.updateSMBState(SMBState.DISCONNECTED)
                else -> Unit
            }
        }

        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}