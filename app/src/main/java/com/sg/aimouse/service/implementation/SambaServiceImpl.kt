package com.sg.aimouse.service.implementation

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.RuntimeException
import java.io.File as JavaFile

enum class SMBState {
    RECONNECT, CONNECTING, CONNECTED, DISCONNECTED
}

class SambaServiceImpl(private val context: Context) : SambaService {

    //region Fields
    private val host = "14.241.244.11"
    private val username = "admin"
    private val pwd = "trek2000"
    private val smbClient = SMBClient()
    private var connection: Connection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _smbState = MutableStateFlow(SMBState.DISCONNECTED)
    override val smbState: StateFlow<SMBState>
        get() = _smbState.asStateFlow()

    private val _remoteFiles = mutableStateListOf<File>()
    override val remoteFiles: List<File>
        get() = _remoteFiles

    private var _isTransferringFileSMB by mutableStateOf(false)
    override val isTransferringFileSMB: Boolean
        get() = _isTransferringFileSMB
    //endregion

    init {
        coroutineScope.launch(Dispatchers.Main) {
            smbState.collect { state ->
                when (state) {
                    SMBState.CONNECTED -> retrieveRemoteFilesSMB()
                    SMBState.CONNECTING -> Unit
                    SMBState.RECONNECT -> reconnect()
                    SMBState.DISCONNECTED -> closeSMB()
                }
            }
        }
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
                diskShare = session?.connectShare("shared") as DiskShare

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
                        SMB2CreateDisposition.FILE_CREATE,
                        null
                    )

                    FileInputStream(localFile).use { inputStream ->
                        remoteFile.outputStream.use { outputStream ->
                            inputStream.copyTo(outputStream)
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
                        requestedFile.inputStream.use { inStream -> inStream.copyTo(outStream) }
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

    private fun reconnect() {
        if (diskShare == null || !diskShare!!.isConnected) {
            closeSMB()
            connectSMB()
        }
    }

    private fun toast(@StringRes msgId: Int) {
        Toast.makeText(context, context.getString(msgId), Toast.LENGTH_SHORT).show()
    }
}