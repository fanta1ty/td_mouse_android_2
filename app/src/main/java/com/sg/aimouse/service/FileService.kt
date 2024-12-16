package com.sg.aimouse.service

import android.content.Context
import com.sg.aimouse.model.File

interface FileService {

    fun getLocalFiles(): List<File>

    fun retrieveLocalFiles()

    fun saveFile(context: Context, data: ByteArray, fileName: String)
}