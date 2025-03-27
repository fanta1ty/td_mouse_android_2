@file:OptIn(ExperimentalMaterial3Api::class)

package com.sg.aimouse.presentation.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sg.aimouse.R
import com.sg.aimouse.model.File
import com.sg.aimouse.presentation.component.Dialog
import com.sg.aimouse.presentation.component.FileItem
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.screen.connect.ConnectionViewModel
import com.sg.aimouse.presentation.screen.home.state.HomeStateHolder
import com.sg.aimouse.service.implementation.SMBState
import com.sg.aimouse.util.viewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val activity = LocalActivity.current
    val connectionViewModel: ConnectionViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = viewModelFactory { ConnectionViewModel(activity) }
    )
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { HomeViewModel(activity, connectionViewModel.getSambaService()) }
    )
    val lifecycleOwner = LocalLifecycleOwner.current

    val stateHolder = remember { HomeStateHolder(activity, lifecycleOwner, viewModel) }

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

    Scaffold(
    ) { innerPaddings ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPaddings)
                .background(color = colorResource(R.color.light_gray_DD))
        ) {
            // TDMouse Drive Explorer (Server files)
            Column(
                modifier = Modifier
                    .weight(1f) // 50% height
                    .fillMaxWidth()
                    .background(Color.White, shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .padding(top = 5.dp, start = 5.dp, end = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = colorResource(R.color.light_gray_ED), shape = RoundedCornerShape(14.dp))
                ){
                    Text(
                        text = stringResource(R.string.title_tdmouse_drive),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp,top = 3.dp, bottom = 3.dp)
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()
                    .padding(start = 8.dp)
                ) {
                    items(viewModel.remoteFiles) { file ->
                        FileItem(file) { /* Handle click */ }
                    }
                }
            }

            // Add a horizontal space
            Spacer(modifier = Modifier.height(8.dp))

            // Phone Drive Explorer (Local files)
            Column(
                modifier = Modifier
                    .weight(1f) // 50% height
                    .fillMaxWidth()
                    .background(Color.White, shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(top = 5.dp, start = 5.dp, end = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = colorResource(R.color.light_gray_ED), shape = RoundedCornerShape(14.dp))
                ) {
                    Text(
                        text = stringResource(R.string.title_phone_drive),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp,top = 3.dp, bottom = 3.dp)
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()
                    .padding(start = 8.dp)
                ) {
                    items(viewModel.localFiles) { file ->
                        FileItem(file) { /* Handle click */ }
                    }
                }
            }
        }
    }

    if (stateHolder.shouldShowStoragePermissionRequiredDialog) {
        Dialog(
            title = stringResource(R.string.permission_required),
            content = stringResource(R.string.storage_permission_required_desc),
            onPositiveClickEvent = stateHolder::navigateToAppPermissionSettings
        )
    }
}