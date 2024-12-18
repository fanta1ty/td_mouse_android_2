package com.sg.aimouse.service.implementation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.sg.aimouse.common.AiMouseSingleton
import com.sg.aimouse.model.File
import com.sg.aimouse.model.Request
import com.sg.aimouse.model.Response
import com.sg.aimouse.service.BluetoothService
import com.sg.aimouse.service.BluetoothState
import com.sg.aimouse.service.CommandType
import com.sg.aimouse.service.FileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    private var selectedFileName = ""

    private val bluetoothStateMutex = Mutex()
    private val _bluetoothState = MutableStateFlow(BluetoothState.DISCONNECTED)
    override val bluetoothState: StateFlow<BluetoothState>
        get() = _bluetoothState.asStateFlow()

    private val _tdMouseFiles = mutableStateListOf<File>()
    override val tdMouseFiles: List<File>
        get() = _tdMouseFiles
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

    override fun sendBluetoothCommand(commandType: CommandType, fileName: String) {
        val cmd = when (commandType) {
            CommandType.LIST_FILE -> Request("filelist").toJSON()
            CommandType.RECEIVE_FILE_TRANSFERJET -> Request("sendfile", fileName).toJSON()
            CommandType.RECEIVE_FILE_BLUETOOTH -> {
                selectedFileName = fileName
                Request("send_BTfile", fileName).toJSON()
            }
        }

        Log.d(AiMouseSingleton.DEBUG_TAG, "bluetooth write: $cmd")
        outStream?.write(cmd.toByteArray())
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
            val buffer = ByteArray(1024)
            var combinedBuffer = byteArrayOf()

            while (_bluetoothState.value == BluetoothState.CONNECTED) {
                try {
                    val byteCount = inStream?.read(buffer) ?: 0

                    if (byteCount > 0) {
                        val msg = String(buffer, Charsets.UTF_8).substring(0..byteCount - 1)
                        //Log.d(AiMouseSingleton.DEBUG_TAG, "msg: $msg")

                        if (msg == "EOF") {
                            saveFile(context, combinedBuffer, selectedFileName)
                            combinedBuffer = byteArrayOf()
                        } else if (msg == "EOJSON") {
                            val json = String(combinedBuffer, Charsets.UTF_8)
                            val response = Response.fromJSON(json)

                            val files = mutableListOf<File>()
                            files.addAll(response.folders.map { File(it, isDirectory = true) })
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