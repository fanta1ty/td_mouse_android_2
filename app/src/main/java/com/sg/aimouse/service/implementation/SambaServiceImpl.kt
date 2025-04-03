package com.sg.aimouse.service.implementation

import android.content.Context
import android.os.Environment
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
import com.sg.aimouse.util.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.io.File as JavaFile

enum class SMBState {
    RECONNECT, CONNECTING, CONNECTED, DISCONNECTED
}

class SambaServiceImpl(internal val context: Context) : SambaService {

    //region Fields
    private var host: String = ""
    private var username: String = ""
    private var pwd: String = ""
    private var rootDir: String = ""

    private val smbClient = SMBClient(
        SmbConfig.builder()
            .withDialects(SMB_2_1)
            .withSigningEnabled(false)
//            .withSigningRequired(false)
//            .withSoTimeout(0)
            .withBufferSize(16 * 1024 * 1024)
            .build()
    )
    private var connection: Connection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

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
    //endregion

    init {
        coroutineScope.launch(Dispatchers.Main) {
            _smbState.collect { state ->
                when (state) {
                    SMBState.CONNECTED -> retrieveRemoteFilesSMB()
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
                diskShare?.let { share ->
                    if (share.fileExists(fileName)) {
                        share.rm(fileName)
                    } else if (share.folderExists(fileName)) {
                        share.rmdir(fileName, true) // Xóa đệ quy
                    }
                    withContext(Dispatchers.Main) {
                        toast(R.string.delete_file_succeeded)
                        // Refresh file list when upload/download complete
                        retrieveRemoteFilesSMB()
                    }
                } ?: throw RuntimeException("Disk share is null")
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.delete_file_error) }
                _smbState.value = SMBState.RECONNECT
            }
        }
    }

    fun isConnected(): Boolean {
        return _smbState.value == SMBState.CONNECTED
    }

    override fun connectSMB() {
        if (!isNetworkAvailable(context)) {
            toast(R.string.no_internet)
            _smbState.value = SMBState.DISCONNECTED
            return
        }

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
//                withContext(Dispatchers.Main) { toast(R.string.td_mouse_connection_error) }
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to connect to TD Mouse", e)
                _smbState.value = SMBState.DISCONNECTED
            }
        }
    }

    override fun closeSMB(isRelease: Boolean) {
        coroutineScope.launch {
            try {
                diskShare?.close()
                session?.close()
                connection?.close()
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
                if (diskShare == null) throw RuntimeException()

                diskShare!!.apply {
                    val remoteFolder = openDirectory(
                        "",
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
                    }.sortedByDescending { it.createdTime }

                    _remoteFiles.addAll(files)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) { toast(R.string.retrieve_remote_file_error) }
                e.printStackTrace()
                _smbState.value = SMBState.RECONNECT
            } catch (e: RuntimeException) {
                withContext(Dispatchers.Main) { toast(R.string.retrieve_remote_file_error) }
                e.printStackTrace()
                _smbState.value = SMBState.RECONNECT
            }
        }
    }

    override fun uploadFileSMB(fileName: String) {
        coroutineScope.launch {
            _isTransferringFileSMB = true
            var remoteFile: com.hierynomus.smbj.share.File? = null

            try {
                diskShare?.let { share ->
                    val downloadDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    val localFile = JavaFile(downloadDir, fileName)

                    remoteFile = share.openFile(
                        fileName,
                        setOf(AccessMask.GENERIC_WRITE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OVERWRITE_IF,
                        null
                    )

                    FileInputStream(localFile).use { inputStream ->
                        remoteFile?.outputStream?.use { outputStream ->
                            transferFile(inputStream, outputStream, localFile.length())
                        }
                    }

                    remoteFile?.close()
                    remoteFile = null

                    withContext(Dispatchers.Main) {
                        toast(R.string.upload_file_succeeded)
                    }
                }
            } catch (e: Exception) {
                remoteFile?.close()
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    toast(R.string.upload_file_error)
                }
                _smbState.value = SMBState.RECONNECT
            } finally {
                _isTransferringFileSMB = false
            }
        }
    }

    override fun downloadFileSMB(fileName: String) {
        coroutineScope.launch(Dispatchers.IO) { // Ensure this runs fully in the background
            _isTransferringFileSMB = true
            _transferSpeed = "Transfer speed: %.2f MB/s".format(0.0)
            _transferProgress = 0f

            try {
                if (diskShare == null) throw RuntimeException("Disk share is null")

                val requestedFile = diskShare!!.openFile(
                    fileName,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )

                val fileSize = requestedFile.getFileInformation(FileStandardInformation::class.java).endOfFile
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val localFile = JavaFile(downloadDir, fileName)

                val chunkSize = if (fileSize > 100 * 1024 * 1024) 16 * 1024 * 1024 else 8 * 1024 * 1024 // **Dynamic buffer size**
                val buffer = ByteArray(chunkSize)

                val inputStream = requestedFile.inputStream
                val randomAccessFile = RandomAccessFile(localFile, "rw")

                var totalBytesRead = 0L
                var bytesRead: Int
                val startTime = System.currentTimeMillis()

                // CoroutineFlow for Smooth Progress Updates**
                val progressFlow = flow {
                    while (true) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break

                        val writeJob = async(Dispatchers.IO) {
                            synchronized(randomAccessFile) {
                                randomAccessFile.seek(totalBytesRead)
                                randomAccessFile.write(buffer, 0, bytesRead)
                            }
                        }

                        totalBytesRead += bytesRead

                        emit(totalBytesRead) // Emit progress
                        writeJob.await() // Ensure writing happens in parallel

                        delay(500) // **Update UI every 500ms**
                    }
                }.flowOn(Dispatchers.IO)

                // Collect Progress Smoothly on UI Thread**
                progressFlow.collect { bytesReadSoFar ->
                    _transferProgress = bytesReadSoFar.toFloat() / fileSize

                    // **Calculate Speed**
                    val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
                    if (elapsedTime > 0) {
                        val speedMBps = (bytesReadSoFar / 1024.0 / 1024.0) / elapsedTime
                        _transferSpeed = "Speed: %.2f MB/s".format(speedMBps)
                    }
                }

                // Cleanup
                randomAccessFile.close()
                inputStream.close()
                requestedFile.close()

                withContext(Dispatchers.Main) { toast(R.string.save_file_succeeded) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.save_file_error) }
                _smbState.value = SMBState.RECONNECT
            }

            _isTransferringFileSMB = false
        }
    }

    override fun uploadFolderSMB(localFolderPath: String) {
        coroutineScope.launch(Dispatchers.IO) {
            _isTransferringFileSMB = true
            val openHandles = mutableListOf<AutoCloseable>()

            try {
                if (diskShare == null) throw RuntimeException("Disk share is null")

                val localFolder = JavaFile(localFolderPath)
                if (!localFolder.exists() || !localFolder.isDirectory) {
                    withContext(Dispatchers.Main) { toast(R.string.upload_file_error) }
                    return@launch
                }

                val relativePath = localFolder.name
                diskShare!!.mkdir(relativePath)

                localFolder.walkTopDown().forEach { file ->
                    try {
                        if (file.isFile) {
                            val remotePath = file.absolutePath.removePrefix(localFolder.parent + JavaFile.separator)
                            val remoteFile = diskShare!!.openFile(
                                remotePath,
                                setOf(AccessMask.GENERIC_WRITE),
                                null,
                                SMB2ShareAccess.ALL,
                                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                                null
                            )
                            openHandles.add(remoteFile)

                            FileInputStream(file).use { inputStream ->
                                remoteFile.outputStream.use { outputStream ->
                                    transferFile(inputStream, outputStream, file.length())
                                }
                            }

                            // Close file after upload
                            remoteFile.close()
                            openHandles.remove(remoteFile)
                        } else if (file.isDirectory && file != localFolder) {
                            val dirPath = file.absolutePath.removePrefix(localFolder.parent + JavaFile.separator)
                            diskShare!!.mkdir(dirPath)
                        }
                    } catch (e: Exception) {
                        Log.e(AiMouseSingleton.DEBUG_TAG, "Error uploading ${file.path}", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    toast(R.string.upload_file_succeeded)
                    retrieveRemoteFilesSMB()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.upload_file_error) }
                _smbState.value = SMBState.RECONNECT
            } finally {
                // Close all open handles
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
    }

    override fun downloadFolderSMB(remoteFolderName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            _isTransferringFileSMB = true
            try {
                if (diskShare == null) throw RuntimeException("Disk share is null")

                val processedFolders = mutableSetOf<String>()

                suspend fun downloadFolderRecursively(folderPath: String, localBaseDir: JavaFile) {
                    if (processedFolders.contains(folderPath)) return
                    processedFolders.add(folderPath)

                    val remoteFolder = diskShare!!.openDirectory(
                        folderPath,
                        setOf(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    ) ?: run {
                        throw RuntimeException("Cannot open directory: $folderPath")
                    }

                    val localFolder = JavaFile(localBaseDir, folderPath)
                    localFolder.mkdirs()

                    val fileList = remoteFolder.list()

                    fileList.forEach { fileInfo ->
                        val fileName = fileInfo.fileName
                        if (fileName == "." || fileName == "..") {
                            return@forEach
                        }

                        val isDir = EnumWithValue.EnumUtils.isSet(fileInfo.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
                        val remoteFilePath = if (folderPath.isEmpty()) fileName else "$folderPath${JavaFile.separator}$fileName"

                        if (isDir) {
                            downloadFolderRecursively(remoteFilePath, localBaseDir)
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

                            val inputStream = remoteFile.inputStream
                            val outputStream = FileOutputStream(localFile)

                            transferFile(inputStream, outputStream, fileSize)

                            inputStream.close()
                            outputStream.close()
                            remoteFile.close()
                        }
                    }
                    remoteFolder.close()
                }

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                downloadFolderRecursively(remoteFolderName, downloadDir)

                withContext(Dispatchers.Main) {
                    toast(R.string.save_file_succeeded)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.save_file_error) }
                _smbState.value = SMBState.RECONNECT
            }
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
    ) {
        _transferSpeed = "Transfer speed: %.2f MB/s".format(0.0)
        _transferProgress = 0f
        val buffer = ByteArray(5 * 1024 * 1024)
        var bytesRead: Int
        var totalBytesCopied = 0L

        // For speed calculation every second (1000 ms)
        var bytesSinceLastUpdate = 0L
        val updateInterval = 1000L  // milliseconds
        var lastUpdateTime = System.currentTimeMillis()

        while (inStream.read(buffer).also { bytesRead = it } != -1) {
            outStream.write(buffer, 0, bytesRead)
            totalBytesCopied += bytesRead
            bytesSinceLastUpdate += bytesRead

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime >= updateInterval) {
                // Calculate speed in bytes per second.
                val intervalMillis = currentTime - lastUpdateTime
                val speedBytesPerSec = (bytesSinceLastUpdate * 1000) / intervalMillis

                // Convert to MB/s (megabytes per second) using 1024 * 1024 conversion.
                val speedMBps = speedBytesPerSec.toDouble() / (1024 * 1024)
                _transferSpeed = "Transfer speed: %.2f MB/s".format(speedMBps)

                // Calculate the progress percentage.
                _transferProgress = totalBytesCopied.toFloat() / fileSize

                // Reset the counter for the next interval.
                bytesSinceLastUpdate = 0
                lastUpdateTime = currentTime
            }
        }

        outStream.flush()
    }

    private fun toast(@StringRes msgId: Int) {
        Toast.makeText(context, context.getString(msgId), Toast.LENGTH_SHORT).show()
    }
}