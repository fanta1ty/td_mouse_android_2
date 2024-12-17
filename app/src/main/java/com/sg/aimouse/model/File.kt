package com.sg.aimouse.model

import java.util.Locale

data class File(
    val fileName: String,
    val size: Long = 0,
    val isDirectory: Boolean = false
) {
    val shortenFileName: String
    val formatedFileSize: String

    init {
        shortenFileName = shortenFileName()
        formatedFileSize = formatedFileSize()
    }

    fun shouldTransferViaBluetooth(): Boolean {
        return size.toDouble() / (1024 * 1024) < 2
    }

    private fun shortenFileName(): String {
        return if (isDirectory) {
            "${fileName.take(8)}...${fileName.takeLast(7)}"
        } else {
            val extensionIndex = fileName.lastIndexOf('.')
            if (extensionIndex == -1
                || extensionIndex == 0
                || extensionIndex == fileName.length - 1
            ) {
                // No valid extension found
                if (fileName.length <= 15) {
                    fileName
                } else {
                    "${fileName.take(8)}...${fileName.takeLast(7)}"
                }
            } else {
                // Valid extension exists
                val namePart = fileName.substring(0, extensionIndex)
                val extension = fileName.substring(extensionIndex)
                if (namePart.length <= 15) {
                    fileName
                } else {
                    "${namePart.take(8)}...${namePart.takeLast(7)}$extension"
                }
            }
        }
    }

    private fun formatedFileSize(): String {
        if (isDirectory) return ""

        val digit = size.toString().length

        return if (digit > 9) { // GB
            String.format(Locale.US, "%.2f GB", size.toDouble() / (1024 * 1024 * 1024))
        } else if (digit > 6) { // MB
            String.format(Locale.US, "%.2f MB", size.toDouble() / (1024 * 1024))
        } else if (digit > 3) { // KB
            String.format(Locale.US, "%.0f KB", size.toDouble() / 1024)
        } else { // Byte
            if (size > 1.0) "$size Bytes" else "$size Byte"
        }
    }
}