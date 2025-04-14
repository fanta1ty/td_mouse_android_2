package com.sg.aimouse.service

import com.sg.aimouse.model.File
import com.sg.aimouse.service.implementation.SMBState
import com.sg.aimouse.service.implementation.SambaServiceImpl.TransferStats

interface SambaService {

    val remoteFiles: List<File>
    val isTransferringFileSMB: Boolean
    val transferSpeed: String
    val transferProgress: Float

    fun connectSMB()
    fun closeSMB(isRelease: Boolean = false)
    fun retrieveRemoteFilesSMB(folderName: String = "")
    suspend fun uploadFileSMB(fileName: String): TransferStats?
    suspend fun downloadFileSMB(fileName: String, targetDirectory: java.io.File? = null): TransferStats?
    suspend fun uploadFolderSMB(folderName: String): TransferStats?
    suspend fun downloadFolderSMB(folderName: String): TransferStats?
    fun updateSMBState(state: SMBState)
    fun deleteFileSMB(fileName: String)
}