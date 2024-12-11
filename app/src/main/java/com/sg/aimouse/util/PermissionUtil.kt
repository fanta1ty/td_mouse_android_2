package com.sg.aimouse.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener

fun getRequiredBluetoothPermissions(): List<String> {
    val permissions = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    return permissions
}

fun getRequiredStoragePermissions(): List<String> {
    val permissions = mutableListOf<String>()

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    return permissions
}

fun hasPermissions(context: Context, permissions: List<String>): Boolean {
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

fun requestPermissions(
    context: Context,
    permissions: List<String>,
    permissionsGrantedListener: (() -> Unit)? = null,
    permissionsDeniedListener: (() -> Unit)? = null
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

fun openAppPermissionSetting(
    context: Context,
    action: String = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
) {
    val intent = Intent(action, Uri.parse("package:${context.packageName}"))
    intent.addCategory(Intent.CATEGORY_DEFAULT)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}