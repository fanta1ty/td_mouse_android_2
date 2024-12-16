package com.sg.aimouse.presentation.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sg.aimouse.model.Request
import com.sg.aimouse.service.BluetoothService
import com.sg.aimouse.service.BluetoothState
import com.sg.aimouse.service.CommandType
import com.sg.aimouse.service.FileService
import com.sg.aimouse.service.implementation.FileServiceImpl
import kotlinx.coroutines.launch

class HomeViewModel(context: Context) : ViewModel(), FileService by FileServiceImpl() {
    private val bluetoothService = BluetoothService(context)

    init {
        viewModelScope.launch {
            bluetoothService.getBluetoothState().collect { state ->
                when (state) {
                    BluetoothState.CONNECTED -> sendCommand(CommandType.LIST_FILE)
                    BluetoothState.DISCONNECTED -> bluetoothService.close()
                    BluetoothState.CONNECTING -> Unit
                }
            }
        }
    }

    fun connect() {
        bluetoothService.connect()
    }

    fun sendCommand(commandType: CommandType, fileName: String = "") {
        val cmd = when (commandType) {
            CommandType.LIST_FILE -> Request("filelist").toJSON()
            CommandType.RECEIVE_FILE_BLUETOOTH -> Request("send_BTfile", fileName).toJSON()
            CommandType.RECEIVE_FILE_TRANSFERJET -> Request("sendfile", fileName).toJSON()
        }

        bluetoothService.sendCommand(cmd)
    }

    fun getTDMouseFiles() = bluetoothService.getFiles()

    fun getBluetoothState() = bluetoothService.getBluetoothState()

    fun isBluetoothEnabled() = bluetoothService.isBluetoothEnable()

    fun isBluetoothDeviceDetected() = bluetoothService.isBluetoothDeviceDetected()

    override fun onCleared() {
        bluetoothService.close(isRelease = true)
        super.onCleared()
    }
}