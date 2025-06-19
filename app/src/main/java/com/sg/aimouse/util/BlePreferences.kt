package com.sg.aimouse.util

import android.content.Context
import android.content.SharedPreferences
import com.sg.aimouse.service.BluetoothDevice

object BlePreferences {
    private const val PREFS_NAME = "ble_prefs"
    private const val KEY_DEVICE_ADDRESS = "device_address"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_IS_TD_DEVICE = "is_td_device"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveDevice(context: Context, device: BluetoothDevice) {
        getPreferences(context).edit().apply {
            putString(KEY_DEVICE_ADDRESS, device.address)
            putString(KEY_DEVICE_NAME, device.name)
            putBoolean(KEY_IS_TD_DEVICE, device.isTDDevice)
            apply()
        }
    }

    fun getSavedDevice(context: Context): BluetoothDevice? {
        val prefs = getPreferences(context)
        val address = prefs.getString(KEY_DEVICE_ADDRESS, null)
        val name = prefs.getString(KEY_DEVICE_NAME, null)
        val isTDDevice = prefs.getBoolean(KEY_IS_TD_DEVICE, false)
        return if (address != null) {
            BluetoothDevice(name, address, isTDDevice)
        } else {
            null
        }
    }

    fun clearSavedDevice(context: Context) {
        getPreferences(context).edit().clear().apply()
    }
}
