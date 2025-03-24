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
import com.hierynomus.mssmb2.SMB2Dialect
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.RuntimeException
import java.util.EnumSet
import java.io.File as JavaFile

enum class SMBState {
    RECONNECT, CONNECTING, CONNECTED, DISCONNECTED
}

class SambaServiceImpl(private val context: Context) : SambaService {

    //region Fields
    private val host = "14.241.244.11"
//    private val host = "192.168.1.32"

//    private val host = "172.20.10.2"
//    private val host = "192.168.54.121"

    private val username = "smbuser"
    private val pwd = "123456"
    private val rootDir = "sambashare"

    private val smbClient = SMBClient(
        SmbConfig.builder()
            .withDialects(SMB2Dialect.SMB_2_1)
            .withSigningEnabled(false)
            .withSigningRequired(false)
            .withSoTimeout(0)
            .withBufferSize(5 * 1024 * 1024)
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

    private val _selectedRemoteFiles = mutableMapOf<String, File>()
    override val selectedRemoteFiles: Map<String, File>
        get() = _selectedRemoteFiles

    private var _isTransferringFileSMB by mutableStateOf(false)
    override val isTransferringFileSMB: Boolean
        get() = _isTransferringFileSMB

    private var _transferSpeed by mutableStateOf("")
    override val transferSpeed: String
        get() = _transferSpeed

    private var _transferProgress by mutableFloatStateOf(0f)
    override val transferProgress: Float
        get() = _transferProgress

    private val _currentPath = mutableStateListOf<String>()
    override val currentPath: List<String>
        get() = _currentPath
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

    override fun addSelectedRemoteFile(file: File) {
        _selectedRemoteFiles.put(file.fileName, file)
    }

    override fun removeSelectedRemoteFile(fileName: String) {
        _selectedRemoteFiles.remove(fileName)
    }

    override fun appendPath(folderName: String) {
        _currentPath.add(folderName)
        retrieveRemoteFilesSMB()
    }

    override fun removePath() {
        _currentPath.removeAt(currentPath.size - 1)
        retrieveRemoteFilesSMB()
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
            } catch (e: RuntimeException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.td_mouse_connection_error) }
                _smbState.value = SMBState.DISCONNECTED
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.td_mouse_connection_error) }
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

    override fun retrieveRemoteFilesSMB() {
        coroutineScope.launch {
            try {
                _remoteFiles.clear()
                if (diskShare == null) throw RuntimeException()

                diskShare!!.apply {
                    val remoteFolder = openDirectory(
                        currentPath.toPath(),
                        setOf(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    ) ?: throw RuntimeException()

                    val files = remoteFolder.filter { fileInfo ->
                        // filter hidden files
                        !EnumWithValue.EnumUtils.isSet(
                            fileInfo.fileAttributes,
                            FileAttributes.FILE_ATTRIBUTE_HIDDEN
                        )
                    }.filter { fileInfo ->
                        // filter hidden folders
                        !fileInfo.fileName.all { char -> char == '.' }
                    }.map { fileInfo ->
                        val isDir = EnumWithValue.EnumUtils.isSet(
                            fileInfo.fileAttributes,
                            FileAttributes.FILE_ATTRIBUTE_DIRECTORY
                        )

                        File(fileInfo.fileName, fileInfo.endOfFile, isDirectory = isDir)
                    }

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

            try {
                if (diskShare == null) throw RuntimeException()

                diskShare!!.apply {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )

                    val localFile = JavaFile(downloadDir, fileName)

                    val remoteFile = openFile(
                        fileName,
                        setOf(AccessMask.GENERIC_WRITE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OVERWRITE_IF,
                        null
                    )

                    FileInputStream(localFile).use { inputStream ->
                        remoteFile.outputStream.use { outputStream ->
                            transferFile(inputStream, outputStream, localFile.length())
                        }
                    }

                    withContext(Dispatchers.Main) { toast(R.string.upload_file_succeeded) }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.upload_file_error) }
                _smbState.value = SMBState.RECONNECT
            } catch (e: RuntimeException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.upload_file_error) }
                _smbState.value = SMBState.RECONNECT
            }

            _isTransferringFileSMB = false
        }
    }

    override fun downloadFileSMB(fileName: String) {
        coroutineScope.launch {
            _isTransferringFileSMB = true

            try {
                if (diskShare == null) throw RuntimeException()

                diskShare!!.apply {
                    val requestedFile = openFile(
                        fileName,
                        setOf(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )

                    val downloadDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )

                    FileOutputStream(JavaFile(downloadDir, fileName)).use { outStream ->
                        requestedFile.inputStream.use { inStream ->
                            val fileSize = requestedFile.getFileInformation(
                                FileStandardInformation::class.java
                            ).endOfFile

                            transferFile(inStream, outStream, fileSize)
                        }
                    }

                    withContext(Dispatchers.Main) { toast(R.string.save_file_succeeded) }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.save_file_error) }
                _smbState.value = SMBState.RECONNECT
            } catch (e: RuntimeException) {
                withContext(Dispatchers.Main) { toast(R.string.save_file_error) }
                _smbState.value = SMBState.RECONNECT
                e.printStackTrace()
            }

            _isTransferringFileSMB = false
        }
    }

    override fun deleteFilesSMB(fileNames: List<String>) {
        coroutineScope.launch {
            try {
                if (diskShare == null) throw RuntimeException()

                diskShare!!.apply {
                    fileNames.forEach { fileName ->
                        val path = if (currentPath.isEmpty())
                            fileName
                        else
                            currentPath.toPath(fileName)

                        if (fileExists(path)) {
                            val file = openFile(
                                path,
                                EnumSet.of(AccessMask.DELETE),
                                null,
                                SMB2ShareAccess.ALL,
                                SMB2CreateDisposition.FILE_OPEN,
                                null
                            )

                            file.close()
                            rm(path)
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.remove_remote_file_error) }
                _smbState.value = SMBState.RECONNECT
            } catch (e: RuntimeException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { toast(R.string.remove_remote_file_error) }
                _smbState.value = SMBState.RECONNECT
            } finally {
                retrieveRemoteFilesSMB()
            }
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

    private fun List<String>.toPath(fileName: String = ""): String {
        return if (fileName.isNotEmpty())
            this.joinToString("/") + "/" + fileName
        else
            this.joinToString("/")
    }
}