package com.sg.aimouse.presentation.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sg.aimouse.model.File
import com.sg.aimouse.service.BluetoothService
import com.sg.aimouse.service.LocalFileService
import com.sg.aimouse.service.SambaService
import com.sg.aimouse.service.implementation.BluetoothServiceImplLocal
import com.sg.aimouse.service.implementation.LocalFileServiceImpl
import com.sg.aimouse.service.implementation.SambaServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(
    context: Context,
    sambaService: SambaService? = null
) : ViewModel(),
    BluetoothService by BluetoothServiceImplLocal(context),
    LocalFileService by LocalFileServiceImpl(context),
    SambaService by (sambaService ?: SambaServiceImpl(context)) {

    init {
        retrieveLocalFiles()
        if (sambaService != null) {
            retrieveRemoteFilesSMB()
        }
    }

    fun uploadFileOrFolder(file: File) {
        if (file.isDirectory) {
            uploadFolderSMB(file.path)
        } else {
            uploadFileSMB(file.fileName)
        }
    }

    fun downloadFileOrFolder(file: File) {
        if (file.isDirectory) {
            downloadFolderSMB(file.fileName)
        } else {
            downloadFileSMB(file.fileName)
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