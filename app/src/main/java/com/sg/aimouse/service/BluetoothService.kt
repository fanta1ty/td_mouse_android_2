package com.sg.aimouse.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.sg.aimouse.common.AiMouseSingleton
import com.sg.aimouse.model.Response
import com.sg.aimouse.model.TDFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class BluetoothState {
    CONNECTING, CONNECTED, DISCONNECTED
}

enum class CommandType {
    LIST_FILE, DOWNLOAD_FILE
}

@SuppressLint("MissingPermission")
class BluetoothService(context: Context) {
    private val uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee")
    private var adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private val bluetoothState = MutableStateFlow(BluetoothState.DISCONNECTED)
    private var bluetoothState2 = BluetoothState.DISCONNECTED
    private val files = mutableStateListOf<TDFile>()

    fun connect() {
        connectThread?.cancel()
        connectThread = null
        connectThread = ConnectThread(adapter.bondedDevices.toList()[0])
        connectThread!!.start()
    }

    fun close() {
        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null
    }

    fun release() {
        coroutineScope.cancel()
    }

    fun sendCommand(cmd: String) {
        connectedThread?.write(cmd)
    }

    fun isBluetoothEnable() = adapter.isEnabled

    fun isBluetoothDeviceDetected() = !adapter.bondedDevices.isEmpty()

    fun getBluetoothState() = bluetoothState.asStateFlow()

    fun getFiles(): List<TDFile> = files

    @Synchronized
    private fun connected(socket: BluetoothSocket) {
        connectedThread?.cancel()
        connectedThread = null
        connectedThread = ConnectedThread(socket)
        connectedThread!!.start()
    }

    private fun updateBluetoothState(newState: BluetoothState) {
        bluetoothState2 = newState
        coroutineScope.launch { bluetoothState.emit(newState) }
    }

    internal inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private var socket: BluetoothSocket? = null

        init {
            socket = try {
                updateBluetoothState(BluetoothState.CONNECTING)
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (e: IOException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to create RFCOMM socket", e)
                cancel()
                null
            }
        }

        override fun run() {
            adapter.cancelDiscovery()
            if (socket == null) return

            try {
                socket!!.connect()
                connected(socket!!)
            } catch (e: IOException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to connect Bluetooth", e)
                cancel()
            }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to close socket", e)
            }

            updateBluetoothState(BluetoothState.DISCONNECTED)
        }
    }

    internal inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private var inStream: InputStream? = null
        private var outStream: OutputStream? = null

        init {
            try {
                inStream = socket.inputStream
                outStream = socket.outputStream
            } catch (e: IOException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to open stream", e)
            }
        }

        override fun run() {
            updateBluetoothState(BluetoothState.CONNECTED)
            var buffer = ByteArray(1024)

            while (bluetoothState2 == BluetoothState.CONNECTED) {
                try {
                    val byteCount = inStream!!.read(buffer)

                    if (byteCount > 0) {
                        val json = String(buffer, Charsets.UTF_8).substring(0..byteCount - 1)
                        val response = Response.fromJSON(json)
                        files.clear()
                        files.addAll(response.folder.map { TDFile(it, true) })
                        files.addAll(response.files.map { TDFile(it) })
                        Log.d(AiMouseSingleton.DEBUG_TAG, "read: $json")
                    }
                } catch (e: IOException) {
                    Log.e(AiMouseSingleton.DEBUG_TAG, "Input stream failed", e)
                    cancel()
                }
            }
        }

        fun write(cmd: String) {
            Log.d(AiMouseSingleton.DEBUG_TAG, "write cmd:: $cmd")
            outStream?.write(cmd.toByteArray())
        }

        fun cancel() {
            try {
                inStream?.close()
                outStream?.close()
                socket.close()
            } catch (e: IOException) {
                Log.e(AiMouseSingleton.DEBUG_TAG, "Failed to close socket", e)
            }

            updateBluetoothState(BluetoothState.DISCONNECTED)
        }
    }
}