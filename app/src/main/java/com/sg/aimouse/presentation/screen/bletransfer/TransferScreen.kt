package com.sg.aimouse.presentation.screen.bletransfer

import android.Manifest
import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.sg.aimouse.presentation.navigation.Screen
import com.sg.aimouse.service.implementation.BLEFileTransferManager
import com.sg.aimouse.service.implementation.TransferState

@SuppressLint("MissingPermission")
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BLEFileTransferApp(navController: NavController) {
    var showDevicesDialog by remember { mutableStateOf(false) }
    val bleManager = BLEFileTransferManager(LocalContext.current)

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("BLE File Transfer") })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Connection Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (bleManager.connectedDevice.value != null) {
                            androidx.compose.material.icons.Icons.Default.CheckCircle
                        } else {
                            androidx.compose.material.icons.Icons.Default.Clear
                        },
                        contentDescription = null,
                        tint = if (bleManager.connectedDevice.value != null) Color.Green else Color.Red
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (bleManager.connectedDevice.value != null) {
                            "Connected: ${bleManager.connectedDevice.value?.name ?: ""}"
                        } else {
                            "Not Connected"
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (bleManager.connectedDevice.value != null) {
                                bleManager.disconnect()
                            } else {
                                bleManager.startScanning()
                                showDevicesDialog = true
                            }
                        }
                    ) {
                        Text(if (bleManager.connectedDevice.value != null) "Disconnect" else "Connect")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Transfer Status
                when (bleManager.state.value) {
                    TransferState.TRANSFERRING -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(progress = bleManager.progress.value.toFloat())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Transferring: ${bleManager.fileName.value}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${(bleManager.progress.value * 100).toInt()}% - Chunk ${bleManager.currentChunk.value}/${bleManager.totalChunks.value}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    TransferState.COMPLETE -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.Green,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("Transfer Complete!", style = MaterialTheme.typography.titleMedium)
                            Text(bleManager.fileName.value, style = MaterialTheme.typography.bodySmall)
                        }

                        Divider(modifier = Modifier.padding(vertical = 16.dp))
                    }
                    else -> {
                    }
                }

                // Action Buttons
                if (bleManager.connectedDevice.value != null && bleManager.state.value != TransferState.TRANSFERRING) {
                    Button(
                        onClick = { bleManager.startTransfer() },
                        enabled = true // Adjust based on your logic
                    ) {
                        Text("Start File Transfer")
                    }
                }

                // Transferred Files
                if (bleManager.transferCompletedFiles.value.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("Transferred Files:", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            items(bleManager.transferCompletedFiles.value) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color.Blue
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(file.name)
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    Text("No files transferred yet", color = Color.Gray)
                    Spacer(modifier = Modifier.weight(1f))
                }

                OutlinedButton(
                    onClick = {
                        navController.navigate(Screen.LocalFileScreen.route)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Navigate to Local Files")
                }
            }

            // Error Alert
            if (bleManager.errorMessage.value.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { bleManager.errorMessage.value = "" },
                    title = { Text("Error") },
                    text = { Text(bleManager.errorMessage.value) },
                    confirmButton = {
                        Button(onClick = { bleManager.errorMessage.value = "" }) {
                            Text("OK")
                        }
                    }
                )
            }

            // Device Selection Dialog
            if (showDevicesDialog) {
                Dialog(onDismissRequest = {
                    bleManager.stopScanning()
                    showDevicesDialog = false
                }) {
                    Card {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text("Available BLE Devices", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (bleManager.discoveredDevices.value.isEmpty()) {
                                Text("Scanning for TD devices...")
                            } else {
                                LazyColumn {
                                    items(bleManager.discoveredDevices.value) { device ->
                                        TextButton(
                                            onClick = {
                                                bleManager.connect(device)
                                                bleManager.stopScanning()
                                                showDevicesDialog = false
                                            }
                                        ) {
                                            Row {
                                                Icon(
                                                    imageVector = androidx.compose.material.icons.Icons.Default.Share,
                                                    contentDescription = null,
                                                    tint = Color.Blue
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(device.name ?: "Unknown Device", style = MaterialTheme.typography.titleMedium)
                                                    Text(device.address, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    bleManager.stopScanning()
                                    showDevicesDialog = false
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}