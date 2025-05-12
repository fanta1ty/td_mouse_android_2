package com.sg.aimouse.service

interface BLEService {

    fun scanForDevices(callback: (List<BluetoothDevice>) -> Unit)

    fun connectToDevice(device: BluetoothDevice, callback: (Boolean) -> Unit)
    
    fun disconnect()
    
    fun isConnected(): Boolean
    
    fun getConnectedDevice(): BluetoothDevice?
    
    fun registerConnectionStateCallback(callback: (Boolean) -> Unit)
    
    fun unregisterConnectionStateCallback()
    
    fun isBluetoothEnabled(): Boolean

    fun readCharacteristic(uuid: String, callback: (ByteArray) -> Unit)
}

data class BluetoothDevice(
    val name: String?,
    val address: String,
    val isTDDevice: Boolean
)
