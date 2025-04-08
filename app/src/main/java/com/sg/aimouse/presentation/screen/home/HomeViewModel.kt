package com.sg.aimouse.presentation.screen.home

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.sg.aimouse.model.File
import com.sg.aimouse.service.BluetoothService
import com.sg.aimouse.service.LocalFileService
import com.sg.aimouse.service.SambaService
import com.sg.aimouse.service.implementation.BluetoothServiceImplLocal
import com.sg.aimouse.service.implementation.LocalFileServiceImpl
import com.sg.aimouse.service.implementation.SambaServiceImpl
import com.sg.aimouse.service.implementation.SambaServiceImpl.TransferStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File as JavaFile
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class HomeViewModel(
    context: Context,
    private val sambaService: SambaService? = null
) : ViewModel(),
    BluetoothService by BluetoothServiceImplLocal(context),
    LocalFileService by LocalFileServiceImpl(context),
    SambaService by (sambaService ?: SambaServiceImpl(context)) {

    var lastTransferStats: TransferStats? = null
        private set
    var showTransferDialog = mutableStateOf(false)
    var lastTransferredFileName: String? = null

    var currentLocalPath by mutableStateOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
    )
        private set

    var currentRemotePath by mutableStateOf("")
        private set

    init {
        retrieveLocalFiles()
        if (sambaService != null) {
            retrieveRemoteFilesSMB()
        }
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

    fun openRemoteFolder(folder: File) {
        if (folder.isDirectory) {
            val newPath = if (currentRemotePath.isEmpty()) {
                folder.fileName
            } else {
                "$currentRemotePath/${folder.fileName}"
            }
            currentRemotePath = newPath
            retrieveRemoteFilesSMB(newPath)
        }
    }

    fun navigateUpRemote() {
        val parts = currentRemotePath.split("/")
        if (parts.size > 1) {
            currentRemotePath = parts.dropLast(1).joinToString("/")
            retrieveRemoteFilesSMB(currentRemotePath)
        } else {
            currentRemotePath = ""
            retrieveRemoteFilesSMB("")
        }
    }

    fun uploadFileOrFolder(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            val stats = if (file.isDirectory) {
                uploadFolderSMB(file.path)
            } else {
                uploadFileSMB(file.fileName)
            }
            withContext(Dispatchers.Main) {
                lastTransferStats = stats
                lastTransferredFileName = file.fileName
                if (lastTransferStats != null) {
                    showTransferDialog.value = true
                }
            }
        }
    }

    fun downloadFileOrFolder(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            val stats = if (file.isDirectory) {
                downloadFolderSMB(file.fileName)
            } else {
                downloadFileSMB(file.fileName)
            }
            withContext(Dispatchers.Main) {
                lastTransferStats = stats
                lastTransferredFileName = file.fileName
                if (lastTransferStats != null) {
                    showTransferDialog.value = true
                }
            }
        }
    }

    fun deleteFile(file: File, isRemote: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            if (isRemote) {
                deleteFileSMB(file.fileName)
            } else {
                deleteFile(file.path)
            }
        }
    }

    override fun onCleared() {
        closeSMB(isRelease = true)
        super.onCleared()
    }
}