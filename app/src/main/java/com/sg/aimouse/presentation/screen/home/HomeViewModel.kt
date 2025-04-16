package com.sg.aimouse.presentation.screen.home

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import com.sg.aimouse.model.File
import com.sg.aimouse.service.SambaService
import com.sg.aimouse.service.implementation.LocalFileServiceImpl
import com.sg.aimouse.service.implementation.SambaServiceImpl
import com.sg.aimouse.service.implementation.SambaServiceImpl.TransferStats
import com.sg.aimouse.service.implementation.SMBState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File as JavaFile
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.widget.Toast
import android.util.Log
import com.sg.aimouse.common.AiMouseSingleton

class HomeViewModel(
    private val context: Context,
    private val sambaService: SambaService? = null
) : ViewModel() {
    private val localFileService = LocalFileServiceImpl(context)
    private val actualSambaService = sambaService ?: SambaServiceImpl(context, localFileService)

    // Delegate to services
    private val _localFileDelegate = localFileService
    private val _sambaDelegate = actualSambaService

    // Implement service interfaces through delegation
    val localFiles get() = _localFileDelegate.localFiles
    fun retrieveLocalFiles() = _localFileDelegate.retrieveLocalFiles()
    fun getCurrentFolderPath() = _localFileDelegate.getCurrentFolderPath()
    fun openFolder(folderPath: String) = _localFileDelegate.openFolder(folderPath)
    fun saveFile(data: ByteArray, fileName: String) = _localFileDelegate.saveFile(data, fileName)
    fun deleteFile(filePath: String) = _localFileDelegate.deleteFile(filePath)

    // Samba delegation
    val remoteFiles get() = _sambaDelegate.remoteFiles
    val isTransferringFileSMB get() = _sambaDelegate.isTransferringFileSMB
    val transferSpeed get() = _sambaDelegate.transferSpeed
    val transferProgress get() = _sambaDelegate.transferProgress
    fun closeSMB(isRelease: Boolean) = _sambaDelegate.closeSMB(isRelease)
    fun retrieveRemoteFilesSMB(folderName: String = "") = _sambaDelegate.retrieveRemoteFilesSMB(folderName)
    suspend fun uploadFileSMB(fileName: String, remotePath: String) = _sambaDelegate.uploadFileSMB(fileName, remotePath)
    suspend fun downloadFileSMB(fileName: String, targetDirectory: JavaFile? = null) = _sambaDelegate.downloadFileSMB(fileName, targetDirectory)
    suspend fun uploadFolderSMB(folderName: String) = _sambaDelegate.uploadFolderSMB(folderName)
    suspend fun downloadFolderSMB(folderName: String, targetDirectory: JavaFile? = null) = _sambaDelegate.downloadFolderSMB(folderName, targetDirectory)
    fun updateSMBState(state: SMBState) = _sambaDelegate.updateSMBState(state)
    fun deleteFileSMB(fileName: String) = _sambaDelegate.deleteFileSMB(fileName)

    var lastTransferStats: TransferStats? = null
        private set
    var showTransferDialog = mutableStateOf(false)
    var lastTransferredFileName: String? = null

    var currentLocalPath by mutableStateOf(
        Environment.getExternalStorageDirectory().path
    )
        private set

    var currentRemotePath by mutableStateOf("")
        private set

    init {
        retrieveLocalFiles()
        if (sambaService != null) {
            retrieveRemoteFilesSMB()
        }
        currentLocalPath = getCurrentFolderPath()
    }

    fun openLocalFolder(folder: File) {
        if (folder.isDirectory) {
            currentLocalPath = folder.path
            openFolder(folder.path)
        }
    }

    fun navigateUpLocal() {
        val parent = JavaFile(currentLocalPath).parentFile
        if (parent != null && parent.exists()) {
            currentLocalPath = parent.path
            openFolder(currentLocalPath)
        }
    }

    fun refreshCurrentLocalFolder() {
        openFolder(currentLocalPath)
    }

    fun openRemoteFolder(folder: File) {
        if (folder.isDirectory) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val newPath = if (currentRemotePath.isEmpty()) {
                        folder.fileName
                    } else {
                        "$currentRemotePath/${folder.fileName}"
                    }
                    currentRemotePath = newPath
                    (actualSambaService as? SambaServiceImpl)?.currentRemotePath = newPath
                    retrieveRemoteFilesSMB(newPath)
                } catch (e: Exception) {
                    Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to open remote folder", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to open folder: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun navigateUpRemote() {
        val parts = currentRemotePath.split("/")
        if (parts.size > 1) {
            currentRemotePath = parts.dropLast(1).joinToString("/")
            retrieveRemoteFilesSMB(currentRemotePath)
        } else {
            currentRemotePath = ""
            retrieveRemoteFilesSMB("")
        }
    }

    fun uploadFileOrFolder(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            val stats = if (file.isDirectory) {
                uploadFolderSMB(file.path)
            } else {
                uploadFileSMB(file.fileName, currentRemotePath)
            }
            withContext(Dispatchers.Main) {
                lastTransferStats = stats
                lastTransferredFileName = file.fileName
                if (lastTransferStats != null) {
                    showTransferDialog.value = true
                }
            }
        }
    }

    fun downloadFileOrFolder(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            val stats = if (file.isDirectory) {
                val currentDir = JavaFile(currentLocalPath)
                downloadFolderSMB(file.fileName, currentDir)
            } else {
                val currentDir = JavaFile(currentLocalPath)
                downloadFileSMB(file.fileName, currentDir)
            }
            withContext(Dispatchers.Main) {
                lastTransferStats = stats
                lastTransferredFileName = file.fileName
                if (lastTransferStats != null) {
                    showTransferDialog.value = true
                }
            }
        }
    }

    fun deleteFile(file: File, isRemote: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            if (isRemote) {
                val remotePath = if (currentRemotePath.isEmpty()) file.fileName
                               else "$currentRemotePath/${file.fileName}"
                deleteFileSMB(remotePath)
            } else {
                deleteFile(file.path)
            }
        }
    }

    private fun isMediaFile(fileName: String): Boolean {
        val mediaExtensions = listOf(".mp4", ".avi", ".mp3", ".wav", ".jpg", ".jpeg", ".png", ".txt")
        return mediaExtensions.any { fileName.lowercase().endsWith(it) }
    }

    fun openRemoteFile(file: File) {
        if (!file.isDirectory && isMediaFile(file.fileName)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Create a unique temp file to avoid conflicts
                    val cacheDir = JavaFile(context.cacheDir, "media_preview")
                    cacheDir.mkdirs()
                    
                    // Use timestamp to make filename unique
                    val timestamp = System.currentTimeMillis()
                    val tempFileName = "${timestamp}_${file.fileName}"
                    val tempFile = JavaFile(cacheDir, tempFileName)
                    
                    // Download directly to cache
                    val stats = downloadFileSMB(file.fileName, tempFile.parentFile)
                    if (stats != null) {
                        withContext(Dispatchers.Main) {
                            try {
                                // Rename downloaded file to temp file name
                                val downloadedFile = JavaFile(tempFile.parentFile, file.fileName)
                                if (downloadedFile.exists()) {
                                    downloadedFile.renameTo(tempFile)
                                }

                                // Create content URI using FileProvider
                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    tempFile
                                )

                                // Create intent to open media file
                                val mimeType = when {
                                    file.fileName.endsWith(".jpg", true) ||
                                    file.fileName.endsWith(".jpeg", true) -> "image/jpeg"
                                    file.fileName.endsWith(".png", true) -> "image/png"
                                    file.fileName.endsWith(".txt", true) -> "text/plain"
                                    file.fileName.endsWith(".mp4", true) -> "video/mp4"
                                    file.fileName.endsWith(".avi", true) -> "video/x-msvideo"
                                    file.fileName.endsWith(".mp3", true) -> "audio/mpeg"
                                    file.fileName.endsWith(".wav", true) -> "audio/x-wav"
                                    else -> "*/*"
                                }

                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(contentUri, mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                
                                // Check if there's an app that can handle this intent
                                val packageManager = context.packageManager
                                val activities = packageManager.queryIntentActivities(intent, 0)
                                if (activities.isEmpty()) {
                                    throw Exception("No app found to handle $mimeType files")
                                }
                                
                                // Start activity with chooser
                                val chooserIntent = Intent.createChooser(intent, "Open with")
                                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooserIntent)

                                // Schedule cleanup of old temp files
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        // Delete files older than 1 hour
                                        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                                        cacheDir.listFiles()?.forEach { file ->
                                            try {
                                                val fileTimestamp = file.name.split("_")[0].toLong()
                                                if (fileTimestamp < oneHourAgo) {
                                                    file.delete()
                                                }
                                            } catch (e: Exception) {
                                                // Ignore errors for individual files
                                                Log.w(AiMouseSingleton.DEBUG_TAG, "Error deleting old temp file: ${file.name}", e)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(AiMouseSingleton.DEBUG_TAG, "Error cleaning up temp files", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(AiMouseSingleton.DEBUG_TAG, "Error in UI thread while opening media file", e)
                                Toast.makeText(context, "Error opening media file: ${e.message}", Toast.LENGTH_LONG).show()
                                // Clean up temp file if we failed to open it
                                tempFile.delete()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(AiMouseSingleton.DEBUG_TAG, "Error downloading media file", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error downloading media file: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else if (file.isDirectory) {
            openRemoteFolder(file)
        }
    }

    override fun onCleared() {
        closeSMB(isRelease = true)
        super.onCleared()
    }
}