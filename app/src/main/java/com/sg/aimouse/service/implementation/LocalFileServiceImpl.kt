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

    override fun retrieveLocalFiles() {
        coroutineScope.launch {
            val downloadDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )

            if (downloadDir.exists() && downloadDir.isDirectory) {
                val localFiles = downloadDir.listFiles()?.map { file ->
                    val size = if (!file.isDirectory) file.length() else 0
                    File(file.name, size, file.path, file.isDirectory)
                } ?: emptyList()

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

    private fun toast(@StringRes msgId: Int) {
        Toast.makeText(context, context.getString(msgId), Toast.LENGTH_SHORT).show()
    }
}