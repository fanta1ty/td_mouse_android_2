package com.sg.aimouse.presentation.screen.phone

import androidx.lifecycle.ViewModel
import com.sg.aimouse.model.File

class PhoneViewModel : ViewModel() {
    private val _files = mutableListOf(
        File("DCIM", true),
        File("Download", true),
        File("Movies", true),
        File("Music", true),
        File("Pictures", true)
    )
    val files: List<File> = _files
}