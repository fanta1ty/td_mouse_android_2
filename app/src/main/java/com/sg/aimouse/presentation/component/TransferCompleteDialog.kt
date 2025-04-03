package com.sg.aimouse.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sg.aimouse.R
import java.util.Locale

@Composable
fun TransferCompleteDialog(
    fileName: String,
    fileSize: Long,
    avgSpeedMBps: Double,
    maxSpeedMBps: Double,
    timeTakenSeconds: Double,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.transfer_summary),
                fontSize = 20.sp,
                fontWeight = FontWeight.W600
            )
        },
        text = {
            Column {
                Text("File Name: $fileName")
                Spacer(modifier = Modifier.height(8.dp))
                Text("File Size: ${formatFileSize(fileSize)}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Average Speed: %.2f MB/s".format(avgSpeedMBps))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Max Speed: %.2f MB/s".format(maxSpeedMBps))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Time Taken: %.2f seconds".format(timeTakenSeconds))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatFileSize(size: Long): String {
    return if (size > 1024 * 1024 * 1024) { // GB
        String.format(Locale.US, "%.2f GB", size.toDouble() / (1024 * 1024 * 1024))
    } else if (size > 1024 * 1024) { // MB
        String.format(Locale.US, "%.2f MB", size.toDouble() / (1024 * 1024))
    } else if (size > 1024) { // KB
        String.format(Locale.US, "%.0f KB", size.toDouble() / 1024)
    } else { // Byte
        if (size > 1) "$size Bytes" else "$size Byte"
    }
}