package com.sg.aimouse.service.implementation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.sg.aimouse.service.BLEService
import com.sg.aimouse.service.BluetoothDevice as AppBluetoothDevice
import java.util.UUID

class BLEServiceImpl(private val context: Context) : BLEService {
    private val TAG = "BLEServiceImpl"
    
    // BLE UUIDs from fw
    private val FILE_CTRL_UUID = UUID.fromString("0000ffe6-0000-1000-8000-00805f9b34fb")
    private val FILE_INFO_UUID = UUID.fromString("0000ffe7-0000-1000-8000-00805f9b34fb")
    private val FILE_DATA_UUID = UUID.fromString("0000ffe8-0000-1000-8000-00805f9b34fb")
    private val FILE_ACK_UUID = UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb")
    private val FILE_ERR_UUID = UUID.fromString("0000ffea-0000-1000-8000-00805f9b34fb")
    
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private var connectionStateCallback: ((Boolean) -> Unit)? = null
    
    private val SCAN_PERIOD: Long = 10000 // 10 seconds
    private val handler = Handler(Looper.getMainLooper())
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    device.name
                } else {
                    null
                }
            } else {
                device.name
            }
            
            val isTDDevice = deviceName?.startsWith("TD") ?: false
            
            if (isTDDevice) {
                val appDevice = AppBluetoothDevice(
                    name = deviceName,
                    address = device.address,
                    isTDDevice = true
                )
                
                // Check if the device already exists in the list
                val deviceExists = foundDevices.any { it.address == appDevice.address }
                
                if (!deviceExists) {
                    foundDevices.add(appDevice)
                    scanDevicesCallback?.invoke(foundDevices.toList())
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server.")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            Log.e(TAG, "No BLUETOOTH_CONNECT permission")
                            connectionStateCallback?.invoke(false)
                            return
                        }
                    }

                    // Discover services after successful connection
                    bluetoothGatt?.discoverServices()
                    connectionStateCallback?.invoke(true)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server.")
                    connectionStateCallback?.invoke(false)
                    
                    // Don't set bluetoothGatt to null immediately to allow reconnection attempts
                    // Instead, schedule a delayed cleanup if no reconnection happens
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (bluetoothGatt != null && !isConnected()) {
                            Log.d(TAG, "Cleaning up GATT resources after disconnect")
                            try {
                                bluetoothGatt?.close()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error closing GATT: ${e.message}")
                            }
                            bluetoothGatt = null
                        }
                    }, 5000) // 5 second delay before cleanup
                }
            } else {
                // Handle connection error
                val errorMsg = when (status) {
                    133 -> "GATT_ERROR (133): Device is out of range or not responding"
                    8 -> "GATT_CONNECTION_TIMEOUT (8): Connection timed out"
                    19 -> "GATT_CONN_TERMINATE_PEER_USER (19): Device refused the connection"
                    22 -> "GATT_CONN_FAIL_ESTABLISH (22): Can't establish connection"
                    else -> "Unknown error: $status"
                }

                Log.e(TAG, "Connection failed: $errorMsg")

                // Close the current connection
                gatt.close()
                bluetoothGatt = null
                connectionStateCallback?.invoke(false)

                // try to reconnect after 2 seconds if the error is due to the device being out of range
                if (status == 133) {
                    Log.i(TAG, "Will try to reconnect in 2 seconds...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Don't auto-reconnect here, let the user choose the device again
                    }, 2000)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully")
                // List all services and characteristics for debugging
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                }

                for (service in gatt.services) {
                    Log.d(TAG, "Service: ${service.uuid}")
                    for (characteristic in service.characteristics) {
                        Log.d(TAG, "  Characteristic: ${characteristic.uuid}")
                    }
                }

                // Check that the device has the required services and characteristics
                val hasRequiredServices = gatt.services.any { service ->
                    service.characteristics.any { characteristic ->
                        characteristic.uuid == FILE_CTRL_UUID ||
                                characteristic.uuid == FILE_INFO_UUID ||
                                characteristic.uuid == FILE_DATA_UUID
                    }
                }

                if (!hasRequiredServices) {
                    Log.w(TAG, "The device does not have the required TD Mouse services")
                    // Still keep the connection because it might be a different type of TD Mouse device
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic read successfully")
                // Process the read data here
            }
        }
    }
    
    private var foundDevices = mutableListOf<AppBluetoothDevice>()
    private var scanDevicesCallback: ((List<AppBluetoothDevice>) -> Unit)? = null
    
    override fun scanForDevices(callback: (List<AppBluetoothDevice>) -> Unit) {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required Bluetooth permissions")
            callback(emptyList())
            return
        }
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            callback(emptyList())
            return
        }
        
        if (isScanning) {
            return
        }
        
        foundDevices.clear()
        scanDevicesCallback = callback
        
        val scanner = bluetoothAdapter.bluetoothLeScanner
        val filters = listOf<ScanFilter>()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        isScanning = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No BLUETOOTH_SCAN permission")
                isScanning = false
                return
            }
        }
        
        scanner?.startScan(filters, settings, scanCallback)
        
        // Stop scanning after a predefined scan period
        handler.postDelayed({
            stopScan()
        }, SCAN_PERIOD)
    }
    
    private fun stopScan() {
        if (isScanning && bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "No BLUETOOTH_SCAN permission")
                    return
                }
            }
            
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        }
        isScanning = false
    }
    
    override fun connectToDevice(device: AppBluetoothDevice, callback: (Boolean) -> Unit) {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required Bluetooth permissions")
            callback(false)
            return
        }
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            callback(false)
            return
        }
        
        // Stop scanning before connecting
        stopScan()
        
        // Get the native BluetoothDevice
        val nativeDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No BLUETOOTH_CONNECT permission")
                callback(false)
                return
            }
            bluetoothAdapter.getRemoteDevice(device.address)
        } else {
            bluetoothAdapter.getRemoteDevice(device.address)
        }
        
        // Connect to the device
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No BLUETOOTH_CONNECT permission")
                callback(false)
                return
            }
            nativeDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            nativeDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }
    
    override fun disconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No BLUETOOTH_CONNECT permission")
                return
            }
        }
        
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun isConnected(): Boolean {
        // Checck if bluetoothGatt is null
        if (bluetoothGatt == null) {
            Log.d(TAG, "isConnected: bluetoothGatt is null")
            return false
        }

        // Check the actual connection status with the device
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "No BLUETOOTH_CONNECT permission")
                    return false
                }
            }

            // Check if the device is in the list of connected devices
            val connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            
            // If there are no connected devices, check the status of GATT
            if (connectedDevices.isEmpty()) {
                // Check the connection state of the GATT
                val gattDevice = bluetoothGatt?.device
                if (gattDevice != null) {
                    // Check if the device is reachable
                    try {
                        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                gattDevice.name
                            } else {
                                null
                            }
                        } else {
                            gattDevice.name
                        }
                        
                        Log.d(TAG, "Device name from GATT: $deviceName")
                        // If can access the device name, consider it connected
                        if (deviceName != null) {
                            return true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error accessing device name: ${e.message}")
                    }
                    
                    // try to ping the device to check connection
                    try {
                        // Try to read a characteristic to see if the device responds
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                return false
                            }
                        }
                        
                        // If GATT still exists, consider it connected
                        if (bluetoothGatt != null) {
                            Log.d(TAG, "GATT still exists, considering connected")
                            return true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pinging device: ${e.message}")
                    }
                }
                
                Log.d(TAG, "No connected GATT devices found")
                return false
            }

            // Check if the current device is in the list of connected devices
            val isDeviceConnected = connectedDevices.any { it.address == bluetoothGatt?.device?.address }

            Log.d(TAG, "BLE connection status: $isDeviceConnected")
            return isDeviceConnected
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection status: ${e.message}")
            return false
        }
    }
    
    override fun getConnectedDevice(): AppBluetoothDevice? {
        if (!isConnected()) return null
        
        val device = bluetoothGatt?.device ?: return null
        
        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.name
            } else {
                null
            }
        } else {
            device.name
        }
        
        return AppBluetoothDevice(
            name = deviceName,
            address = device.address,
            isTDDevice = deviceName?.startsWith("TD") ?: false
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun readCharacteristic(uuid: String, callback: (ByteArray) -> Unit) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.services.find { service ->
            service.characteristics.any { it.uuid.toString() == uuid }
        } ?: return

        val characteristic = service.characteristics.find { it.uuid.toString() == uuid } ?: return

        bluetoothGatt?.readCharacteristic(characteristic)
        // Handle the callback in your BLE callback implementation when the read is complete
    }
    
    override fun registerConnectionStateCallback(callback: (Boolean) -> Unit) {
        connectionStateCallback = callback
    }
    
    override fun unregisterConnectionStateCallback() {
        connectionStateCallback = null
    }
    
    override fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }
    
    /**
     * Get the current BluetoothGatt object for connection checking
     * Note: This method should only be used for checking purposes
     * @return BluetoothGatt object or null if not connected
     */
    fun getBluetoothGatt(): BluetoothGatt? {
        return bluetoothGatt
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}
