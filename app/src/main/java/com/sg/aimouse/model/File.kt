package com.sg.aimouse.model

data class File(
    val fileName: String,
    val isDirectory: Boolean = false,
    val size: Long = 0,
    val formatedSize: String = ""
)