package com.sg.aimouse.service

import android.content.Context
import android.provider.Settings

interface PermissionService {

    val requiredStoragePermissions: List<String>
    val requiredBluetoothPermissions: List<String>

    fun hasStoragePermission(context: Context): Boolean

    fun hasBluetoothPermission(context: Context): Boolean

    fun requestPermissions(
        context: Context,
        permissions: List<String>,
        permissionsGrantedListener: (() -> Unit)?,
        permissionsDeniedListener: (() -> Unit)?
    )

    fun openAppPermissionSetting(
        context: Context,
        action: String = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    )
}