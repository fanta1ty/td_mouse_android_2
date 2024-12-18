package com.sg.aimouse.presentation.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sg.aimouse.service.BluetoothService
import com.sg.aimouse.service.BluetoothState
import com.sg.aimouse.service.CommandType
import com.sg.aimouse.service.FileService
import com.sg.aimouse.service.implementation.BluetoothServiceImpl
import com.sg.aimouse.service.implementation.FileServiceImpl
import kotlinx.coroutines.launch

class HomeViewModel(
    context: Context
) : ViewModel(),
    BluetoothService by BluetoothServiceImpl(context),
    FileService by FileServiceImpl() {

    init {
        viewModelScope.launch {
            bluetoothState.collect { state ->
                when (state) {
                    BluetoothState.CONNECTED -> sendBluetoothCommand(CommandType.LIST_FILE)
                    BluetoothState.DISCONNECTED -> closeBluetoothConnection()
                    BluetoothState.CONNECTING -> Unit
                }
            }
        }
    }

    override fun onCleared() {
        closeBluetoothConnection(true)
        super.onCleared()
    }
}