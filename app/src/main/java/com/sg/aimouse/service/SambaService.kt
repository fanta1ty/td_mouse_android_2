package com.sg.aimouse.service

import com.sg.aimouse.model.File
import com.sg.aimouse.service.implementation.SMBState

interface SambaService {

    val isTransferringFileSMB: Boolean
    val transferSpeed: String
    val transferProgress: Float
    val remoteFiles: List<File>
    val selectedRemoteFiles: Map<String, File>
    val currentPath: List<String>

    fun addSelectedRemoteFile(file: File)

    fun removeSelectedRemoteFile(fileName: String)

    fun appendPath(folderName: String)

    fun removePath()

    fun connectSMB()

    fun closeSMB(isRelease: Boolean = false)

    fun retrieveRemoteFilesSMB()

    fun uploadFileSMB(fileName: String)

    fun downloadFileSMB(fileName: String)

    fun deleteFilesSMB(fileName: List<String>)

    fun updateSMBState(state: SMBState)
}