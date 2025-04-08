package com.sg.aimouse.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
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
        confirmButton = {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(R.color.orange)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(android.R.string.ok),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colorResource(R.color.orange),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.transfer_summary),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                InfoRow("File Name: ", fileName)
                InfoRow("File Size:", formatFileSize(fileSize))
                InfoRow("Average Speed:", "%.2f MB/s".format(avgSpeedMBps))
                InfoRow("Max Speed:", "%.2f MB/s".format(maxSpeedMBps))
                InfoRow("Time Taken:", "%.2f seconds".format(timeTakenSeconds))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Medium)
        Text(text = value, fontWeight = FontWeight.Normal)
    }
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