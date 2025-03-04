package com.sg.aimouse.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

@Composable
fun Dialog(
    title: String = "",
    content: String = "",
    isCancellable: Boolean = true,
    positiveButtonText: Int = android.R.string.ok,
    negativeButtonText: Int = android.R.string.cancel,
    onPositiveClickEvent: (() -> Unit)? = null,
    onNegativeClickEvent: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = { onDismissRequest?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = isCancellable,
            dismissOnClickOutside = isCancellable
        ),
        title = {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.W600,
            )
        },
        text = {
            Text(content)
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (onNegativeClickEvent != null) {
                    TextButton(onClick = onNegativeClickEvent) {
                        Text(stringResource(negativeButtonText))
                    }

                    if (onPositiveClickEvent != null) {
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }

                if (onPositiveClickEvent != null) {
                    TextButton(onClick = onPositiveClickEvent) {
                        Text(stringResource(positiveButtonText))
                    }
                }
            }
        })
}