package com.sg.aimouse.service.implementation

import android.bluetooth.BluetoothGattCharacteristic
import android.Manifest
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import android.bluetooth.*
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

object BLEConstants {
    val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val FILE_CONTROL_CHAR_UUID = UUID.fromString("0000ffe6-0000-1000-8000-00805f9b34fb")
    val FILE_INFO_CHAR_UUID = UUID.fromString("0000ffe7-0000-1000-8000-00805f9b34fb")
    val FILE_DATA_CHAR_UUID = UUID.fromString("0000ffe8-0000-1000-8000-00805f9b34fb")
    val FILE_ACK_CHAR_UUID = UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb")
    val FILE_ERR_CHAR_UUID = UUID.fromString("0000ffea-0000-1000-8000-00805f9b34fb")
}

enum class FileCommand(val value: Byte) {
    START_TRANSFER(0x01),
    ABORT_TRANSFER(0x02),
    CONTINUE_TRANSFER(0x03),
    REQUEST_CHUNK(0x06),
    CHUNK_RECEIVED(0x07),
    COMPLETE(0x08)
}
//enum class FileCommand(val value: Byte) {
//    START_TRANSFER(0x01),
//    REQUEST_CHUNK(0x03), // Changed from 0x06 to 0x03
//    ACK(0x04),
//    COMPLETE_TRANSFER(0x05),
//    ERROR(0xFF.toByte())
//}

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

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevice.value = gatt?.device
                    gatt?.discoverServices()
                    println("Connected to ${gatt?.device?.name}")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevice.value = null
                    state.value = TransferState.IDLE
                    gatt?.close()
                    println("Disconnected")
                }
            } else {
                state.value = TransferState.ERROR
                errorMessage.value = "Connection failed: status $status"
                println("Connection failed: status $status")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.services?.find { it.uuid == BLEConstants.SERVICE_UUID }?.let { service ->
                    fileControlCharacteristic = service.getCharacteristic(BLEConstants.FILE_CONTROL_CHAR_UUID)
                    fileInfoCharacteristic = service.getCharacteristic(BLEConstants.FILE_INFO_CHAR_UUID)
                    fileDataCharacteristic = service.getCharacteristic(BLEConstants.FILE_DATA_CHAR_UUID)
                    fileAckCharacteristic = service.getCharacteristic(BLEConstants.FILE_ACK_CHAR_UUID)
                    fileErrorCharacteristic = service.getCharacteristic(BLEConstants.FILE_ERR_CHAR_UUID)
                    println("Discovered characteristics: control=${fileControlCharacteristic != null}, info=${fileInfoCharacteristic != null}, data=${fileDataCharacteristic != null}")
                    fileControlCharacteristic?.let {
                        println("File control properties: WRITE=${it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0}, READ=${it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0}, NOTIFY=${it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0}")
                        if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            gatt?.setCharacteristicNotification(it, true)
                            val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt?.writeDescriptor(descriptor)
                            println("Enabled notifications for file control characteristic")
                        }
                    }
                    fileDataCharacteristic?.let {
                        println("File data properties: WRITE=${it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0}, READ=${it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0}, NOTIFY=${it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0}")
                        if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            gatt?.setCharacteristicNotification(it, true)
                            val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt?.writeDescriptor(descriptor)
                            println("Enabled notifications for file data characteristic")
                        }
                    }
                }
            } else {
                state.value = TransferState.ERROR
                errorMessage.value = "Service discovery failed: status $status"
                println("Service discovery failed: status $status")
            }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic?.value?.let { data ->
                    when (characteristic.uuid) {
                        BLEConstants.FILE_CONTROL_CHAR_UUID -> {
                            println("==> Step 2: Read file control, data: ${data.joinToString { it.toInt().toString(16) }}")
                            if (totalChunks.value == -1 && state.value == TransferState.TRANSFERRING) {
                                fileInfoCharacteristic?.let {
                                    gatt?.readCharacteristic(it)
                                    println("==> Step 3: Reading file info characteristic")
                                } ?: run {
                                    state.value = TransferState.ERROR
                                    errorMessage.value = "File info characteristic not ready"
                                    println("File info characteristic not ready")
                                }
                            }
                        }
                        BLEConstants.FILE_INFO_CHAR_UUID -> {
                            println("==> Step 4: Processing file info: ${data.joinToString { it.toInt().toString(16) }}")
                            processFileInfo(data)
                        }
                        BLEConstants.FILE_DATA_CHAR_UUID -> {
                            println("==> Step 5: Processing chunk data: ${data.joinToString { it.toInt().toString(16) }}")
                            processChunkData(data)
                        }
                        BLEConstants.FILE_ACK_CHAR_UUID -> {
                            println("==> Processing ack: ${data.joinToString { it.toInt().toString(16) }}")
                            handleAckResponse(data)
                        }
                        BLEConstants.FILE_ERR_CHAR_UUID -> {
                            println("==> Error received: ${data.joinToString { it.toInt().toString(16) }}")
                        }
                    }
                }
            } else {
                state.value = TransferState.ERROR
                errorMessage.value = "Read failed for ${characteristic?.uuid}: status $status"
                println("Read failed for ${characteristic?.uuid}: status $status")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                println("==> Write successful for ${characteristic?.uuid}")
                if (characteristic?.uuid == BLEConstants.FILE_CONTROL_CHAR_UUID && state.value == TransferState.TRANSFERRING && totalChunks.value == -1) {
                    fileControlCharacteristic?.let {
                        gatt?.readCharacteristic(it)
                        println("==> Step 2: Reading file control characteristic after write")
                    }
                } else if (characteristic?.uuid == BLEConstants.FILE_CONTROL_CHAR_UUID && state.value == TransferState.TRANSFERRING) {
                    // After chunk request, read fileDataCharacteristic
                    fileDataCharacteristic?.let {
                        gatt?.readCharacteristic(it)
                        println("==> Step 5: Reading file data characteristic for chunk ${currentChunk.value}")
                    } ?: run {
                        state.value = TransferState.ERROR
                        errorMessage.value = "File data characteristic not ready"
                        println("File data characteristic not ready")
                    }
                }
            } else {
                state.value = TransferState.ERROR
                errorMessage.value = "Write failed for ${characteristic?.uuid}: status $status"
                println("Write failed for ${characteristic?.uuid}: status $status")
            }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.value?.let { data ->
                println("==> Notification for ${characteristic.uuid}: ${data.joinToString { it.toInt().toString(16) }}")
                when (characteristic.uuid) {
                    BLEConstants.FILE_CONTROL_CHAR_UUID -> {
                        if (totalChunks.value == -1 && state.value == TransferState.TRANSFERRING) {
                            fileInfoCharacteristic?.let {
                                gatt?.readCharacteristic(it)
                                println("==> Step 3: Reading file info characteristic")
                            } ?: run {
                                state.value = TransferState.ERROR
                                errorMessage.value = "File info characteristic not ready"
                                println("File info characteristic not ready")
                            }
                        }
                    }
                    BLEConstants.FILE_DATA_CHAR_UUID -> {
                        println("==> Step 5: Processing chunk data (notification): ${data.joinToString { it.toInt().toString(16) }}")
                        processChunkData(data)
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                println("==> Descriptor write successful for ${descriptor?.uuid}")
            } else {
                state.value = TransferState.ERROR
                errorMessage.value = "Descriptor write failed for ${descriptor?.uuid}: status $status"
                println("Descriptor write failed for ${descriptor?.uuid}: status $status")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startTransfer() {
        if (bluetoothGatt == null) {
            state.value = TransferState.ERROR
            errorMessage.value = "BLE Not Connected!"
            println("BLE Not Connected!")
            return
        }

        fileControlCharacteristic?.let { char ->
            state.value = TransferState.TRANSFERRING
            val startTransferCmd = byteArrayOf(FileCommand.START_TRANSFER.value, 0)
            char.value = startTransferCmd
            println("==> Step 1: Writing to ${char.uuid}, GATT connected: ${bluetoothGatt != null}")
            bluetoothGatt?.writeCharacteristic(char)
            println("==> Step 1: Wrote START_TRANSFER command: ${startTransferCmd.joinToString { it.toInt().toString(16) }}")
        } ?: run {
            state.value = TransferState.ERROR
            errorMessage.value = "File control characteristic not ready"
            println("File control characteristic not ready")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun processFileInfo(data: ByteArray) {
        println("==> Processing file info data: ${data.joinToString { it.toInt().toString(16) }}")
        try {
            // Skip filename from peripheral, keep default "received_file.txt"
            // val nameBytes = data.copyOfRange(0, 15).takeWhile { it != 0.toByte() }.toByteArray()
            // fileName.value = String(nameBytes, Charsets.UTF_8)
            println("==> Using default file name: ${fileName.value}")

            // Extract file size (bytes 15-18, little-endian)
            val sizeBytes = data.copyOfRange(15, 19)
            fileSize.value = ByteBuffer.wrap(sizeBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            println("==> File size: ${fileSize.value} bytes")

            // Validate file size
            if (fileSize.value <= 0) {
                throw IllegalStateException("Invalid file size: ${fileSize.value}")
            }

            // Calculate total chunks
            totalChunks.value = (fileSize.value + chunkSize - 1) / chunkSize
            println("==> Total chunks: ${totalChunks.value}")

            // Request first chunk
            currentChunk.value = 0
            requestChunk(currentChunk.value)
        } catch (e: Exception) {
            state.value = TransferState.ERROR
            errorMessage.value = "Failed to process file info: ${e.localizedMessage}"
            println("==> Error processing file info: ${e.localizedMessage}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun requestChunk(chunkIndex: Int) {
        fileControlCharacteristic?.let { char ->
            val chunkRequestCmd = byteArrayOf(FileCommand.REQUEST_CHUNK.value, chunkIndex.toByte())
            char.value = chunkRequestCmd
            println("==> Requesting chunk $chunkIndex: ${chunkRequestCmd.joinToString { it.toInt().toString(16) }}")
            bluetoothGatt?.writeCharacteristic(char)
            println("==> Wrote chunk request to ${char.uuid}")
        } ?: run {
            state.value = TransferState.ERROR
            errorMessage.value = "File control characteristic not ready"
            println("File control characteristic not ready")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun processChunkData(data: ByteArray) {
        println("==> Processing chunk ${currentChunk.value}: ${data.size} bytes, data: ${data.joinToString { it.toInt().toString(16) }}")
        if (totalChunks.value <= 0) {
            state.value = TransferState.ERROR
            errorMessage.value = "Invalid total chunks: ${totalChunks.value}"
            println("==> Error: Invalid total chunks: ${totalChunks.value}")
            return
        }

        fileData.addAll(data.toList())
        currentChunk.value += 1
        progress.value = currentChunk.value.toDouble() / totalChunks.value

        println("==> Current chunk: ${currentChunk.value}, Total chunks: ${totalChunks.value}, File data size: ${fileData.size} bytes")

        if (currentChunk.value < totalChunks.value) {
            requestChunk(currentChunk.value)
        } else {
            completeTransfer()
        }
    }

    private fun handleAckResponse(data: ByteArray) {
        println("==> Handling ACK response: ${data.joinToString { it.toInt().toString(16) }}")
        // Handle ACK if needed (e.g., confirm chunk receipt)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveFile() {
        println("==> Saving file: ${fileName.value}")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+ (API 29+)
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName.value) // Uses "received_file.txt"
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BLEFile")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("Failed to create file URI")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(fileData.toByteArray())
                    outputStream.flush()
                } ?: throw Exception("Failed to open output stream")

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                // Add to transferCompletedFiles (optional, for UI display)
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "BLEFile/${fileName.value}"
                )
                transferCompletedFiles.value = transferCompletedFiles.value + file
                println("==> File saved successfully: ${file.absolutePath}")
            } else {
                // Use File API for Android 9 and below
                val bleFileDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "BLEFile"
                )
                if (!bleFileDir.exists()) {
                    bleFileDir.mkdirs()
                }
                val file = File(bleFileDir, fileName.value) // Uses "received_file.txt"
                file.writeBytes(fileData.toByteArray())
                transferCompletedFiles.value = transferCompletedFiles.value + file
                println("==> File saved successfully: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            state.value = TransferState.ERROR
            errorMessage.value = "Failed to save file: ${e.localizedMessage}"
            println("==> Error saving file: ${e.localizedMessage}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun completeTransfer() {
        println("==> Completing transfer")
        saveFile()
        state.value = TransferState.COMPLETE
        fileData.clear()
        currentChunk.value = 0
        totalChunks.value = -1
        progress.value = 0.0
    }
}