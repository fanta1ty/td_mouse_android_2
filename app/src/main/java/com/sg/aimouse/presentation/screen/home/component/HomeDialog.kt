package com.sg.aimouse.presentation.screen.home.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sg.aimouse.R
import com.sg.aimouse.presentation.component.Dialog

@Composable
fun StoragePermissionRequiredDialog(
    shouldShow: Boolean,
    onPositiveClickEvent: () -> Unit
) {
    if (shouldShow) {
        Dialog(
            title = stringResource(R.string.permission_required),
            content = stringResource(R.string.storage_permission_required_desc),
            onPositiveClickEvent = onPositiveClickEvent
        )
    }
}