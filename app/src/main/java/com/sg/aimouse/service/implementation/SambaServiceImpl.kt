package com.sg.aimouse.service.implementation

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.RuntimeException
import java.io.File as JavaFile

class SambaServiceImpl(private val context: Context) : SambaService {
    private val host = "14.241.244.11"
    private val username = "admin"
    private val pwd = "trek2000"
    private val smbClient = SMBClient()
    private var connection: Connection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val _remoteFiles = mutableStateListOf<File>()

    override val remoteFiles: List<File>
        get() = _remoteFiles

    override fun connectSMB() {
        coroutineScope.launch {
            if (diskShare != null && _remoteFiles.isNotEmpty()) return@launch

            closeSMB()
            _remoteFiles.clear()

            try {
                connection = smbClient.connect(host)
                session = connection?.authenticate(
                    AuthenticationContext(username, pwd.toCharArray(), "")
                )
                diskShare = session?.connectShare("shared") as DiskShare

                getRemoteFiles()
            } catch (e: RuntimeException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to connect Samba", e)
            } catch (e: IOException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to connect Samba", e)
            }
        }
    }

    override fun closeSMB() {
        try {
            diskShare?.close()
            session?.close()
            connection?.close()
        } catch (e: Exception) {
            Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to close Samba connection")
            e.printStackTrace()
        }
    }

    override suspend fun getRemoteFiles(folderName: String) {
        try {
            diskShare?.apply {
                val remoteFolder = openDirectory(
                    "",
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )

                if (remoteFolder == null) return

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
            Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to get remote files")
            e.printStackTrace()
        }
    }

    override fun uploadFile(fileName: String) {
        coroutineScope.launch {
            try {
                diskShare?.apply {
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

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Upload file successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to upload file",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                e.printStackTrace()
            }
        }
    }

    override fun downloadFile(fileName: String) {
        coroutineScope.launch {
            try {
                diskShare?.apply {
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

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.save_file_succeeded),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.save_file_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                e.printStackTrace()
            }
        }
    }
}