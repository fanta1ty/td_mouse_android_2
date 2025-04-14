package com.sg.aimouse.presentation.screen.connect

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sg.aimouse.service.SambaService
import com.sg.aimouse.service.implementation.SambaServiceImpl
import com.sg.aimouse.service.implementation.LocalFileServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConnectionViewModel(private val context: Context) : ViewModel() {
    private val localFileService = LocalFileServiceImpl(context)
    private val sambaService: SambaService = SambaServiceImpl(context, localFileService)
    var lastErrorMessage: String = ""

    fun connectSMB(ipAddress: String, username: String, password: String, rootDir: String, callback: (Boolean) -> Unit) {
        (sambaService as SambaServiceImpl).updateConnectionInfo(ipAddress, username, password, rootDir)
        sambaService.connectSMB()

        CoroutineScope(Dispatchers.Main).launch {
            delay(2000) // waiting connect
            val isConnected = sambaService.isConnected()
            if (!isConnected) {
                lastErrorMessage = context.getString(com.sg.aimouse.R.string.td_mouse_connection_error)
            }
            callback(isConnected)
        }
    }

    fun getSambaService(): SambaService = sambaService
}