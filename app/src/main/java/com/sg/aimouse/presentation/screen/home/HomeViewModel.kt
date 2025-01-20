package com.sg.aimouse.presentation.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sg.aimouse.service.BluetoothService
import com.sg.aimouse.service.LocalFileService
import com.sg.aimouse.service.SambaService
import com.sg.aimouse.service.implementation.BluetoothServiceImplLocal
import com.sg.aimouse.service.implementation.LocalFileServiceImpl
import com.sg.aimouse.service.implementation.SambaServiceImpl

class HomeViewModel(
    context: Context
) : ViewModel(),
    BluetoothService by BluetoothServiceImplLocal(context),
    LocalFileService by LocalFileServiceImpl(),
    SambaService by SambaServiceImpl(context) {

    override fun onCleared() {
        closeSMB()
        super.onCleared()
    }
}