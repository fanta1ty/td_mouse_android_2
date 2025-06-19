package com.sg.aimouse.util

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiConfiguration
import android.net.ConnectivityManager
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

object WifiConnector {
    private const val SSID = "TD_Mouse"
    private const val PASSWORD = "12345678"

    fun connectToTDMouseWifi(context: Context, callback: (Boolean) -> Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 and above
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(SSID)
                .setWpa2Passphrase(PASSWORD)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)
                    callback(true)
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    callback(false)
                }
            }

            try {
                connectivityManager.requestNetwork(request, networkCallback)
            } catch (e: Exception) {
                Log.e("WifiConnector", "Error connecting to WiFi: ${e.message}")
                callback(false)
            }
        } else {
            // Below Android 10
            try {
                val conf = WifiConfiguration()
                conf.SSID = "\"$SSID\""
                conf.preSharedKey = "\"$PASSWORD\""

                val netId = wifiManager.addNetwork(conf)
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()

                callback(true)
            } catch (e: Exception) {
                Log.e("WifiConnector", "Error connecting to WiFi: ${e.message}")
                callback(false)
            }
        }
    }
}
