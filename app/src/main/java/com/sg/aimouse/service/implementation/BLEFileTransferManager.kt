package com.sg.aimouse.service.implementation

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

object BLEConstants {
    val SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    val FILE_CONTROL_CHAR_UUID = UUID.fromString("0000FFE6-0000-1000-8000-00805F9B34FB")
    val FILE_INFO_CHAR_UUID = UUID.fromString("0000FFE7-0000-1000-8000-00805F9B34FB")
    val FILE_DATA_CHAR_UUID = UUID.fromString("0000FFE8-0000-1000-8000-00805F9B34FB")
    val FILE_ACK_CHAR_UUID = UUID.fromString("0000FFE9-0000-1000-8000-00805F9B34FB")
    val FILE_ERR_CHAR_UUID = UUID.fromString("0000FFEA-0000-1000-8000-00805F9B34FB")
}

enum class FileCommand(val value: Byte) {
    START_TRANSFER(0x01),
    ABORT_TRANSFER(0x02),
    CONTINUE_TRANSFER(0x03),
    REQUEST_CHUNK(0x06),
    CHUNK_RECEIVED(0x07),
    COMPLETE(0x08)
}

enum class TransferState {
    IDLE, CONNECTING, PREPARING, TRANSFERRING, SAVING, COMPLETE, ERROR
}

class BLEFileTransferManager(private val context: Context) {
    // State variables
    val state = mutableStateOf(TransferState.IDLE)
    val connectedDevice = mutableStateOf<BluetoothDevice?>(null)
    val discoveredDevices = mutableStateOf<List<BluetoothDevice>>(emptyList())
    val progress = mutableStateOf(0.0)
    val fileName = mutableStateOf("received_file.txt")
    val fileSize = mutableStateOf(0)
    val currentChunk = mutableStateOf(0)
    val totalChunks = mutableStateOf(-1)
    val errorMessage = mutableStateOf("")
    val transferCompletedFiles = mutableStateOf<List<File>>(emptyList())

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null
    private var fileControlCharacteristic: BluetoothGattCharacteristic? = null
    private var fileInfoCharacteristic: BluetoothGattCharacteristic? = null
    private var fileDataCharacteristic: BluetoothGattCharacteristic? = null
    private var fileAckCharacteristic: BluetoothGattCharacteristic? = null
    private var fileErrorCharacteristic: BluetoothGattCharacteristic? = null

    private var fileData = mutableListOf<Byte>()
    private val chunkSize = 180

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (device.name?.startsWith("TD") == true && !discoveredDevices.value.contains(device)) {
                    discoveredDevices.value = discoveredDevices.value + device
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevice.value = gatt?.device
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevice.value = null
                    state.value = TransferState.IDLE
                    gatt?.close()
                }
            } else {
                state.value = TransferState.ERROR
                errorMessage.value = "Connection failed"
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.services?.find { itsUUID -> itsUUID.uuid == BLEConstants.SERVICE_UUID }?.let { service ->
                    fileControlCharacteristic = service.getCharacteristic(BLEConstants.FILE_CONTROL_CHAR_UUID)
                    fileInfoCharacteristic = service.getCharacteristic(BLEConstants.FILE_INFO_CHAR_UUID)
                    fileDataCharacteristic = service.getCharacteristic(BLEConstants.FILE_DATA_CHAR_UUID)
                    fileAckCharacteristic = service.getCharacteristic(BLEConstants.FILE_ACK_CHAR_UUID)
                    fileErrorCharacteristic = service.getCharacteristic(BLEConstants.FILE_ERR_CHAR_UUID)
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic?.value?.let { data ->
                    when (characteristic.uuid) {
                        BLEConstants.FILE_INFO_CHAR_UUID -> processFileInfo(data)
                        BLEConstants.FILE_DATA_CHAR_UUID -> processChunkData(data)
                        BLEConstants.FILE_ACK_CHAR_UUID -> handleAckResponse(data)
                        BLEConstants.FILE_CONTROL_CHAR_UUID -> {
                            if (totalChunks.value == -1 && state.value == TransferState.TRANSFERRING) {
                                gatt?.readCharacteristic(fileInfoCharacteristic)
                            }
                        }
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                state.value = TransferState.ERROR
                errorMessage.value = "Write failed"
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        if (bluetoothAdapter?.isEnabled == true) {
            discoveredDevices.value = emptyList()
            bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
            state.value = TransferState.CONNECTING
        } else {
            state.value = TransferState.ERROR
            errorMessage.value = "Bluetooth is not enabled"
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startTransfer() {
        if (bluetoothGatt == null) {
            state.value = TransferState.ERROR
            errorMessage.value = "BLE Not Connected!"
            return
        }
        Log.d("debug", "Starting file transfer")

        state.value = TransferState.TRANSFERRING
        fileControlCharacteristic?.let { char ->
            val startTransferCmd = byteArrayOf(FileCommand.START_TRANSFER.value, 0)
            char.value = startTransferCmd
            bluetoothGatt?.writeCharacteristic(char)
        } ?: run {
            state.value = TransferState.ERROR
            errorMessage.value = "File control characteristic not ready"
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun requestChunk(chunkNum: Byte) {
        fileControlCharacteristic?.let { char ->
            val requestChunkCommand = byteArrayOf(FileCommand.REQUEST_CHUNK.value, chunkNum)
            char.value = requestChunkCommand
            bluetoothGatt?.writeCharacteristic(char)
            fileDataCharacteristic?.let { dataChar ->
                bluetoothGatt?.readCharacteristic(dataChar)
            }
        } ?: run {
            state.value = TransferState.ERROR
            errorMessage.value = "File control characteristic not ready"
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun processFileInfo(data: ByteArray) {
        if (data.size < 5) {
            state.value = TransferState.ERROR
            errorMessage.value = "Invalid file info"
            return
        }

        if (totalChunks.value == -1) {
            val nameLength = data[0].toInt()
            if (data.size < nameLength + 5) {
                state.value = TransferState.ERROR
                errorMessage.value = "Invalid file info format"
                return
            }

            fileName.value = String(data, 1, nameLength).trim('\u0000')
            val fileSizeData = data.copyOfRange(nameLength + 1, nameLength + 5)
            fileSize.value = ByteBuffer.wrap(fileSizeData).int
            totalChunks.value = (fileSize.value + chunkSize - 1) / chunkSize
            currentChunk.value = 0
            fileData.clear()

            requestNextChunk()
        } else {
            currentChunk.value += 1
            requestNextChunk()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun processChunkData(data: ByteArray) {
        if (totalChunks.value == -1) return

        fileData.addAll(data.toList())
        fileAckCharacteristic?.let { char ->
            val ackData = byteArrayOf(FileCommand.CHUNK_RECEIVED.value, currentChunk.value.toByte())
            char.value = ackData
            bluetoothGatt?.writeCharacteristic(char)
            fileDataCharacteristic?.let { dataChar ->
                bluetoothGatt?.readCharacteristic(dataChar)
            }
        }

        if (currentChunk.value + 1 >= totalChunks.value) {
            completeTransfer()
        } else {
            currentChunk.value += 1
            requestNextChunk()
        }
    }

    private fun handleAckResponse(data: ByteArray) {
        if (data.size < 2) {
            state.value = TransferState.ERROR
            errorMessage.value = "Invalid response format"
            return
        }

        if (data[0] == 0x01.toByte() && data[1] != 0x00.toByte()) {
            state.value = TransferState.ERROR
            errorMessage.value = "Failed to start transfer, error code: ${data[1]}"
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun requestNextChunk() {
        if (currentChunk.value >= totalChunks.value) {
            completeTransfer()
            return
        }
        requestChunk(currentChunk.value.toByte())
    }

    private fun completeTransfer() {
        state.value = TransferState.COMPLETE
        currentChunk.value = 0
        totalChunks.value = -1

        val file = File(context.cacheDir, fileName.value)
        file.writeBytes(fileData.toByteArray())
        transferCompletedFiles.value = transferCompletedFiles.value + file
    }
}