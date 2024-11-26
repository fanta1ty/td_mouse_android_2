package com.sg.aimouse.presentation.screen.mouse.state

import android.content.pm.PackageManager
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
import com.sg.aimouse.presentation.screen.mouse.MouseViewModel
import com.sg.aimouse.service.CommandType
import com.sg.aimouse.util.getRequiredPermissions
import com.sg.aimouse.util.hasPermissions
import com.sg.aimouse.util.requestPermissions

@OptIn(ExperimentalMaterialApi::class)
class MouseStateHolder(
    val activity: ComponentActivity,
    val viewModel: MouseViewModel,
    val pullRefreshState: PullRefreshState
) {
    private val requiredPermissions = getRequiredPermissions()
    private var currentSelectedFile = ""

    var shouldShowPermissionRequiredDialog by mutableStateOf(false)
        private set
    var shouldShowBluetoothRequiredDialog by mutableStateOf(false)
        private set
    var shouldShowBluetoothDeviceUndetectedDialog by mutableStateOf(false)
        private set
    var shouldShowFileRequestDialog by mutableStateOf(false)
        private set

    fun connect() {
        if (!hasPermissions(activity, requiredPermissions)) {
            requestPermissions(
                activity,
                requiredPermissions,
                permissionsGrantedListener = { connect() },
                permissionsDeniedListener = { shouldShowPermissionRequiredDialog = true }
            )
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

        viewModel.connect()
    }

    fun openToshibaTransferJet() {
        viewModel.sendCommand(CommandType.DOWNLOAD_FILE, currentSelectedFile)

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

    fun showFileRequestDialog(fileName: String) {
        currentSelectedFile = fileName
        shouldShowFileRequestDialog = true
    }

    fun getFileRequestDialogDescription(): String {
        return String.format(activity.getString(R.string.request_file_desc), currentSelectedFile)
    }

    fun dismissPermissionRequiredDialog() {
        shouldShowPermissionRequiredDialog = false
    }

    fun dismissBluetoothRequiredDialog() {
        shouldShowBluetoothRequiredDialog = false
    }

    fun dismissBluetoothDeviceUndetectedDialog() {
        shouldShowBluetoothDeviceUndetectedDialog = false
    }

    fun dismissFileRequestDialog() {
        shouldShowFileRequestDialog = false
    }

    fun navigateBack() {
        activity.moveTaskToBack(true)
    }
}