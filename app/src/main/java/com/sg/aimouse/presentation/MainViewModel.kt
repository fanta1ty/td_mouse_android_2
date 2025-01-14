package com.sg.aimouse.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val viewModelId = Pair(
        "${app.packageName}:viewModel",
        System.currentTimeMillis().hashCode()
    )
}