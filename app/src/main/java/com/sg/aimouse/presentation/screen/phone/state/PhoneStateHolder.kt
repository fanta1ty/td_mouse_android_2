package com.sg.aimouse.presentation.screen.phone.state

import androidx.activity.ComponentActivity
import com.sg.aimouse.presentation.screen.phone.PhoneViewModel

class PhoneStateHolder(
    val activity: ComponentActivity,
    val viewModel: PhoneViewModel
) {
    fun navigateBack() {
        activity.moveTaskToBack(true)
    }
}