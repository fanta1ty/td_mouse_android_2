package com.sg.aimouse.presentation.screen.phone.state

import androidx.activity.ComponentActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sg.aimouse.model.File
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import com.sg.aimouse.service.PermissionService
import com.sg.aimouse.service.implementation.PermissionServiceImpl

@OptIn(ExperimentalMaterialApi::class)
class PhoneStateHolder(
    val activity: ComponentActivity,
    val viewModel: HomeViewModel,
    val pullRefreshState: PullRefreshState
) : PermissionService by PermissionServiceImpl() {

    private var currentSelectedFile: File? = null

    var shouldShowFileSendingConfirmationDialog by mutableStateOf(false)
        private set

    fun sendFile() {
    }

    fun onFileItemClick(file: File) {
        currentSelectedFile = file
        viewModel.uploadFile(file.fileName)
    }

    fun dismissFileSendingConfirmationDialog() {
        shouldShowFileSendingConfirmationDialog = false
    }

    fun navigateBack() {
        activity.moveTaskToBack(true)
    }
}