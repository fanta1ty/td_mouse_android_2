package com.sg.aimouse.presentation.screen.connect

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sg.aimouse.service.SambaService
import com.sg.aimouse.service.implementation.SambaServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConnectionViewModel(private val context: Context) : ViewModel() { // Save Context as Property
    private val sambaService: SambaService = SambaServiceImpl(context) // Private instance
    var lastErrorMessage: String = ""

    fun connectSMB(ipAddress: String, username: String, password: String, rootDir: String, callback: (Boolean) -> Unit) {
        // Update connection information in SambaServiceImpl
        (sambaService as SambaServiceImpl).updateConnectionInfo(ipAddress, username, password, rootDir)

        // Try connecting
        sambaService.connectSMB()

        // Check connection results
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000) // Wait 2 seconds for connection to complete
            val isConnected = sambaService.isConnected()
            if (!isConnected) {
                lastErrorMessage = context.getString(com.sg.aimouse.R.string.td_mouse_connection_error)
            }
            callback(isConnected)
        }
    }

    // After successful connection
    fun getSambaService(): SambaService = sambaService
}