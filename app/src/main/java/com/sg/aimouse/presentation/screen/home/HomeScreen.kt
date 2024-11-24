@file:OptIn(ExperimentalMaterialApi::class)

package com.sg.aimouse.presentation.screen.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sg.aimouse.R
import com.sg.aimouse.presentation.component.Dialog
import com.sg.aimouse.presentation.screen.home.component.HomeFileItem
import com.sg.aimouse.presentation.screen.home.state.HomeStateHolder
import com.sg.aimouse.service.BluetoothState
import com.sg.aimouse.service.CommandType
import com.sg.aimouse.util.openAppPermissionSetting
import com.sg.aimouse.util.viewModelFactory

@Composable
fun HomeScreen(innerPadding: PaddingValues) {
    val stateHolder = rememberHomeStateHolder()
    val viewModel = stateHolder.viewModel
    val bluetoothState by viewModel.getBluetoothState().collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .pullRefresh(stateHolder.pullRefreshState)
    ) {
        when (bluetoothState) {
            BluetoothState.CONNECTED -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val files = viewModel.getFiles()
                    itemsIndexed(items = files) { index, item ->
                        HomeFileItem(item) { fileName -> stateHolder.showFileRequestDialog(fileName) }

                        if (index < files.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(8.dp),
                                thickness = 1.dp
                            )
                        }
                    }
                }

                PullRefreshIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    refreshing = false,
                    state = stateHolder.pullRefreshState,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }

            BluetoothState.DISCONNECTED -> {
                Button(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .align(Alignment.Center),
                    onClick = stateHolder::connect
                ) {
                    Text("Connect TD Mouse")
                }
            }

            BluetoothState.CONNECTING -> {
                Button(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .align(Alignment.Center),
                    onClick = {},
                    enabled = false
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                }
            }
        }
    }

    if (stateHolder.shouldShowPermissionRequiredDialog) {
        Dialog(
            title = stringResource(R.string.permission_required),
            content = stringResource(R.string.permission_required_desc),
            isCancellable = false,
            onPositiveClickEvent = {
                stateHolder.dismissPermissionRequiredDialog()
                openAppPermissionSetting(stateHolder.context)
            },
            onDismissRequest = stateHolder::dismissPermissionRequiredDialog
        )
        return
    }

    if (stateHolder.shouldShowBluetoothRequiredDialog) {
        Dialog(
            title = stringResource(R.string.bluetooth_disabled),
            content = stringResource(R.string.bluetooth_disabled_desc),
            isCancellable = false,
            onPositiveClickEvent = {
                stateHolder.dismissBluetoothRequiredDialog()
                stateHolder.connect()
            },
            onDismissRequest = stateHolder::dismissBluetoothRequiredDialog
        )
        return
    }

    if (stateHolder.shouldShowBluetoothDeviceUndetectedDialog) {
        Dialog(
            title = stringResource(R.string.bluetooth_device_undetected),
            content = stringResource(R.string.bluetooth_device_undetected_desc),
            isCancellable = false,
            onPositiveClickEvent = {
                stateHolder.dismissBluetoothDeviceUndetectedDialog()
                stateHolder.connect()
            },
            onDismissRequest = stateHolder::dismissBluetoothDeviceUndetectedDialog
        )
        return
    }

    if (stateHolder.shouldShowFileRequestDialog) {
        Dialog(
            title = stringResource(R.string.request_file),
            content = stateHolder.getFileRequestDialogDescription(),
            onPositiveClickEvent = stateHolder::openToshibaTransferJet,
            onNegativeClickEvent = stateHolder::dismissFileRequestDialog,
            onDismissRequest = stateHolder::dismissFileRequestDialog
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun rememberHomeStateHolder(
    context: Context = LocalContext.current,
    viewModel: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(context) }),
    pullRefreshState: PullRefreshState = rememberPullRefreshState(
        refreshing = false,
        onRefresh = { viewModel.sendCommand(CommandType.LIST_FILE) }
    )
): HomeStateHolder {
    return remember { HomeStateHolder(context, viewModel, pullRefreshState) }
}