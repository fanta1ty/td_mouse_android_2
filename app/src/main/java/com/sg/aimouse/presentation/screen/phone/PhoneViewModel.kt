package com.sg.aimouse.presentation.screen.phone

import android.content.Context
import androidx.lifecycle.ViewModel
import com.sg.aimouse.model.File

class PhoneViewModel(context: Context) : ViewModel() {
    private val _files = mutableListOf(
        File("DCIM", isDirectory = true),
        File("Download", isDirectory = true),
        File("Movies", isDirectory = true),
        File("Music", isDirectory = true),
        File("Pictures", isDirectory = true)
    )
    val files: List<File> = _files
}