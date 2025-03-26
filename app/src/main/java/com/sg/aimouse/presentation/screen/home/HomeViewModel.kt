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
    context: Context,
    sambaService: SambaService? = null // Get from ConnectionViewModel if available
) : ViewModel(),
    BluetoothService by BluetoothServiceImplLocal(context),
    LocalFileService by LocalFileServiceImpl(context),
    SambaService by (sambaService ?: SambaServiceImpl(context)) {

    override fun onCleared() {
        closeSMB(isRelease = true)
        super.onCleared()
    }
}