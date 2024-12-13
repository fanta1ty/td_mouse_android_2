package com.sg.aimouse.presentation.screen.phone.state

import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sg.aimouse.presentation.screen.phone.PhoneViewModel
import com.sg.aimouse.service.PermissionService
import com.sg.aimouse.service.implementation.PermissionServiceImpl

class PhoneStateHolder(
    val activity: ComponentActivity,
    val viewModel: PhoneViewModel
) : PermissionService by PermissionServiceImpl() {
//    private val requiredStoragePermissions = getRequiredStoragePermission()
//    private val requiredBluetoothPermissions = getRequiredBluetoothPermission()

    var isStoragePermissionGranted by mutableStateOf(false)
        private set
    var shouldShowPermissionRequiredDialog by mutableStateOf(false)
        private set

    fun getFiles() {
//        if (!hasStoragePermission()) {
//            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
//                requestPermissions(
//                    activity,
//                    requiredPermissions,
//                    permissionsGrantedListener = { getFiles() },
//                    permissionsDeniedListener = { shouldShowPermissionRequiredDialog = true }
//                )
//            } else {
//                shouldShowPermissionRequiredDialog = true
//            }
//
//            return
//        }

        isStoragePermissionGranted = true
    }

    fun navigateBack() {
        activity.moveTaskToBack(true)
    }

    fun navigateToSettings() {
        dismissPermissionRequiredDialog()

        val action = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        } else {
            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
        }

        openAppPermissionSetting(activity, action)
    }

    fun dismissPermissionRequiredDialog() {
        shouldShowPermissionRequiredDialog = false
    }
}