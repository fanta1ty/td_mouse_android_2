package com.sg.aimouse.presentation.screen.phone.state

import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sg.aimouse.R
import com.sg.aimouse.model.File
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import com.sg.aimouse.service.PermissionService
import com.sg.aimouse.service.implementation.PermissionServiceImpl

@OptIn(ExperimentalMaterialApi::class)
class PhoneStateHolder(
    val activity: ComponentActivity,
    val viewModel: HomeViewModel,
    val pullRefreshState: PullRefreshState
) : PermissionService by PermissionServiceImpl() {

    private var currentSelectedFile: File? = null

    var shouldShowBluetoothPermissionRequiredDialog by mutableStateOf(false)
        private set
    var shouldShowStoragePermissionRequiredDialog by mutableStateOf(false)
        private set
    var shouldShowBluetoothRequiredDialog by mutableStateOf(false)
        private set
    var shouldShowBluetoothDeviceUndetectedDialog by mutableStateOf(false)
        private set
    var shouldShowFileSendingConfirmationDialog by mutableStateOf(false)
        private set
    var shouldShowLocalFileList by mutableStateOf(false)
        private set

    fun getFiles() {
//        if (!hasBluetoothPermission(activity)) {
//            requestBluetoothPermission(
//                activity,
//                permissionsGrantedListener = { getFiles() },
//                permissionsDeniedListener = { shouldShowBluetoothPermissionRequiredDialog = true }
//            )
//            return
//        }

        if (!hasStoragePermission(activity)) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                requestStoragePermission(
                    activity,
                    permissionsGrantedListener = { getFiles() },
                    permissionsDeniedListener = { shouldShowStoragePermissionRequiredDialog = true }
                )
            } else {
                shouldShowStoragePermissionRequiredDialog = true
            }
            return
        }

//        if (!viewModel.isBluetoothEnabled()) {
//            shouldShowBluetoothRequiredDialog = true
//            return
//        }
//
//        if (!viewModel.isBluetoothDeviceDetected()) {
//            shouldShowBluetoothDeviceUndetectedDialog = true
//            return
//        }

        viewModel.retrieveLocalFiles()
        shouldShowLocalFileList = true
    }

    fun getFileSendingConfirmationDialogDescription(): String {
        return if (currentSelectedFile!!.shouldTransferViaBluetooth()) {
            activity.getString(R.string.request_file_bluetooth_desc)
        } else {
            activity.getString(R.string.request_file_transferjet_desc)
        }
    }

    fun dismissStoragePermissionRequiredDialog() {
        shouldShowStoragePermissionRequiredDialog = false
    }

    fun dismissBluetoothPermissionRequiredDialog() {
        shouldShowBluetoothPermissionRequiredDialog = false
    }

    fun dismissBluetoothRequiredDialog() {
        shouldShowBluetoothRequiredDialog = false
    }

    fun dismissBluetoothDeviceUndetectedDialog() {
        shouldShowBluetoothDeviceUndetectedDialog = false
    }

    fun dismissFileSendingConfirmationDialog() {
        shouldShowFileSendingConfirmationDialog = false
    }

    fun navigateBack() {
        activity.moveTaskToBack(true)
    }

    fun navigateToSettings() {
        dismissStoragePermissionRequiredDialog()

        val action = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        } else {
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        }

        openAppPermissionSetting(activity, action)
    }
}