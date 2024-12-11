package com.sg.aimouse.service

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.sg.aimouse.model.File

class FileServiceImpl(private val context: Context) {
    val files = mutableStateListOf<File>()
    var currentPath = ""
        private set

    fun getLocalFiles(path: String) {

    }
}