package com.sg.aimouse.model

import kotlin.math.round

data class File(val fileName: String, val size: Long = 0, val isDirectory: Boolean = false) {
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
        val digit = size.toString().length

        return if (digit > 9) { // Gb
            val formatedSize = round(size.toDouble() / (1024 * 1024 * 1024))
            if (formatedSize > 1) "$formatedSize Gb" else "$formatedSize Gbs"
        } else if (digit > 6) { // Mb
            val formatedSize = round(size.toDouble() / (1024 * 1024))
            if (formatedSize > 1) "$formatedSize Mb" else "$formatedSize Mbs"
        } else if (digit > 3) { // Kb
            val formatedSize = size.toDouble() / 1024
            if (formatedSize > 1) "$formatedSize Kb" else "$formatedSize Kbs"
        } else { // byte
            if (size > 1) "$size byte" else "$size bytes"
        }
    }
}