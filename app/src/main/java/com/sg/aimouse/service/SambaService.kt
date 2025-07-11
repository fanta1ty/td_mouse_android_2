package com.sg.aimouse.service

import com.sg.aimouse.model.File
import com.sg.aimouse.service.implementation.SMBState
import com.sg.aimouse.service.implementation.SambaServiceImpl.TransferStats
import java.io.File as JavaFile

interface SambaService {

    val remoteFiles: List<File>
    val isTransferringFileSMB: Boolean
    val transferSpeed: String
    val transferProgress: Float

    fun connectSMB()
    fun closeSMB(isRelease: Boolean = false)
    fun retrieveRemoteFilesSMB(folderName: String = "")
    suspend fun uploadFileSMB(fileName: String, remotePath: String): TransferStats?
    suspend fun downloadFileSMB(fileName: String, targetDirectory: java.io.File? = null): TransferStats?
    suspend fun uploadFolderSMB(folderName: String, remotePath: String = ""): TransferStats?
    suspend fun downloadFolderSMB(remoteFolderName: String, targetDirectory: JavaFile? = null): TransferStats?
    fun updateSMBState(state: SMBState)
    fun deleteFileSMB(fileName: String)
    fun isConnected(): Boolean
}