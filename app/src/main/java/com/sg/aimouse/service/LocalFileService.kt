package com.sg.aimouse.service

import com.sg.aimouse.model.File

interface LocalFileService {

    val localFiles: List<File>
    val selectedLocalFiles: Map<String, File>

    fun addSelectedLocalFile(file: File)

    fun removeSelectedLocalFile(fileName: String)

    fun retrieveLocalFiles()

    fun saveFile(data: ByteArray, fileName: String)
}