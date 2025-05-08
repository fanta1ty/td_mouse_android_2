package com.sg.aimouse.service.implementation

import android.content.Context
import com.sg.aimouse.service.BLEService

/**
 * Singleton to manage BLEService, ensuring only one instance exists throughout the application
 */
object BLEServiceSingleton {
    private var instance: BLEService? = null

    fun getInstance(context: Context): BLEService {
        if (instance == null) {
            instance = BLEServiceImpl(context.applicationContext)
        }
        return instance!!
    }
}
