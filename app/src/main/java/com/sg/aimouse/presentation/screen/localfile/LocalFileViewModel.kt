package com.sg.aimouse.presentation.screen.localfile

import java.net.URLConnection

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import com.sg.aimouse.model.File
import com.sg.aimouse.service.implementation.LocalFileServiceImpl
import com.sg.aimouse.service.implementation.SambaServiceImpl.TransferStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File as JavaFile
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.widget.Toast
import android.util.Log
import com.sg.aimouse.common.AiMouseSingleton
import com.sg.aimouse.service.BLEService
import com.sg.aimouse.service.BluetoothDevice
import com.sg.aimouse.service.implementation.BLEServiceSingleton
import com.sg.aimouse.service.implementation.PermissionServiceImpl

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

    var lastTransferStats: TransferStats? = null
        private set
    var showTransferDialog = mutableStateOf(false)
    var lastTransferredFileName: String? = null

    var currentLocalPath by mutableStateOf(
        Environment.getExternalStorageDirectory().path
    )
        private set


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
                            val javaFile = java.io.File(file.path)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                javaFile
                            )
                            intent.setDataAndType(uri, URLConnection.guessContentTypeFromName(file.fileName))
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(AiMouseSingleton.DEBUG_TAG, "Error opening local media file", e)
                            Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(AiMouseSingleton.DEBUG_TAG, "Error in IO thread while opening local media file", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else if (file.isDirectory) {
            openLocalFolder(file)
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

    fun readBleCharacteristic(uuid: String, callback: (ByteArray) -> Unit) {
        bleService.readCharacteristic(uuid, callback)
    }

    fun scanForBluetoothDevices(callback: (List<BluetoothDevice>) -> Unit) {
        if (permissionService.hasBluetoothPermission(context)) {
            bleService.scanForDevices(callback)
        } else {
            permissionService.requestPermissions(context, permissionService.requiredBluetoothPermissions, {
                bleService.scanForDevices(callback)
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
        bleService.unregisterConnectionStateCallback()
        bleService.disconnect()
        super.onCleared()
    }
}