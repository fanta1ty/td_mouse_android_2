package com.sg.aimouse.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.sg.aimouse.common.AiMouseSingleton
import com.sg.aimouse.model.File
import com.sg.aimouse.model.Response
import com.sg.aimouse.service.implementation.FileServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class BluetoothState {
    CONNECTING, CONNECTED, DISCONNECTED
}

enum class CommandType {
    LIST_FILE, RECEIVE_FILE_TRANSFERJET, RECEIVE_FILE_BLUETOOTH
}

@SuppressLint("MissingPermission")
class BluetoothService(private val context: Context) : FileService by FileServiceImpl() {
    private val uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee")
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var connectJob: Job? = null
    private var connectedJob: Job? = null
    private var socket: BluetoothSocket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private val bluetoothStateMutex = Mutex()
    private val bluetoothState = MutableStateFlow(BluetoothState.DISCONNECTED)
    private val files = mutableStateListOf<File>()

    fun connect() {
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
                this@BluetoothService.cancel()
                return@launch
            }

            try {
                socket!!.connect()
                withContext(Dispatchers.Main) { connected(socket!!) }
            } catch (e: IOException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to connect Bluetooth", e)
                this@BluetoothService.cancel()
            }
        }
    }

    fun close(isRelease: Boolean = false) {
        connectJob?.cancel()
        connectedJob?.cancel()

        if (isRelease) {
            coroutineScope.cancel()
        }
    }

    fun sendCommand(cmd: String) {
        Log.d(AiMouseSingleton.DEBUG_TAG, "write cmd:: $cmd")
        outStream?.write(cmd.toByteArray())
    }

    fun isBluetoothEnable() = adapter.isEnabled

    fun isBluetoothDeviceDetected() = !adapter.bondedDevices.isEmpty()

    fun getBluetoothState() = bluetoothState.asStateFlow()

    fun getFiles(): List<File> = files

    private fun connected(socket: BluetoothSocket) {
        connectedJob?.cancel()
        connectedJob = coroutineScope.launch {

            try {
                inStream = socket.inputStream
                outStream = socket.outputStream
            } catch (e: IOException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to open stream", e)
                this@BluetoothService.cancel()
                return@launch
            }

            updateBluetoothState(BluetoothState.CONNECTED)
            val buffer = ByteArray(1024)
            var combinedBuffer = byteArrayOf()

            while (bluetoothState.value == BluetoothState.CONNECTED) {
                try {
                    val byteCount = inStream?.read(buffer) ?: 0

                    if (byteCount > 0) {
                        val msg = String(buffer, Charsets.UTF_8).substring(0..byteCount - 1)
                        //Log.d(AiMouseSingleton.DEBUG_TAG, "msg: $msg")

                        if (msg == "EOF") {
                            saveFile(context, combinedBuffer, "")
                            combinedBuffer = byteArrayOf()
                        } else if (msg == "EOJSON") {
                            val json = String(combinedBuffer, Charsets.UTF_8)
                            val response = Response.fromJSON(json)

                            val files = mutableListOf<File>()
                            files.addAll(response.folders.map { File(it) })
                            files.addAll(response.files.map { File(it.name, it.size) })

                            withContext(Dispatchers.Main) {
                                this@BluetoothService.files.clear()
                                this@BluetoothService.files.addAll(files)
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
                    this@BluetoothService.cancel()
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
        bluetoothStateMutex.withLock { bluetoothState.value = newState }
    }
}