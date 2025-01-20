package com.sg.aimouse.service

import com.sg.aimouse.model.File

interface SambaService {

    val remoteFiles: List<File>

    fun connectSMB()

    fun closeSMB()

    suspend fun getRemoteFiles(folderName: String = "")

    fun uploadFile(fileName: String)

    fun downloadFile(fileName: String)
}