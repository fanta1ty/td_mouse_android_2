package com.sg.aimouse.presentation.screen.home.state

import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import com.sg.aimouse.model.File
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import com.sg.aimouse.service.PermissionService
import com.sg.aimouse.service.implementation.PermissionServiceImpl

@OptIn(ExperimentalMaterialApi::class)
class HomeStateHolder(
    val activity: ComponentActivity,
    val lifecycleOwner: LifecycleOwner,
    val viewModel: HomeViewModel,
    val pagerState: PagerState,
    val pullRefreshState: PullRefreshState,
) : PermissionService by PermissionServiceImpl() {

    var shouldShowStoragePermissionRequiredDialog by mutableStateOf(false)
        private set

    fun onMouseFileItemClick(file: File) {
        file.isSelected.value = !file.isSelected.value
    }

    fun onPhoneFileItemClick(file: File) {
        file.isSelected.value = !file.isSelected.value
    }

    fun requestStoragePermission() {
        if (!hasStoragePermission(activity)) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                requestPermissions(
                    activity,
                    requiredStoragePermissions,
                    permissionsGrantedListener = {
                        viewModel.connectSMB()
                        viewModel.retrieveLocalFiles()
                    },
                    permissionsDeniedListener = { shouldShowStoragePermissionRequiredDialog = true }
                )
            } else {
                shouldShowStoragePermissionRequiredDialog = true
            }
        } else {
            dismissStoragePermissionRequiredDialog()
            viewModel.connectSMB()
            viewModel.retrieveLocalFiles()
        }
    }

    fun navigateToAppPermissionSettings() {
        dismissStoragePermissionRequiredDialog()

        val action = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        } else {
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        }

        openAppPermissionSetting(activity, action)
    }

    private fun dismissStoragePermissionRequiredDialog() {
        if (shouldShowStoragePermissionRequiredDialog) {
            shouldShowStoragePermissionRequiredDialog = false
        }
    }
}