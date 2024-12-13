package com.sg.aimouse.model

data class File(
    val fileName: String,
    val size: Long = 0,
    val isDirectory: Boolean = false,
    val formatedSize: String = ""
)