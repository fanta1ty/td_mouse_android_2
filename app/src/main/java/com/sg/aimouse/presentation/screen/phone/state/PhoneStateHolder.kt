package com.sg.aimouse.presentation.screen.phone.state

import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sg.aimouse.R
import com.sg.aimouse.common.AiMouseSingleton
import com.sg.aimouse.model.File
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import com.sg.aimouse.service.BluetoothState
import com.sg.aimouse.service.CommandType
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

    fun getFiles() {
        if (!hasStoragePermission(activity)) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                requestPermissions(
                    activity,
                    requiredStoragePermissions,
                    permissionsGrantedListener = { getFiles() },
                    permissionsDeniedListener = { shouldShowStoragePermissionRequiredDialog = true }
                )
            } else {
                shouldShowStoragePermissionRequiredDialog = true
            }
            return
        }

        viewModel.retrieveLocalFiles()
        viewModel.updateShouldShowLocalFileList(true)
    }

    fun sendFile() {
        dismissFileSendingConfirmationDialog()

        currentSelectedFile?.let { file ->
            if (!file.shouldTransferViaBluetooth()) {
                viewModel.sendBluetoothCommand(CommandType.SEND_FILE_TRANSFERJET)
                openToshibaTransferJet()
            } else {
                if (viewModel.bluetoothState.value == BluetoothState.CONNECTED) {
                    viewModel.sendFileViaBluetooth(file)
                } else {
                    Toast.makeText(
                        activity,
                        "Please connect TD Mouse in Mouse Screen before send file via Bluetooth",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun onFileItemClick(file: File) {
        currentSelectedFile = file
        if (!file.shouldTransferViaBluetooth()) {
            shouldShowFileSendingConfirmationDialog = true
        } else {
            sendFile()
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

    private fun openToshibaTransferJet() {
        val packageName = activity.getString(R.string.toshibar_transferjet_package_name)
        val isAppInstalled = try {
            activity.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(AiMouseSingleton.DEBUG_TAG, "", e)
            Toast.makeText(
                activity,
                activity.getString(R.string.transferjet_not_installed),
                Toast.LENGTH_SHORT
            ).show()

            false
        }

        if (isAppInstalled) {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.let { activity.startActivity(it) }
        }
    }
}