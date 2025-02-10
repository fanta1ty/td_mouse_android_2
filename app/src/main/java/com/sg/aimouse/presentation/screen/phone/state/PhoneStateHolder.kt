package com.sg.aimouse.presentation.screen.phone.state

import androidx.activity.ComponentActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import com.sg.aimouse.model.File
import com.sg.aimouse.presentation.screen.home.HomeViewModel

@OptIn(ExperimentalMaterialApi::class)
class PhoneStateHolder(
    val activity: ComponentActivity,
    val viewModel: HomeViewModel,
    val pullRefreshState: PullRefreshState
) {

    private var currentSelectedFile: File? = null

    fun onFileItemClick(file: File) {
        if (!file.isDirectory) {
            currentSelectedFile = file
            viewModel.uploadFileSMB(file.fileName)
        }
    }

    fun navigateBack() {
        activity.moveTaskToBack(true)
    }
}