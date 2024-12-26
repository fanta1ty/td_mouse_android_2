package com.sg.aimouse.service.implementation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sg.aimouse.R
import com.sg.aimouse.common.AiMouseSingleton
import com.sg.aimouse.model.File
import com.sg.aimouse.model.Request
import com.sg.aimouse.model.Response
import com.sg.aimouse.service.BluetoothResponseType
import com.sg.aimouse.service.BluetoothService
import com.sg.aimouse.service.BluetoothState
import com.sg.aimouse.service.CommandType
import com.sg.aimouse.service.FileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.text.toByteArray
import java.io.File as JavaFile

@SuppressLint("MissingPermission")
class BluetoothServiceImpl(
    private val context: Context
) : BluetoothService, FileService by FileServiceImpl() {

    //region Fields
    private val uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee")
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var connectJob: Job? = null
    private var connectedJob: Job? = null
    private var socket: BluetoothSocket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private var selectedTDMouseFile: File? = null
    private var responseType = BluetoothResponseType.JSON

    private val bluetoothStateMutex = Mutex()
    private val _bluetoothState = MutableStateFlow(BluetoothState.DISCONNECTED)
    override val bluetoothState: StateFlow<BluetoothState>
        get() = _bluetoothState.asStateFlow()

    private val _tdMouseFiles = mutableStateListOf<File>()
    override val tdMouseFiles: List<File>
        get() = _tdMouseFiles

    private var _isTransferringFile by mutableStateOf(false)
    override val isTransferringFile: Boolean
        get() = _isTransferringFile
    //endregion

    override fun isBluetoothEnabled() = adapter.isEnabled

    override fun isBluetoothDeviceDetected() = !adapter.bondedDevices.isEmpty()

    override fun connectBluetooth() {
        connectJob?.cancel()
        connectJob = coroutineScope.launch {
            val devices = adapter.bondedDevices.toList()
            updateBluetoothState(BluetoothState.CONNECTING)

            for (device in devices) {
                socket = try {
                    device.createRfcommSocketToServiceRecord(uuid)
                } catch (_: IOException) {
                    null
                }

                if (socket != null) break
            }

            adapter.cancelDiscovery()
            if (socket == null) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to create RFCOMM socket")
                this@BluetoothServiceImpl.cancel()
                return@launch
            }

            try {
                socket!!.connect()
                withContext(Dispatchers.Main) { connected(socket!!) }
            } catch (e: IOException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to connect Bluetooth", e)
                this@BluetoothServiceImpl.cancel()
            }
        }
    }

    override fun closeBluetoothConnection(isRelease: Boolean) {
        connectJob?.cancel()
        connectedJob?.cancel()

        if (isRelease) {
            coroutineScope.cancel()
        }
    }

    override fun sendBluetoothCommand(
        commandType: CommandType,
        file: File?,
        responseType: BluetoothResponseType
    ) {
        this.responseType = responseType

        coroutineScope.launch {
            when (commandType) {
                CommandType.LIST_FILE -> {
                    val cmd = Request("filelist").toJSON()
                    outStream?.write(cmd.toByteArray())
                }

                CommandType.SEND_FILE_TRANSFERJET -> {
                    val cmd = Request("receive_TXJfile").toJSON()
                    outStream?.write(cmd.toByteArray())
                }

                CommandType.RECEIVE_FILE_TRANSFERJET -> {
                    if (file == null) return@launch
                    selectedTDMouseFile = file
                    val cmd = Request("sendfile", file.fileName).toJSON()
                    outStream?.write(cmd.toByteArray())
                }

                CommandType.RECEIVE_FILE_BLUETOOTH -> {
                    if (file == null) return@launch
                    _isTransferringFile = true
                    selectedTDMouseFile = file
                    val cmd = Request("send_BTfile", file.fileName).toJSON()
                    outStream?.write(cmd.toByteArray())
                }
            }
        }
    }

    override fun sendFileViaBluetooth(file: File) {
        coroutineScope.launch {
            _isTransferringFile = true
            val cmd = Request("receive_BTfile", file.fileName, file.size).toJSON()
            outStream?.write(cmd.toByteArray())


            delay(5000)

            try {
                val javaFile = JavaFile(file.path)
                val data = javaFile.readBytes()
                outStream?.write(data)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "File sent successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "File failed to send", Toast.LENGTH_SHORT).show()
                }
            }

            _isTransferringFile = false
        }
    }

    private fun connected(socket: BluetoothSocket) {
        connectedJob?.cancel()
        connectedJob = coroutineScope.launch {

            try {
                inStream = socket.inputStream
                outStream = socket.outputStream
            } catch (e: IOException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to open stream", e)
                this@BluetoothServiceImpl.cancel()
                return@launch
            }

            updateBluetoothState(BluetoothState.CONNECTED)
            val buffer = ByteArray(2 * 1024 * 1024)
            var combinedBuffer = byteArrayOf()

            while (_bluetoothState.value == BluetoothState.CONNECTED) {
                try {
                    val byteCount = inStream?.read(buffer) ?: 0

                    if (byteCount > 0) {
                        if (responseType == BluetoothResponseType.FILE) {
                            val tempBuffer = ByteArray(byteCount)
                            buffer.copyInto(
                                destination = tempBuffer, startIndex = 0, endIndex = byteCount
                            )
                            combinedBuffer += tempBuffer

                            if (combinedBuffer.size.toLong() == selectedTDMouseFile!!.size) {
                                saveFile(context, combinedBuffer, selectedTDMouseFile!!.fileName)
                                combinedBuffer = byteArrayOf()

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.save_file_succeeded),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                _isTransferringFile = false
                            }
                        } else {
                            val msg = String(buffer, Charsets.UTF_8).substring(0..byteCount - 1)
                            if (msg == "EOJSON") {
                                val json = String(combinedBuffer, Charsets.UTF_8)

                                val response = Response.fromJSON(json)

                                val files = mutableListOf<File>()
                                files.addAll(response.folders.map {
                                    File(
                                        it,
                                        isDirectory = true
                                    )
                                })
                                files.addAll(response.files.map { File(it.name, it.size) })

                                withContext(Dispatchers.Main) {
                                    this@BluetoothServiceImpl._tdMouseFiles.clear()
                                    this@BluetoothServiceImpl._tdMouseFiles.addAll(files)
                                }

                                combinedBuffer = byteArrayOf()
                            } else {
                                val tempBuffer = ByteArray(byteCount)
                                buffer.copyInto(
                                    destination = tempBuffer, startIndex = 0, endIndex = byteCount
                                )
                                combinedBuffer += tempBuffer
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e(AiMouseSingleton.DEBUG_TAG, "Input stream failed", e)
                    this@BluetoothServiceImpl.cancel()
                }
            }
        }
    }

    private suspend fun cancel() {
        try {
            inStream?.close()
            outStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to close socket", e)
        }

        updateBluetoothState(BluetoothState.DISCONNECTED)
    }

    private suspend fun updateBluetoothState(newState: BluetoothState) {
        bluetoothStateMutex.withLock { _bluetoothState.value = newState }
    }
}