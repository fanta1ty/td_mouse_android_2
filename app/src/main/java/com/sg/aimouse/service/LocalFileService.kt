package com.sg.aimouse.service

import com.sg.aimouse.model.File

interface LocalFileService {

    val localFiles: List<File>

    fun retrieveLocalFiles()

    fun saveFile(data: ByteArray, fileName: String)
}