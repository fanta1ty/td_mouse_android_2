package com.sg.aimouse.presentation.screen.home.state

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sg.aimouse.R
import com.sg.aimouse.common.AiMouseSingleton
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import com.sg.aimouse.service.CommandType
import com.sg.aimouse.util.getRequiredPermissions
import com.sg.aimouse.util.hasPermissions
import com.sg.aimouse.util.requestPermissions

@OptIn(ExperimentalMaterialApi::class)
class HomeStateHolder(
    val context: Context,
    val viewModel: HomeViewModel,
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
        if (!hasPermissions(context, requiredPermissions)) {
            requestPermissions(
                context,
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

        val packageName = context.getString(R.string.toshibar_transferjet_package_name)
        val isAppInstalled = try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(AiMouseSingleton.DEBUG_TAG, "", e)
            Toast.makeText(
                context,
                context.getString(R.string.transferjet_not_installed),
                Toast.LENGTH_SHORT
            ).show()

            false
        }

        if (isAppInstalled) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.let {
                dismissFileRequestDialog()
                context.startActivity(it)
            }
        }
    }

    fun showFileRequestDialog(fileName: String) {
        currentSelectedFile = fileName
        shouldShowFileRequestDialog = true
    }

    fun getFileRequestDialogDescription(): String {
        return String.format(context.getString(R.string.request_file_desc), currentSelectedFile)
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
}