package com.sg.aimouse.presentation.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sg.aimouse.service.LocalFileService
import com.sg.aimouse.service.SambaService
import com.sg.aimouse.service.implementation.LocalFileServiceImpl
import com.sg.aimouse.service.implementation.SambaServiceImpl

class HomeViewModel(
    context: Context
) : ViewModel(),
    LocalFileService by LocalFileServiceImpl(context),
    SambaService by SambaServiceImpl(context) {

    override fun onCleared() {
        closeSMB(isRelease = true)
        super.onCleared()
    }
}