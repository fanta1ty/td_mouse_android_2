package com.sg.aimouse.presentation.screen.mouse.state

import android.content.pm.PackageManager
import android.os.Build
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
import com.sg.aimouse.service.BluetoothResponseType
import com.sg.aimouse.service.CommandType
import com.sg.aimouse.service.PermissionService
import com.sg.aimouse.service.implementation.PermissionServiceImpl

@OptIn(ExperimentalMaterialApi::class)
class MouseStateHolder(
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
    var shouldShowFileRequestDialog by mutableStateOf(false)
        private set

    fun connect() {
        if (!hasBluetoothPermission(activity)) {
            requestPermissions(
                activity,
                requiredBluetoothPermissions,
                permissionsGrantedListener = { connect() },
                permissionsDeniedListener = { shouldShowBluetoothPermissionRequiredDialog = true }
            )
            return
        }

        if (!hasStoragePermission(activity)) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                requestPermissions(
                    activity,
                    requiredStoragePermissions,
                    permissionsGrantedListener = { connect() },
                    permissionsDeniedListener = { shouldShowStoragePermissionRequiredDialog = true }
                )
            } else {
                shouldShowStoragePermissionRequiredDialog = true
            }
            return
        }

        if (!viewModel.isBluetoothEnabled()) {
            shouldShowBluetoothRequiredDialog = true
            return
        }

        if (!viewModel.isBluetoothDeviceDetected()) {
            shouldShowBluetoothDeviceUndetectedDialog = true
            return
        }

        viewModel.connectBluetooth()
    }

    fun transferFile() {
        dismissFileRequestDialog()
        if (currentSelectedFile!!.shouldTransferViaBluetooth()) {
            viewModel.sendBluetoothCommand(
                CommandType.RECEIVE_FILE_BLUETOOTH,
                currentSelectedFile,
                BluetoothResponseType.FILE
            )
        } else {
            viewModel.sendBluetoothCommand(
                CommandType.RECEIVE_FILE_TRANSFERJET,
                currentSelectedFile
            )
            openToshibaTransferJet()
        }
    }

    fun onFileItemClick(file: File) {
        currentSelectedFile = file
        viewModel.downloadFile(file.fileName)
    }

    fun dismissFileRequestDialog() {
        shouldShowFileRequestDialog = false
    }

    fun navigateBack() {
        activity.moveTaskToBack(true)
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
            launchIntent?.let {
                dismissFileRequestDialog()
                activity.startActivity(it)
            }
        }
    }
}