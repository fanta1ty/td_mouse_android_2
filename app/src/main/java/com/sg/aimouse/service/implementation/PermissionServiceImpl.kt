package com.sg.aimouse.service.implementation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.sg.aimouse.service.PermissionService

class PermissionServiceImpl() : PermissionService {

    private val requiredStoragePermissions = getRequiredStoragePermission()
    private val requiredBluetoothPermissions = getRequiredBluetoothPermission()

    override fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            hasPermissions(context, requiredStoragePermissions)
        } else {
            Environment.isExternalStorageManager()
        }
    }

    override fun hasBluetoothPermission(context: Context): Boolean {
        return if (requiredBluetoothPermissions.isEmpty()) {
            true
        } else {
            hasPermissions(context, requiredBluetoothPermissions)
        }
    }

    override fun requestStoragePermission(
        context: Context,
        permissionsGrantedListener: (() -> Unit)?,
        permissionsDeniedListener: (() -> Unit)?
    ) {
        requestPermissions(
            context,
            requiredStoragePermissions,
            permissionsGrantedListener,
            permissionsDeniedListener
        )
    }

    override fun requestBluetoothPermission(
        context: Context,
        permissionsGrantedListener: (() -> Unit)?,
        permissionsDeniedListener: (() -> Unit)?
    ) {
        requestPermissions(
            context,
            requiredBluetoothPermissions,
            permissionsGrantedListener,
            permissionsDeniedListener
        )
    }

    override fun openAppPermissionSetting(context: Context, action: String) {
        val intent = Intent(action, Uri.parse("package:${context.packageName}"))
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun getRequiredStoragePermission(): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        return permissions
    }

    private fun getRequiredBluetoothPermission(): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        return permissions
    }

    private fun hasPermissions(
        context: Context,
        permissions: List<String>
    ): Boolean {
        var isGranted = true

        run breaking@{
            permissions.forEach { permission ->
                if (ContextCompat.checkSelfPermission(
                        context,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    isGranted = false
                    return@breaking
                }
            }
        }

        return isGranted
    }

    private fun requestPermissions(
        context: Context,
        permissions: List<String>,
        permissionsGrantedListener: (() -> Unit)?,
        permissionsDeniedListener: (() -> Unit)?
    ) {
        Dexter.withContext(context)
            .withPermissions(permissions)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report == null) return

                    if (report.areAllPermissionsGranted()) {
                        permissionsGrantedListener?.invoke()
                    } else {
                        permissionsDeniedListener?.invoke()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    requestList: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                }
            }).check()
    }
}