package com.sg.aimouse.service.implementation

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sg.aimouse.R
import com.sg.aimouse.model.File
import com.sg.aimouse.service.FileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.io.File as JavaFile

class FileServiceImpl : FileService {

    private var _shouldShowLocalFileList by mutableStateOf(false)
    override val shouldShowLocalFileList: Boolean
        get() = _shouldShowLocalFileList

    private val _localFiles = mutableStateListOf<File>()
    override val localFiles: List<File>
        get() = _localFiles

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun retrieveLocalFiles() {
        coroutineScope.launch {
            val downloadDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )

            if (downloadDir.exists() && downloadDir.isDirectory) {
                val localFiles = downloadDir.listFiles()?.map { file ->
                    val size = if (!file.isDirectory) file.length() else 0
                    File(file.name, size, file.isDirectory)
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    _localFiles.clear()
                    _localFiles.addAll(localFiles)
                }
            }
        }
    }

    override fun saveFile(context: Context, data: ByteArray, fileName: String) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )

        if (downloadDir == null) {
            Toast.makeText(
                context,
                context.getString(R.string.save_file_error),
                Toast.LENGTH_SHORT
            ).show()
        }

        downloadDir?.let { dir ->
            val file = JavaFile(dir, fileName)

            try {
                FileOutputStream(file).use { output -> output.write(data) }

                Toast.makeText(
                    context,
                    context.getString(R.string.save_file_succeeded),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: IOException) {
                e.printStackTrace()

                Toast.makeText(
                    context,
                    context.getString(R.string.save_file_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun updateShouldShowLocalFileList(isShow: Boolean) {
        _shouldShowLocalFileList = isShow
    }
}