@file:OptIn(ExperimentalMaterial3Api::class)

package com.sg.aimouse.presentation.screen.localfile

import android.Manifest
import android.annotation.SuppressLint
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.sg.aimouse.R
import com.sg.aimouse.model.File
import com.sg.aimouse.presentation.component.*
import com.sg.aimouse.util.viewModelFactory
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.sg.aimouse.presentation.screen.localfile.state.LocalfileStateHolder
import com.sg.aimouse.service.BluetoothDevice
import androidx.compose.runtime.remember
import com.sg.aimouse.presentation.navigation.Screen

@SuppressLint("MissingPermission")
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFileScreen(navController: NavController? = null) {
    val activity = LocalActivity.current

    var showDevicesDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val viewModel: LocalFileViewModel = viewModel(
        factory = viewModelFactory { LocalFileViewModel(activity) }
    )
    val lifecycleOwner = LocalLifecycleOwner.current

    val stateHolder = remember { LocalfileStateHolder(activity, viewModel) }

    var draggedFile by remember { mutableStateOf<File?>(null) }
    var dragOffset by remember { mutableStateOf<Offset?>(null) }

    var localListPosition by remember { mutableStateOf<Offset?>(null) }
    var localListSize by remember { mutableStateOf<IntSize?>(null) }

    val localListState = rememberLazyListState()

    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    var isRefreshingLocal by remember { mutableStateOf(false) }
    val localSwipeRefreshState = rememberSwipeRefreshState(isRefreshingLocal)

    // BLE related states
    var showBLEScanDialog by remember { mutableStateOf(false) }
    var showBluetoothEnableDialog by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var foundDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isConnecting by remember { mutableStateOf(false) }

    // BLE connection status
    var bleConnected by remember { mutableStateOf(viewModel.isBleConnected()) }
    
    // Register for BLE connection state changes
    DisposableEffect(Unit) {
        val connectionCallback: (Boolean) -> Unit = { isConnected ->
            bleConnected = isConnected
        }
        viewModel.registerBleConnectionCallback(connectionCallback)
        onDispose {
            // Clean up if needed
        }
    }

    fun checkBluetoothConnection() {
        if (!viewModel.isBluetoothEnabled()) {
            showBluetoothEnableDialog = true
        } else if (!viewModel.isBleConnected()) {
//            showBLEScanDialog = true
            viewModel.scanForBluetoothDevices { }
            showDevicesDialog = true
        }
    }

    fun cancelScanBLE() {
        isScanning = false
        isConnecting = false
        foundDevices = emptyList()
    }

    // Check BLE connection on start
    LaunchedEffect(Unit) {
        checkBluetoothConnection()
        // Register callback to monitor BLE connection status
        viewModel.registerBleConnectionCallback { connected ->
            bleConnected = connected
        }
    }

    // Monitor BLE connection status and switch screen when connection is successful
    LaunchedEffect(bleConnected) {
        if (bleConnected && navController != null) {
            // Close the scan dialog if BLE connected
            showBLEScanDialog = false
        }
    }

    LaunchedEffect(isRefreshingLocal) {
        if (isRefreshingLocal) {
            viewModel.retrieveLocalFiles()
            isRefreshingLocal = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    stateHolder.requestStoragePermission()
                    // Check BLE connection on resume
                    checkBluetoothConnection()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Bluetooth Enable Dialog
    if (showBluetoothEnableDialog) {
        Dialog(onDismissRequest = { showBluetoothEnableDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.bluetooth_disabled),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.bluetooth_disabled_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { showBluetoothEnableDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray
                            )
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }

                        Button(
                            onClick = {
                                // Open Bluetooth settings
                                val intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                activity.startActivity(intent)
                                showBluetoothEnableDialog = false
                            }
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }


    // TD Mouse Device Selection Dialog
    if (showDevicesDialog) {
        Dialog(onDismissRequest = {
            viewModel.stopScanningDevices()
            showDevicesDialog = false
        }) {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Available BLE Devices",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .background(
                                color = Color(0xFFCCCCCC),
                                shape = RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = 0.dp,
                                    bottomEnd = 0.dp
                                )
                            )
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (viewModel.discoveredDevices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.1f)
                                .background(Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.Blue)
                        }
                    } else {
                        LazyColumn {
                            items(viewModel.discoveredDevices) { device ->
                                Button(
                                    onClick = {
                                        viewModel.connectToBluetoothDevice(device) { }
                                        viewModel.stopScanningDevices()
                                        showDevicesDialog = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 16.dp),
                                ) {
                                    Row {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(device.name ?: device.address, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.stopScanningDevices()
                            showDevicesDialog = false
                        },
                        modifier = Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 16.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    Scaffold { innerPaddings ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPaddings)
                    .background(color = colorResource(R.color.light_gray_DD))
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White, shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .padding(top = 5.dp, start = 5.dp, end = 5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier,
                            horizontalAlignment = Alignment.CenterHorizontally
                        )  {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (viewModel.connectedDevice() != null) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.Clear
                                    },
                                    contentDescription = null,
                                    tint = if (viewModel.connectedDevice() != null) Color.Green else Color.Red
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (viewModel.connectedDevice() != null) {
                                        "Connected: ${viewModel.connectedDevice()?.name ?: ""}"
                                    } else {
                                        "BLE Not Connected"
                                    }
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        if (viewModel.connectedDevice() != null) {
                                            viewModel.bleDisconnect()
                                            bleConnected = false
                                            cancelScanBLE()
                                        } else {
                                            viewModel.scanForBluetoothDevices { }
                                            showDevicesDialog = true
//                                            showBLEScanDialog = true
                                        }
                                    }
                                ) {
                                    Text(if (viewModel.connectedDevice() != null) "Disconnect" else "Connect")
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 10.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color = colorResource(R.color.light_gray_ED), shape = RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.title_phone_drive),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    top = 3.dp,
                                    bottom = 3.dp
                                )
                            )
                        }
                    }
                    // Back button
                    val rootPath = Environment.getExternalStorageDirectory().path
                    val currentPath = viewModel.currentLocalPath
                    if (currentPath != rootPath) {
                        IconButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .height(40.dp),
                            onClick = {
                                viewModel.navigateUpLocal()
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 14.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to parent folder",
                                    tint = Color.Black
                                )
                                Text(
                                    text = " ...",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                    SwipeRefresh(
                        state = localSwipeRefreshState,
                        onRefresh = { isRefreshingLocal = true }
                    ) {
                        LazyColumn(
                            state = localListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 8.dp)
                                .onGloballyPositioned { coordinates ->
                                    localListPosition = coordinates.localToRoot(Offset.Zero)
                                    localListSize = coordinates.size
                                }
                        ) {
                            items(
                                viewModel.localFiles,
                                key = { it.path + it.fileName }) { file ->
                                FileItem(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            viewModel.openLocalFolder(file)
                                        } else {
                                            viewModel.openLocalFile(file)
                                        }
                                    },
                                    onSwipeToDelete = {
                                        fileToDelete = file
                                        showDeleteDialog = true
                                    },
                                    refreshTrigger = refreshTrigger
                                )
                            }
                        }

                        if (viewModel.localFiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Empty folder",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            draggedFile?.let { file ->
                dragOffset?.let { offset ->
                    var cardSize by remember { mutableStateOf<IntSize?>(null) }
                    Card(
                        modifier = Modifier
                            .offset {
                                val halfWidth = cardSize?.width?.div(2) ?: 0
                                val halfHeight = cardSize?.height?.div(2) ?: 0
                                IntOffset(
                                    x = (offset.x - halfWidth).toInt(),
                                    y = (offset.y - halfHeight).toInt()
                                )
                            }
                            .zIndex(10f)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .onGloballyPositioned { coordinates ->
                                cardSize = coordinates.size
                            },
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FileItem(
                                file = file,
                                onClick = {},
                                onSwipeToDelete = {},
                                refreshTrigger = refreshTrigger
                            )
                        }
                    }
                }
            }

            if (showDeleteDialog && fileToDelete != null) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteDialog = false
                        fileToDelete = null
                    },
                    title = { Text("Confirm Delete") },
                    text = { Text("Are you sure you want to delete ${fileToDelete!!.fileName}?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteFile(fileToDelete!!)
                                showDeleteDialog = false
                                fileToDelete = null
                                refreshTrigger++
                            }
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                fileToDelete = null
                                refreshTrigger++
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (stateHolder.shouldShowStoragePermissionRequiredDialog) {
                Dialog(
                    title = stringResource(R.string.permission_required),
                    content = stringResource(R.string.storage_permission_required_desc),
                    onPositiveClickEvent = stateHolder::navigateToAppPermissionSettings
                )
            }
        }
    }
}
