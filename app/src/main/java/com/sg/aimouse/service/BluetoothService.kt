package com.sg.aimouse.service
/*
import com.sg.aimouse.model.File
import kotlinx.coroutines.flow.StateFlow

enum class BluetoothState {
    CONNECTING, CONNECTED, DISCONNECTED
}

enum class CommandType {
    LIST_FILE, SEND_FILE_TRANSFERJET, RECEIVE_FILE_TRANSFERJET, RECEIVE_FILE_BLUETOOTH
}

enum class BluetoothResponseType {
    FILE, JSON
}

interface BluetoothService {
    val bluetoothState: StateFlow<BluetoothState>
    val tdMouseFiles: List<File>
    val isTransferringFile: Boolean

    fun isBluetoothEnabled(): Boolean

    fun isBluetoothDeviceDetected(): Boolean

    fun connectBluetooth()

    fun closeBluetoothConnection(isRelease: Boolean = false)

    fun sendBluetoothCommand(
        commandType: CommandType,
        file: File? = null,
        responseType: BluetoothResponseType = BluetoothResponseType.JSON
    )

    fun sendFileViaBluetooth(file: File)
}
 */