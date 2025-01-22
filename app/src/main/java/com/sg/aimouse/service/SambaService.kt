package com.sg.aimouse.service

import com.sg.aimouse.model.File
import com.sg.aimouse.service.implementation.SMBState
import kotlinx.coroutines.flow.StateFlow

interface SambaService {

    val smbState: StateFlow<SMBState>
    val remoteFiles: List<File>
    val isTransferringFileSMB: Boolean

    fun connectSMB()

    fun closeSMB(isRelease: Boolean = false)

    fun retrieveRemoteFilesSMB(folderName: String = "")

    fun uploadFileSMB(fileName: String)

    fun downloadFileSMB(fileName: String)
}