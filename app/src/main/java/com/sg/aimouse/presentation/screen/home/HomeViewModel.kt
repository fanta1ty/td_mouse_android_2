package com.sg.aimouse.presentation.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sg.aimouse.model.Request
import com.sg.aimouse.service.BluetoothService
import com.sg.aimouse.service.BluetoothState
import com.sg.aimouse.service.CommandType
import kotlinx.coroutines.launch

class HomeViewModel(context: Context) : ViewModel() {
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
            CommandType.DOWNLOAD_FILE -> Request("sendfile", fileName).toJSON()
        }

        bluetoothService.sendCommand(cmd)
    }

    fun getFiles() = bluetoothService.getFiles()

    fun getBluetoothState() = bluetoothService.getBluetoothState()

    fun isBluetoothEnabled() = bluetoothService.isBluetoothEnable()

    fun isBluetoothDeviceDetected() = bluetoothService.isBluetoothDeviceDetected()

    override fun onCleared() {
        bluetoothService.close(isRelease = true)
        super.onCleared()
    }
}