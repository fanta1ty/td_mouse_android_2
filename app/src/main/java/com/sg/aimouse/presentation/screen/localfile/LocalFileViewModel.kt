package com.sg.aimouse.presentation.screen.localfile

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import com.sg.aimouse.model.File
import com.sg.aimouse.service.BLEService
import com.sg.aimouse.service.BluetoothDevice
import com.sg.aimouse.service.implementation.BLEServiceSingleton
import com.sg.aimouse.service.implementation.LocalFileServiceImpl
import com.sg.aimouse.service.implementation.PermissionServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLConnection
import java.io.File as JavaFile

class LocalFileViewModel(
    private val context: Context
) : ViewModel() {
    private val localFileService = LocalFileServiceImpl(context)
    private val bleService: BLEService = BLEServiceSingleton.getInstance(context)
    private val permissionService = PermissionServiceImpl()

    // Delegate to services
    private val _localFileDelegate = localFileService

    // Implement service interfaces through delegation
    val localFiles get() = _localFileDelegate.localFiles
    fun retrieveLocalFiles() = _localFileDelegate.retrieveLocalFiles()
    fun getCurrentFolderPath() = _localFileDelegate.getCurrentFolderPath()
    fun openFolder(folderPath: String) = _localFileDelegate.openFolder(folderPath)
    fun deleteFile(filePath: String) = _localFileDelegate.deleteFile(filePath)

    var currentLocalPath by mutableStateOf(
        Environment.getExternalStorageDirectory().path
    )
        private set

    // BLE device list
    private val _discoveredDevices = mutableStateOf<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: List<BluetoothDevice>
        get() = _discoveredDevices.value

    init {
        currentLocalPath = getCurrentFolderPath()
    }

    fun openLocalFolder(folder: File) {
        if (folder.isDirectory) {
            currentLocalPath = folder.path
            openFolder(folder.path)
        }
    }

    fun navigateUpLocal() {
        val parent = JavaFile(currentLocalPath).parentFile
        if (parent != null && parent.exists()) {
            currentLocalPath = parent.path
            openFolder(currentLocalPath)
        }
    }

    fun deleteFile(file: File) {
        CoroutineScope(Dispatchers.Main).launch {
            deleteFile(file.path)
        }
    }

    private fun isMediaFile(fileName: String): Boolean {
        val mediaExtensions = listOf(".mp4", ".avi", ".mp3", ".wav", ".jpg", ".jpeg", ".png", ".txt")
        return mediaExtensions.any { fileName.lowercase().endsWith(it) }
    }

    fun openLocalFile(file: File) {
        if (!file.isDirectory && isMediaFile(file.fileName)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withContext(Dispatchers.Main) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW)
                            val javaFile = JavaFile(file.path)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                javaFile
                            )
                            val mimeType = URLConnection.guessContentTypeFromName(file.fileName)
                            intent.setDataAndType(uri, mimeType)
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("LocalFileViewModel", "Error opening file: ${e.message}")
                            Toast.makeText(
                                context,
                                "Cannot open this file type",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("LocalFileViewModel", "Error opening file: ${e.message}")
                        Toast.makeText(
                            context,
                            "Error opening file",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // BLE related methods
    fun isBleConnected(): Boolean {
        return bleService.isConnected()
    }

    fun isBluetoothEnabled(): Boolean {
        return bleService.isBluetoothEnabled()
    }

    fun bleDisconnect() {
        bleService.disconnect()
    }

    fun connectedDevice(): BluetoothDevice? {
        return bleService.getConnectedDevice()
    }

    fun stopScanningDevices() {
        // Stop scanning by passing empty callback
        bleService.scanForDevices { }
    }

    fun scanForBluetoothDevices(callback: (List<BluetoothDevice>) -> Unit) {
        if (permissionService.hasBluetoothPermission(context)) {
            bleService.scanForDevices { devices ->
                _discoveredDevices.value = devices
                callback(devices)
            }
        } else {
            permissionService.requestPermissions(context, permissionService.requiredBluetoothPermissions, {
                bleService.scanForDevices { devices ->
                    _discoveredDevices.value = devices
                    callback(devices)
                }
            }, {
                Log.e("LocalfileViewModel", "Bluetooth permissions denied")
                callback(emptyList())
            })
        }
    }

    fun connectToBluetoothDevice(device: BluetoothDevice, callback: (Boolean) -> Unit) {
        bleService.connectToDevice(device, callback)
    }

    fun registerBleConnectionCallback(callback: (Boolean) -> Unit) {
        bleService.registerConnectionStateCallback(callback)
    }

    override fun onCleared() {
        super.onCleared()
    }
}