@file:OptIn(ExperimentalMaterialApi::class)

package com.sg.aimouse.presentation.screen.phone

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sg.aimouse.R
import com.sg.aimouse.presentation.component.Dialog
import com.sg.aimouse.presentation.component.FileItem
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.component.LocalParentViewModel
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import com.sg.aimouse.presentation.screen.phone.state.PhoneStateHolder

@Composable
fun PhoneScreen() {
    val stateHolder = rememberPhoneStateHolder()
    val viewModel = stateHolder.viewModel
    BackHandler(onBack = stateHolder::navigateBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(stateHolder.pullRefreshState)
    ) {
        when (viewModel.shouldShowLocalFileList) {
            true -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(items = viewModel.localFiles) { index, item ->
                        FileItem(item) { file ->
                            if (!file.isDirectory) {
                                stateHolder.showFileSendingConfirmationDialog(file)
                            }
                        }

                        if (index < viewModel.localFiles.lastIndex) {
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

            false -> {
                Button(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .align(Alignment.Center),
                    onClick = stateHolder::getFiles
                ) { Text(stringResource(R.string.get_local_files)) }
            }
        }
    }

    if (stateHolder.shouldShowStoragePermissionRequiredDialog) {
        Dialog(
            title = stringResource(R.string.permission_required),
            content = stringResource(R.string.storage_permission_required_desc),
            isCancellable = false,
            onPositiveClickEvent = stateHolder::navigateToSettings,
            onDismissRequest = stateHolder::dismissStoragePermissionRequiredDialog
        )
        return
    }

    if (stateHolder.shouldShowBluetoothPermissionRequiredDialog) {
        Dialog(
            title = stringResource(R.string.permission_required),
            content = stringResource(R.string.bluetooth_permission_required_desc),
            isCancellable = false,
            onPositiveClickEvent = {
                stateHolder.apply {
                    dismissBluetoothPermissionRequiredDialog()
                    openAppPermissionSetting(activity)
                }
            },
            onDismissRequest = stateHolder::dismissBluetoothPermissionRequiredDialog
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
                stateHolder.getFiles()
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
                stateHolder.getFiles()
            },
            onDismissRequest = stateHolder::dismissBluetoothDeviceUndetectedDialog
        )
        return
    }

    if (stateHolder.shouldShowFileSendingConfirmationDialog) {
        Dialog(
            title = stringResource(R.string.send_file),
            content = stateHolder.getFileSendingConfirmationDialogDescription(),
            onPositiveClickEvent = stateHolder::sendFile,
            onNegativeClickEvent = stateHolder::dismissFileSendingConfirmationDialog,
            onDismissRequest = stateHolder::dismissFileSendingConfirmationDialog
        )
    }
}

@Composable
fun rememberPhoneStateHolder(
    activity: ComponentActivity = LocalActivity.current,
    viewModel: HomeViewModel = LocalParentViewModel.current as HomeViewModel,
    pullRefreshState: PullRefreshState = rememberPullRefreshState(
        refreshing = false,
        onRefresh = { viewModel.retrieveLocalFiles() }
    )
): PhoneStateHolder {
    return remember { PhoneStateHolder(activity, viewModel, pullRefreshState) }
}