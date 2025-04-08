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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
                        folderName,
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

    var lastTransferStats: TransferStats? = null
        private set

    override suspend fun uploadFileSMB(fileName: String): TransferStats? {
        _isTransferringFileSMB = true
        var remoteFile: com.hierynomus.smbj.share.File? = null

        return try {
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

                val stats = FileInputStream(localFile).use { inputStream ->
                    remoteFile?.outputStream?.use { outputStream ->
                        transferFile(inputStream, outputStream, localFile.length())
                    }
                }
                lastTransferStats = stats

                remoteFile?.close()
                remoteFile = null

                withContext(Dispatchers.Main) {
                    toast(R.string.upload_file_succeeded)
                }

                stats
            }
        } catch (e: Exception) {
            remoteFile?.close()
            e.printStackTrace()
            withContext(Dispatchers.Main) { toast(R.string.upload_file_error) }
            _smbState.value = SMBState.RECONNECT
            null
        } finally {
            _isTransferringFileSMB = false
        }
    }

    override suspend fun downloadFileSMB(fileName: String): TransferStats? {
        _isTransferringFileSMB = true
        _transferSpeed = "Transfer speed: %.2f MB/s".format(0.0)
        _transferProgress = 0f

        return try {
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

            val inputStream = requestedFile.inputStream
            val outputStream = FileOutputStream(localFile)
            val stats = transferFile(inputStream, outputStream, fileSize)
            lastTransferStats = stats

            inputStream.close()
            outputStream.close()
            requestedFile.close()

            withContext(Dispatchers.Main) { toast(R.string.save_file_succeeded) }

            stats

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { toast(R.string.save_file_error) }
            _smbState.value = SMBState.RECONNECT
            null
        } finally {
            _isTransferringFileSMB = false
        }
    }

    override suspend fun uploadFolderSMB(localFolderPath: String): TransferStats? {
        _isTransferringFileSMB = true
        val openHandles = mutableListOf<AutoCloseable>()

        return try {
            if (diskShare == null) throw RuntimeException("Disk share is null")

            val localFolder = JavaFile(localFolderPath)
            if (!localFolder.exists() || !localFolder.isDirectory) {
                withContext(Dispatchers.Main) { toast(R.string.upload_file_error) }
                return null
            }

            val relativePath = localFolder.name
            diskShare!!.mkdir(relativePath)

            val startTime = System.currentTimeMillis()
            var totalBytesCopied = 0L
            var maxSpeedMBps = 0.0

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
                        val dirPath = file.absolutePath.removePrefix(localFolder.parent + JavaFile.separator)
                        diskShare!!.mkdir(dirPath)
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
                retrieveRemoteFilesSMB()
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

    override suspend fun downloadFolderSMB(remoteFolderName: String): TransferStats? {
        _isTransferringFileSMB = true
        return try {
            if (diskShare == null) throw RuntimeException("Disk share is null")

            val processedFolders = mutableSetOf<String>()
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            val startTime = System.currentTimeMillis()
            var totalBytesCopied = 0L
            var maxSpeedMBps = 0.0

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
                ) ?: throw RuntimeException("Cannot open directory: $folderPath")

                val localFolder = JavaFile(localBaseDir, folderPath)
                localFolder.mkdirs()

                val fileList = remoteFolder.list()

                fileList.forEach { fileInfo ->
                    val fileName = fileInfo.fileName
                    if (fileName == "." || fileName == "..") return@forEach

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

            downloadFolderRecursively(remoteFolderName, downloadDir)

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
        val updateInterval = 1000L // milliseconds
        var lastUpdateTime = System.currentTimeMillis()
        val startTime = System.currentTimeMillis()
        var maxSpeedMBps = 0.0

        while (inStream.read(buffer).also { bytesRead = it } != -1) {
            outStream.write(buffer, 0, bytesRead)
            totalBytesCopied += bytesRead
            bytesSinceLastUpdate += bytesRead

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime >= updateInterval) {
                val intervalMillis = currentTime - lastUpdateTime
                val speedBytesPerSec = (bytesSinceLastUpdate * 1000) / intervalMillis
                val speedMBps = speedBytesPerSec.toDouble() / (1024 * 1024)
                _transferSpeed = "Transfer speed: %.2f MB/s".format(speedMBps)
                _transferProgress = totalBytesCopied.toFloat() / fileSize

                if (speedMBps > maxSpeedMBps) maxSpeedMBps = speedMBps

                bytesSinceLastUpdate = 0
                lastUpdateTime = currentTime
            }
        }

        outStream.flush()

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