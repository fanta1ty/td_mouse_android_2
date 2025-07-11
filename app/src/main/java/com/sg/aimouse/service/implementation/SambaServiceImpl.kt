package com.sg.aimouse.service.implementation

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2Dialect.SMB_2_1
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.sg.aimouse.R
import com.sg.aimouse.common.AiMouseSingleton
import com.sg.aimouse.model.File
import com.sg.aimouse.service.SambaService
import com.sg.aimouse.service.LocalFileService
import com.sg.aimouse.util.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.File as JavaFile

enum class SMBState {
    RECONNECT, CONNECTING, CONNECTED, DISCONNECTED
}

class SambaServiceImpl(
    internal val context: Context,
    private val localFileService: LocalFileService
) : SambaService {

    //region Fields
    private var host: String = ""
    private var username: String = ""
    private var pwd: String = ""
    private var rootDir: String = ""

    private val smbClient = SMBClient(
        SmbConfig.builder()
            .withDialects(SMB_2_1)
            .withSigningEnabled(false)
            .withBufferSize(16 * 1024 * 1024)
            .build()
    )
    private var connection: Connection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var _currentRemotePath = ""
    var currentRemotePath: String = ""
        get() = _currentRemotePath

    private val _smbState = MutableStateFlow(SMBState.DISCONNECTED)

    private val _remoteFiles = mutableStateListOf<File>()
    override val remoteFiles: List<File>
        get() = _remoteFiles

    private var _isTransferringFileSMB by mutableStateOf(false)
    override val isTransferringFileSMB: Boolean
        get() = _isTransferringFileSMB

    private var _transferSpeed by mutableStateOf("")
    override val transferSpeed: String
        get() = _transferSpeed

    private var _transferProgress by mutableFloatStateOf(0f)
    override val transferProgress: Float
        get() = _transferProgress

    var lastTransferStats: TransferStats? = null
        private set

    //endregion

    init {
        coroutineScope.launch(Dispatchers.Main) {
            _smbState.collect { state ->
                when (state) {
                    SMBState.CONNECTED -> Unit
                    SMBState.CONNECTING -> Unit
                    SMBState.RECONNECT -> reconnect()
                    SMBState.DISCONNECTED -> closeSMB()
                }
            }
        }
    }

    // Function to update connection information
    fun updateConnectionInfo(newHost: String, newUsername: String, newPassword: String, newRootDir: String) {
        host = newHost
        username = newUsername
        pwd = newPassword
        rootDir = newRootDir
    }

    override fun deleteFileSMB(fileName: String) {
        coroutineScope.launch {
            try {
                ensureConnected()
                
                diskShare!!.apply {
                    // For files
                    if (fileExists(fileName)) {
                        rm(fileName)
                    } 
                    // For folders
                    else if (folderExists(fileName)) {
                        rmdir(fileName, true)
                    }
                    
                    // Refresh files in current path
                    withContext(Dispatchers.Main) {
                        toast(R.string.delete_file_succeeded)
                        retrieveRemoteFilesSMB(currentRemotePath)
                    }
                }
            } catch (e: Exception) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to delete remote file", e)
                withContext(Dispatchers.Main) { toast(R.string.delete_file_error) }
                // Only reconnect if it's a connection issue
                if (e is IOException || diskShare == null || !isConnected()) {
                    _smbState.value = SMBState.RECONNECT
                }
            }
        }
    }

    override fun isConnected(): Boolean {
        return _smbState.value == SMBState.CONNECTED
    }

    override fun connectSMB() {
//            if (!isNetworkAvailable(context)) {
//                toast(R.string.no_internet)
//                _smbState.value = SMBState.DISCONNECTED
//                return
//            }

        if (_smbState.value == SMBState.CONNECTED || _smbState.value == SMBState.CONNECTING)
            return

        coroutineScope.launch {
            try {
                _smbState.value = SMBState.CONNECTING

                connection = smbClient.connect(host)
                session = connection?.authenticate(
                    AuthenticationContext(username, pwd.toCharArray(), "")
                )
                diskShare = session?.connectShare(rootDir) as DiskShare

                _smbState.value = SMBState.CONNECTED
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to connect to TD Mouse", e)
                _smbState.value = SMBState.DISCONNECTED
            }
        }
    }

    override fun closeSMB(isRelease: Boolean) {
        coroutineScope.launch {
            try {
                // Close resources in reverse order of creation
                try {
                    diskShare?.close()
                } catch (e: Exception) {
                    Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to close disk share", e)
                }
                diskShare = null

                try {
                    session?.close()
                } catch (e: Exception) {
                    Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to close session", e)
                }
                session = null

                try {
                    connection?.close()
                } catch (e: Exception) {
                    Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to close connection", e)
                }
                connection = null
            } catch (e: Exception) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to close Samba connection", e)
            }

            if (isRelease) {
                coroutineScope.cancel()
            }
        }
    }

    override fun retrieveRemoteFilesSMB(folderName: String) {
        coroutineScope.launch {
            try {
                _remoteFiles.clear()
                ensureConnected()
                
                val normalizedPath = if (folderName.isEmpty()) "/" else folderName
                _currentRemotePath = folderName
                diskShare!!.apply {
                    val remoteFolder = openDirectory(
                        normalizedPath,
                        setOf(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    ) ?: throw RuntimeException()

                    val files = remoteFolder.map { fileInfo ->
                        val isDir = EnumWithValue.EnumUtils.isSet(
                            fileInfo.fileAttributes,
                            FileAttributes.FILE_ATTRIBUTE_DIRECTORY
                        )

                        val createdTime = fileInfo.creationTime.toEpochMillis()

                        File(
                            fileName = fileInfo.fileName,
                            size = fileInfo.endOfFile,
                            isDirectory = isDir,
                            createdTime = createdTime
                        )
                    }.sortedWith(compareByDescending<File> { it.isDirectory }
                        .thenByDescending { it.createdTime })

                    _remoteFiles.addAll(files.filter { file ->
                        val name = file.fileName
                        name != "." && name != ".." && !name.startsWith(".")
                    })
                }
            } catch (e: Exception) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to get remote files", e)
                withContext(Dispatchers.Main) { toast(R.string.retrieve_remote_file_error) }
                // Only reconnect if it's a connection issue
                if (e is IOException || diskShare == null || !isConnected()) {
                    _smbState.value = SMBState.RECONNECT
                }
            }
        }
    }

    private suspend fun ensureConnected() {
        if (diskShare == null || !isConnected()) {
            try {
                connectSMB()
                // Wait for connection to be established
                var attempts = 0
                while (!isConnected() && attempts < 3) {
                    delay(500)
                    attempts++
                }
                if (!isConnected()) {
                    throw IOException("Failed to reconnect to SMB server after $attempts attempts")
                }
            } catch (e: Exception) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Connection failed", e)
                // Don't change state here, just propagate the error
                throw e
            }
        }
    }

    override suspend fun downloadFileSMB(fileName: String, targetDirectory: JavaFile?): TransferStats? {
        _isTransferringFileSMB = true
        _transferSpeed = "Transfer speed: %.2f MB/s".format(0.0)
        _transferProgress = 0f
        
        var requestedFile: com.hierynomus.smbj.share.File? = null
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        return try {
            ensureConnected()

            requestedFile = diskShare!!.openFile(
                fileName,
                setOf(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            val fileSize = requestedFile.getFileInformation(FileStandardInformation::class.java).endOfFile
            val targetDir = targetDirectory ?: JavaFile(localFileService.getCurrentFolderPath())
            
            // Extract just the file name from the path
            val justFileName = fileName.split("/").last()
            val localFile = JavaFile(targetDir, justFileName)

            // Create parent directories if they don't exist
            localFile.parentFile?.mkdirs()

            inputStream = requestedFile.inputStream
            outputStream = FileOutputStream(localFile)
            
            val stats = transferFile(inputStream, outputStream, fileSize)
            lastTransferStats = stats

            withContext(Dispatchers.Main) { 
                if (targetDirectory == null) { // Only show toast for downloads, not temp files
                    toast(R.string.save_file_succeeded)
                    retrieveRemoteFilesSMB(currentRemotePath) // Only refresh list for actual downloads, not previews
                }
            }

            stats
        } catch (e: Exception) {
            Log.e(AiMouseSingleton.DEBUG_TAG, "Error downloading media file", e)
            withContext(Dispatchers.Main) {
                toast(R.string.save_file_error)
            }
            // Don't trigger reconnect for preview errors
            if (targetDirectory == null) {
                _smbState.value = SMBState.RECONNECT
            }
            null
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
                requestedFile?.close()
            } catch (e: Exception) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Error closing resources", e)
            }
            _isTransferringFileSMB = false
        }
    }

    override suspend fun uploadFileSMB(filePath: String, remotePath: String): TransferStats? {
        _isTransferringFileSMB = true
        var remoteFile: com.hierynomus.smbj.share.File? = null
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        return try {
            ensureConnected()

            diskShare?.let { share ->
                val localFile = JavaFile(filePath)
                if (!localFile.exists()) throw FileNotFoundException("File not found: ${localFile.path}")

                // Get just the filename from path
                val fileName = localFile.name
                val remoteFilePath = if (remotePath.isEmpty()) fileName
                                   else "$remotePath/$fileName"

                remoteFile = share.openFile(
                    remoteFilePath,
                    setOf(AccessMask.GENERIC_WRITE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null
                )

                inputStream = FileInputStream(localFile)
                outputStream = remoteFile?.outputStream

                val stats = transferFile(inputStream!!, outputStream!!, localFile.length())
                lastTransferStats = stats

                withContext(Dispatchers.Main) {
                    toast(R.string.upload_file_succeeded)
                }

                stats
            }
        } catch (e: Exception) {
            Log.e(AiMouseSingleton.DEBUG_TAG, "Error uploading file", e)
            withContext(Dispatchers.Main) { toast(R.string.upload_file_error) }
            _smbState.value = SMBState.RECONNECT
            null
        } finally {
            try {
                inputStream?.close()
                outputStream?.close() 
                remoteFile?.close()
            } catch (e: Exception) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Error closing resources", e)
            }
            _isTransferringFileSMB = false
        }
    }

    override suspend fun uploadFolderSMB(localFolderPath: String, remotePath: String): TransferStats? {
        _isTransferringFileSMB = true
        val openHandles = mutableListOf<AutoCloseable>()

        return try {
            ensureConnected()

            if (diskShare == null) throw RuntimeException("Disk share is null")

            val localFolder = JavaFile(localFolderPath)
            if (!localFolder.exists() || !localFolder.isDirectory) {
                withContext(Dispatchers.Main) { toast(R.string.upload_file_error) }
                return null
            }

            val folderName = localFolder.name
            // Create the folder in the specified remote path
            val remoteFolderPath = if (remotePath.isEmpty()) folderName else "$remotePath/$folderName"
            diskShare!!.mkdir(remoteFolderPath)

            val startTime = System.currentTimeMillis()
            var totalBytesCopied = 0L
            var maxSpeedMBps = 0.0

            localFolder.walkTopDown().forEach { file ->
                try {
                    if (file.isFile) {
                        // Calculate the relative path from the local folder
                        val localRelativePath = file.absolutePath.removePrefix(localFolder.parent + JavaFile.separator)
                        
                        // Construct the remote path by replacing the folder name with the remote folder path
                        val remoteFilePath = if (remotePath.isEmpty()) {
                            localRelativePath
                        } else {
                            // Replace the first part of the path (folder name) with the remote path + folder name
                            "$remotePath/$localRelativePath"
                        }
                        
                        val remoteFile = diskShare!!.openFile(
                            remoteFilePath,
                            setOf(AccessMask.GENERIC_WRITE),
                            null,
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OVERWRITE_IF,
                            null
                        )
                        openHandles.add(remoteFile)

                        val stats = FileInputStream(file).use { inputStream ->
                            remoteFile.outputStream.use { outputStream ->
                                transferFile(inputStream, outputStream, file.length())
                            }
                        }

                        totalBytesCopied += stats.fileSize
                        if (stats.maxSpeedMBps > maxSpeedMBps) maxSpeedMBps = stats.maxSpeedMBps

                        remoteFile.close()
                        openHandles.remove(remoteFile)
                    } else if (file.isDirectory && file != localFolder) {
                        // Calculate the relative path from the local folder
                        val localRelativePath = file.absolutePath.removePrefix(localFolder.parent + JavaFile.separator)
                        
                        // Construct the remote path by replacing the folder name with the remote folder path
                        val remoteDirPath = if (remotePath.isEmpty()) {
                            localRelativePath
                        } else {
                            // Replace the first part of the path (folder name) with the remote path + folder name
                            "$remotePath/$localRelativePath"
                        }
                        
                        diskShare!!.mkdir(remoteDirPath)
                    }
                } catch (e: Exception) {
                    Log.e(AiMouseSingleton.DEBUG_TAG, "Error uploading ${file.path}", e)
                }
            }

            val endTime = System.currentTimeMillis()
            val timeTakenSeconds = (endTime - startTime) / 1000.0
            val avgSpeedMBps = if (timeTakenSeconds > 0) (totalBytesCopied.toDouble() / (1024 * 1024)) / timeTakenSeconds else 0.0
            if (maxSpeedMBps < avgSpeedMBps) maxSpeedMBps = avgSpeedMBps

            withContext(Dispatchers.Main) {
                toast(R.string.upload_file_succeeded)
                // Refresh using the original remote path (where we were before the upload)
                retrieveRemoteFilesSMB(remotePath)
            }

            TransferStats(
                fileSize = totalBytesCopied,
                avgSpeedMBps = avgSpeedMBps,
                maxSpeedMBps = maxSpeedMBps,
                timeTakenSeconds = timeTakenSeconds
            )
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { toast(R.string.upload_file_error) }
            _smbState.value = SMBState.RECONNECT
            null
        } finally {
            openHandles.forEach { handle ->
                try {
                    handle.close()
                } catch (e: Exception) {
                    Log.e(AiMouseSingleton.DEBUG_TAG, "Error closing handle", e)
                }
            }
            _isTransferringFileSMB = false
            delay(500)
        }
    }

    override suspend fun downloadFolderSMB(remoteFolderName: String, targetDirectory: JavaFile?): TransferStats? {
        _isTransferringFileSMB = true
        return try {
            ensureConnected()

            val processedFolders = mutableSetOf<String>()
            val downloadDir = targetDirectory ?: JavaFile(localFileService.getCurrentFolderPath())
            
            // Extract the target folder name from the path
            val targetFolderName = remoteFolderName.split("/").last()
            
            val startTime = System.currentTimeMillis()
            var totalBytesCopied = 0L
            var maxSpeedMBps = 0.0

            suspend fun downloadFolderRecursively(folderPath: String, localBaseDir: JavaFile, isRootFolder: Boolean) {
                if (processedFolders.contains(folderPath)) return
                processedFolders.add(folderPath)

                val remoteFolder = diskShare!!.openDirectory(
                    folderPath,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                ) ?: throw RuntimeException()

                // For the root folder, create a folder with just the target name
                // For subfolders, create the relative path structure
                val localFolder = if (isRootFolder) {
                    JavaFile(localBaseDir, targetFolderName)
                } else {
                    // Get the relative path from the root folder
                    val relativePath = folderPath.substring(remoteFolderName.length + 1) // +1 for the slash
                    JavaFile(JavaFile(localBaseDir, targetFolderName), relativePath)
                }
                localFolder.mkdirs()

                val fileList = remoteFolder.list()

                fileList.forEach { fileInfo ->
                    val fileName = fileInfo.fileName
                    if (fileName == "." || fileName == "..") return@forEach

                    val isDir = EnumWithValue.EnumUtils.isSet(
                        fileInfo.fileAttributes,
                        FileAttributes.FILE_ATTRIBUTE_DIRECTORY
                    )

                    val remoteFilePath = if (folderPath.isEmpty()) fileName else "$folderPath/$fileName"

                    if (isDir) {
                        downloadFolderRecursively(remoteFilePath, localBaseDir, false)
                    } else {
                        val localFile = JavaFile(localFolder, fileName)
                        val remoteFile = diskShare!!.openFile(
                            remoteFilePath,
                            setOf(AccessMask.GENERIC_READ),
                            null,
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OPEN,
                            null
                        )

                        val fileSize = remoteFile.getFileInformation(FileStandardInformation::class.java).endOfFile
                        val stats = remoteFile.inputStream.use { inputStream ->
                            FileOutputStream(localFile).use { outputStream ->
                                transferFile(inputStream, outputStream, fileSize)
                            }
                        }

                        totalBytesCopied += stats.fileSize
                        if (stats.maxSpeedMBps > maxSpeedMBps) maxSpeedMBps = stats.maxSpeedMBps

                        remoteFile.close()
                    }
                }
                remoteFolder.close()
            }

            downloadFolderRecursively(remoteFolderName, downloadDir, true)

            val endTime = System.currentTimeMillis()
            val timeTakenSeconds = (endTime - startTime) / 1000.0
            val avgSpeedMBps = if (timeTakenSeconds > 0) (totalBytesCopied.toDouble() / (1024 * 1024)) / timeTakenSeconds else 0.0
            if (maxSpeedMBps < avgSpeedMBps) maxSpeedMBps = avgSpeedMBps

            withContext(Dispatchers.Main) {
                toast(R.string.save_file_succeeded)
            }

            TransferStats(
                fileSize = totalBytesCopied,
                avgSpeedMBps = avgSpeedMBps,
                maxSpeedMBps = maxSpeedMBps,
                timeTakenSeconds = timeTakenSeconds
            )
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { toast(R.string.save_file_error) }
            _smbState.value = SMBState.RECONNECT
            null
        } finally {
            _isTransferringFileSMB = false
        }
    }

    override fun updateSMBState(state: SMBState) {
        _smbState.value = state
    }

    private fun reconnect() {
        if (diskShare == null || !diskShare!!.isConnected) {
            closeSMB()
            connectSMB()
        }
    }

    private fun transferFile(
        inStream: InputStream,
        outStream: OutputStream,
        fileSize: Long
    ): TransferStats {
        _transferSpeed = "Transfer speed: %.2f MB/s".format(0.0)
        _transferProgress = 0f
        val buffer = ByteArray(5 * 1024 * 1024)
        var bytesRead: Int
        var totalBytesCopied = 0L
        var bytesSinceLastUpdate = 0L
        val updateInterval = 500L // Reduced from 1000ms to 500ms for more frequent updates
        var lastUpdateTime = System.currentTimeMillis()
        val startTime = System.currentTimeMillis()
        var maxSpeedMBps = 0.0
        
        try {
            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                outStream.write(buffer, 0, bytesRead)
                totalBytesCopied += bytesRead
                bytesSinceLastUpdate += bytesRead

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= updateInterval) {
                    val elapsedSeconds = (currentTime - lastUpdateTime) / 1000.0
                    val speedMBps = (bytesSinceLastUpdate.toDouble() / (1024 * 1024)) / elapsedSeconds
                    if (speedMBps > maxSpeedMBps) {
                        maxSpeedMBps = speedMBps
                    }

                    _transferSpeed = "Transfer speed: %.2f MB/s".format(speedMBps)
                    _transferProgress = (totalBytesCopied.toFloat() / fileSize)

                    bytesSinceLastUpdate = 0
                    lastUpdateTime = currentTime
                }

                // Add timeout check
                if (currentTime - startTime > 30 * 60 * 1000) { // 30 minutes timeout
                    throw IOException("Transfer timeout after 30 minutes")
                }
            }

            outStream.flush()
        } catch (e: Exception) {
            Log.e(AiMouseSingleton.DEBUG_TAG, "Error during file transfer", e)
            throw e
        }

        val endTime = System.currentTimeMillis()
        val timeTakenSeconds = (endTime - startTime) / 1000.0
        val avgSpeedMBps = if (timeTakenSeconds > 0) (totalBytesCopied.toDouble() / (1024 * 1024)) / timeTakenSeconds else 0.0

        if (maxSpeedMBps < avgSpeedMBps) {
            maxSpeedMBps = avgSpeedMBps
        }

        return TransferStats(
            fileSize = totalBytesCopied,
            avgSpeedMBps = avgSpeedMBps,
            maxSpeedMBps = maxSpeedMBps,
            timeTakenSeconds = timeTakenSeconds
        )
    }

    data class TransferStats(
        val fileSize: Long,
        val avgSpeedMBps: Double,
        val maxSpeedMBps: Double,
        val timeTakenSeconds: Double
    )

    private fun toast(@StringRes msgId: Int) {
        Toast.makeText(context, context.getString(msgId), Toast.LENGTH_SHORT).show()
    }
}