package com.sg.aimouse.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiConfiguration
import android.net.ConnectivityManager
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object WifiConnector {
    private const val TAG = "WifiConnector"
    private const val SSID = "SCOM_5Gh"
    private const val PASSWORD = "trekvn2015"

    // Timeout after which we give up on the request (ms)
    private const val REQUEST_TIMEOUT = 30_000L
    private const val PERMISSION_REQUEST_CODE = 123

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingCallback: ((Boolean) -> Unit)? = null

    fun checkAndRequestPermissions(activity: Activity, onResult: (Boolean) -> Unit) {
        when {
            hasRequiredPermissions(activity) -> {
                onResult(true)
            }
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show an explanation to the user
                // You might want to show a dialog here explaining why the permission is needed
                requestPermissions(activity)
            }
            else -> {
                requestPermissions(activity)
            }
        }
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_REQUEST_CODE
        )
    }

    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val permissionGranted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            pendingCallback?.invoke(permissionGranted)
            pendingCallback = null
        }
    }

    fun connectToTDMouseWifi(context: Context, callback: (Boolean) -> Unit) {
        if (!hasRequiredPermissions(context)) {
            Log.e(TAG, "Missing required ACCESS_FINE_LOCATION permission")
            if (context is Activity) {
                pendingCallback = callback
                checkAndRequestPermissions(context) { granted ->
                    if (granted) {
                        connectToWifi(context, callback)
                    } else {
                        callback(false)
                    }
                }
            } else {
                callback(false)
            }
            return
        }

        connectToWifi(context, callback)
    }

    private fun connectToWifi(context: Context, callback: (Boolean) -> Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        Log.d(TAG, "Attempting to connect to Wi-Fi SSID=$SSID on API ${Build.VERSION.SDK_INT}")

        if (!wifiManager.isWifiEnabled) {
            Log.d(TAG, "Wi-Fi disabled – enabling…")
            try {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
            } catch (e: Exception) {
                Log.e(TAG, "Unable to enable Wi-Fi: ${e.message}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 and above
            Log.d(TAG, "Using WifiNetworkSpecifier requestNetwork flow")
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(SSID)
                .setWpa2Passphrase(PASSWORD)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                // TD-Mouse AP has no Internet capability – declare so Android accepts it
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            var timeoutPosted = false
            lateinit var timeoutRunnable: Runnable

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    Log.d(TAG, "onAvailable – Wi-Fi network acquired, binding process")
                    // Cancel timeout once connected
                    if (timeoutPosted) mainHandler.removeCallbacks(timeoutRunnable)
                    connectivityManager.bindProcessToNetwork(network)
                    callback(true)
                }

                override fun onUnavailable() {
                    Log.e(TAG, "onUnavailable – Wi-Fi request failed/unavailable")
                    if (timeoutPosted) mainHandler.removeCallbacks(timeoutRunnable)
                    callback(false)
                }

                override fun onLost(network: android.net.Network) {
                    Log.w(TAG, "onLost – Wi-Fi network lost")
                    // Optional: release binding when lost
                    connectivityManager.bindProcessToNetwork(null)
                }
            }

            try {
                connectivityManager.requestNetwork(request, networkCallback)
                // Guard timeout – if not connected within X seconds, fail callback
                timeoutRunnable = Runnable {
                    Log.w(TAG, "Wi-Fi request timed out after ${REQUEST_TIMEOUT}ms")
                    try {
                        connectivityManager.unregisterNetworkCallback(networkCallback)
                    } catch (_: Exception) {}
                    callback(false)
                }
                mainHandler.postDelayed(timeoutRunnable, REQUEST_TIMEOUT)
                timeoutPosted = true
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while connecting to Wi-Fi: ${e.message}")
                callback(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to Wi-Fi via requestNetwork: ${e.message}")
                callback(false)
            }
        } else {
            // Below Android 10
            Log.d(TAG, "Using legacy WifiConfiguration flow")
            try {
                val conf = WifiConfiguration().apply {
                    SSID = "\"$SSID\"" // quoted per API requirement
                    preSharedKey = "\"$PASSWORD\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                }

                try {
                    // Remove existing config for same SSID (if any)
                    @Suppress("DEPRECATION")
                    val existingId = wifiManager.configuredNetworks?.firstOrNull { it.SSID == conf.SSID }?.networkId
                    if (existingId != null) {
                        Log.d(TAG, "Removing previous config for $SSID (id=$existingId)")
                        wifiManager.removeNetwork(existingId)
                        wifiManager.saveConfiguration()
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Security exception while accessing configured networks: ${e.message}")
                    // Continue with adding new network even if we couldn't remove old one
                }

                @Suppress("DEPRECATION")
                val netId = wifiManager.addNetwork(conf)
                Log.d(TAG, "addNetwork returned id=$netId")
                if (netId == -1) {
                    Log.e(TAG, "Failed to add Wi-Fi network config")
                    callback(false)
                    return
                }

                wifiManager.disconnect()
                val enableOk = wifiManager.enableNetwork(netId, true)
                Log.d(TAG, "enableNetwork result=$enableOk – reconnecting…")
                @Suppress("DEPRECATION")
                val reconnectOk = wifiManager.reconnect()
                Log.d(TAG, "reconnect result=$reconnectOk")

                callback(enableOk && reconnectOk)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to Wi-Fi via legacy flow: ${e.message}")
                callback(false)
            }
        }
    }
}