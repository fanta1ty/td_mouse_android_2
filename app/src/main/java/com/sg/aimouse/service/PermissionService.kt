package com.sg.aimouse.service

import android.content.Context
import android.provider.Settings

interface PermissionService {

    fun hasStoragePermission(context: Context): Boolean

    fun hasBluetoothPermission(context: Context): Boolean

    fun requestBluetoothPermission(
        context: Context,
        permissionsGrantedListener: (() -> Unit)?,
        permissionsDeniedListener: (() -> Unit)?
    )

    fun requestStoragePermission(
        context: Context,
        permissionsGrantedListener: (() -> Unit)?,
        permissionsDeniedListener: (() -> Unit)?
    )

    fun openAppPermissionSetting(
        context: Context,
        action: String = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    )
}