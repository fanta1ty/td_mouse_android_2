package com.sg.aimouse.service

import com.sg.aimouse.model.File
import kotlinx.coroutines.flow.StateFlow

enum class BluetoothState {
    CONNECTING, CONNECTED, DISCONNECTED
}

enum class CommandType {
    LIST_FILE, RECEIVE_FILE_TRANSFERJET, RECEIVE_FILE_BLUETOOTH
}

interface BluetoothService {
    val bluetoothState: StateFlow<BluetoothState>
    val tdMouseFiles: List<File>

    fun isBluetoothEnabled(): Boolean

    fun isBluetoothDeviceDetected(): Boolean

    fun connectBluetooth()

    fun closeBluetoothConnection(isRelease: Boolean = false)

    fun sendBluetoothCommand(commandType: CommandType, fileName: String = "")
}