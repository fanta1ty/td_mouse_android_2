package com.sg.aimouse.service.implementation

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateListOf
import com.sg.aimouse.R
import com.sg.aimouse.model.File
import com.sg.aimouse.service.LocalFileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.io.File as JavaFile

class LocalFileServiceImpl(private val context: Context) : LocalFileService {

    private val _localFiles = mutableStateListOf<File>()
    override val localFiles: List<File>
        get() = _localFiles

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var _currentFolderPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path

    override fun retrieveLocalFiles() {
        coroutineScope.launch {
            val currentFolder = JavaFile(_currentFolderPath)
            if (currentFolder.exists() && currentFolder.isDirectory) {
                val localFiles = currentFolder.listFiles()?.map { file ->
                    val size = if (!file.isDirectory) file.length() else 0
                    File(
                        fileName = file.name,
                        size = size,
                        path = file.path,
                        isDirectory = file.isDirectory,
                        createdTime = file.lastModified()
                    )
                }?.sortedByDescending { it.createdTime } ?: emptyList()

                withContext(Dispatchers.Main) {
                    _localFiles.clear()
                    _localFiles.addAll(localFiles)
                }
            }
        }
    }

    override fun saveFile(data: ByteArray, fileName: String) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )

        if (downloadDir == null) {
            toast(R.string.save_file_error)
            return
        }

        downloadDir.let { dir ->
            val file = JavaFile(dir, fileName)

            try {
                FileOutputStream(file).use { output -> output.write(data) }
            } catch (e: IOException) {
                e.printStackTrace()
                toast(R.string.save_file_error)
            }
        }
    }

    override fun deleteFile(filePath: String) {
        coroutineScope.launch {
            val file = JavaFile(filePath)
            val success = if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            withContext(Dispatchers.Main) {
                if (success) {
                    toast(R.string.delete_file_succeeded)
                    retrieveLocalFiles()
                } else {
                    toast(R.string.delete_file_error)
                }
            }
        }
    }

    override fun openFolder(folderPath: String) {
        coroutineScope.launch {
            val folder = JavaFile(folderPath)
            if (folder.exists() && folder.isDirectory) {
                _currentFolderPath = folderPath
                val files = folder.listFiles()?.map { file ->
                    File(
                        fileName = file.name,
                        size = if (!file.isDirectory) file.length() else 0,
                        path = file.path,
                        isDirectory = file.isDirectory,
                        createdTime = file.lastModified()
                    )
                }?.sortedByDescending { it.createdTime } ?: emptyList()

                withContext(Dispatchers.Main) {
                    _localFiles.clear()
                    _localFiles.addAll(files)
                }
            } else {
                withContext(Dispatchers.Main) {
                    toast(R.string.folder_not_found)
                }
            }
        }
    }

    override fun getCurrentFolderPath(): String = _currentFolderPath

    private fun toast(@StringRes msgId: Int) {
        Toast.makeText(context, context.getString(msgId), Toast.LENGTH_SHORT).show()
    }
}