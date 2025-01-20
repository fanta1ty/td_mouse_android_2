package com.sg.aimouse.presentation.screen.home.state

import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
import com.sg.aimouse.R
import com.sg.aimouse.presentation.navigation.Screen
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import com.sg.aimouse.service.PermissionService
import com.sg.aimouse.service.implementation.PermissionServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
class HomeStateHolder(
    val activity: ComponentActivity,
    val lifecycleOwner: LifecycleOwner,
    val viewModel: HomeViewModel,
    val navController: NavHostController,
    val coroutineScope: CoroutineScope,
    val drawerState: DrawerState
) : PermissionService by PermissionServiceImpl() {

    var selectedDrawerIndex by mutableIntStateOf(0)
        private set
    var shouldShowStoragePermissionRequiredDialog by mutableStateOf(false)
        private set

    val drawerItems = listOf<DrawerItem>(
        DrawerItem(
            Screen.MouseScreen.route,
            activity.getString(R.string.files_on_tdmouse),
            R.drawable.ic_mouse
        ),
        DrawerItem(
            Screen.PhoneScreen.route,
            activity.getString(R.string.files_on_local),
            R.drawable.ic_phone
        )
    )

    fun updateSelectedDrawerIndex(index: Int) {
        selectedDrawerIndex = index
    }

    fun onDrawerItemClick(index: Int) {
        if (selectedDrawerIndex != index) {
            navController.navigate(drawerItems[index].destination) {
                popUpTo(
                    drawerItems[selectedDrawerIndex].destination
                ) { inclusive = true }
            }
            updateSelectedDrawerIndex(index)
        }
        coroutineScope.launch { drawerState.close() }
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