package com.sg.aimouse.util

import android.bluetooth.BluetoothDevice

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.sg.aimouse.service.implementation.BLEServiceSingleton
import java.util.UUID

/**
 * Lớp tiện ích để kiểm tra kết nối BLE
 */
object BLEConnectionTester {
    private const val TAG = "BLEConnectionTester"

    // Danh sách UUID của characteristic để thử đọc khi kiểm tra kết nối
    private val TEST_CHARACTERISTIC_UUIDS = listOf(
        UUID.fromString("0000ffe7-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe6-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe8-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffea-0000-1000-8000-00805f9b34fb"),
        // Thêm một số UUID phổ biến khác
        UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"), // Device Name
        UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb"), // Appearance
        UUID.fromString("00002a05-0000-1000-8000-00805f9b34fb")  // Service Changed
    )

    /**
     * Kiểm tra kết nối BLE bằng cách đọc một characteristic
     * @param context Context của ứng dụng
     * @param callback Callback để nhận kết quả kiểm tra (true nếu kết nối hoạt động, false nếu không)
     */
    fun testConnection(context: Context, callback: (Boolean, String) -> Unit) {
        val bleService = BLEServiceSingleton.getInstance(context)

        // Kiểm tra xem BLE có được kết nối không theo phương thức isConnected()
        if (!bleService.isConnected()) {
            callback(false, "BLE không được kết nối theo phương thức isConnected()")
            return
        }

        // Lấy thông tin thiết bị đang kết nối
        val connectedDevice = bleService.getConnectedDevice()
        if (connectedDevice == null) {
            callback(false, "Không tìm thấy thiết bị đã kết nối")
            return
        }

        // Hiển thị thông tin thiết bị
        Log.d(
            TAG,
            "Thiết bị đang kết nối: ${connectedDevice.name ?: "Không có tên"} (${connectedDevice.address})"
        )

        // Kiểm tra kết nối thực tế bằng cách đọc một characteristic
        val gatt =
            (bleService as? com.sg.aimouse.service.implementation.BLEServiceImpl)?.getBluetoothGatt()
        if (gatt == null) {
            callback(false, "Không thể truy cập BluetoothGatt")
            return
        }

        // Thử phát hiện lại dịch vụ
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                callback(false, "Không có quyền BLUETOOTH_CONNECT")
                return
            }
        }

        // Hiển thị tất cả các dịch vụ và đặc tính có sẵn
        val servicesInfo = StringBuilder()
        servicesInfo.appendLine("Các dịch vụ và đặc tính có sẵn:")

        var foundAnyService = false
        var foundAnyCharacteristic = false

        for (service in gatt.services) {
            foundAnyService = true
            servicesInfo.appendLine("Service: ${service.uuid}")

            for (characteristic in service.characteristics) {
                foundAnyCharacteristic = true
                servicesInfo.appendLine("  Characteristic: ${characteristic.uuid}")

                // Kiểm tra quyền đọc/ghi
                val properties = characteristic.properties
                val canRead = (properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
                val canWrite = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                val canNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0

                servicesInfo.appendLine("    Properties: ${if (canRead) "Read " else ""}${if (canWrite) "Write " else ""}${if (canNotify) "Notify" else ""}")
            }
        }
    }
}