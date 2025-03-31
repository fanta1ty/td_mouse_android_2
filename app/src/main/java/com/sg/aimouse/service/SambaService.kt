package com.sg.aimouse.service

import com.sg.aimouse.model.File
import com.sg.aimouse.service.implementation.SMBState

interface SambaService {

    val remoteFiles: List<File>
    val isTransferringFileSMB: Boolean
    val transferSpeed: String
    val transferProgress: Float

    fun connectSMB()

    fun closeSMB(isRelease: Boolean = false)

    fun retrieveRemoteFilesSMB(folderName: String = "")

    fun uploadFileSMB(fileName: String)

    fun downloadFileSMB(fileName: String)

    fun updateSMBState(state: SMBState)

    fun deleteFileSMB(fileName: String)
}