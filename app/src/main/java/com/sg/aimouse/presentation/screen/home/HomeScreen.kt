@file:OptIn(ExperimentalMaterial3Api::class)

package com.sg.aimouse.presentation.screen.home

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.sg.aimouse.presentation.screen.connect.ConnectionViewModel
import com.sg.aimouse.presentation.screen.home.state.HomeStateHolder
import com.sg.aimouse.service.implementation.SMBState
import com.sg.aimouse.util.viewModelFactory
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.sg.aimouse.presentation.navigation.Screen
import com.sg.aimouse.service.BluetoothDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController? = null) {
    val activity = LocalActivity.current
    val connectionViewModel: ConnectionViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = viewModelFactory { ConnectionViewModel(activity) }
    )
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { HomeViewModel(activity, connectionViewModel.getSambaService()) }
    )
    val lifecycleOwner = LocalLifecycleOwner.current

    val stateHolder = remember { HomeStateHolder(activity, viewModel) }

    var draggedFile by remember { mutableStateOf<File?>(null) }
    var dragOffset by remember { mutableStateOf<Offset?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var dragSource by remember { mutableStateOf<String?>(null) }

    var remoteListPosition by remember { mutableStateOf<Offset?>(null) }
    var remoteListSize by remember { mutableStateOf<IntSize?>(null) }
    var localListPosition by remember { mutableStateOf<Offset?>(null) }
    var localListSize by remember { mutableStateOf<IntSize?>(null) }

    val remoteListState = rememberLazyListState()
    val localListState = rememberLazyListState()

    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var isRemoteFile by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    var isRefreshingRemote by remember { mutableStateOf(false) }
    var isRefreshingLocal by remember { mutableStateOf(false) }
    val remoteSwipeRefreshState = rememberSwipeRefreshState(isRefreshingRemote)
    val localSwipeRefreshState = rememberSwipeRefreshState(isRefreshingLocal)

    // BLE related states
    var showBLEScanDialog by remember { mutableStateOf(false) }
    var showBluetoothEnableDialog by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var foundDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isConnecting by remember { mutableStateOf(false) }

    // Trạng thái kết nối BLE
    var bleConnected by remember { mutableStateOf(viewModel.isBleConnected()) }
    var smbConnected by remember { mutableStateOf(viewModel.isSMBConnected()) }

    fun checkBluetoothConnection() {
        if (!viewModel.isBluetoothEnabled()) {
            showBluetoothEnableDialog = true
        } else if (!viewModel.isBleConnected()) {
            showBLEScanDialog = true
        }
    }

    // Check BLE connection on start
    LaunchedEffect(Unit) {
        checkBluetoothConnection()
        // Đăng ký callback theo dõi trạng thái kết nối BLE
        viewModel.registerBleConnectionCallback { connected ->
            bleConnected = connected
        }
    }

    // Theo dõi trạng thái kết nối BLE và chuyển màn hình khi kết nối thành công
    LaunchedEffect(bleConnected) {
        if (bleConnected && navController != null) {
            // Đóng dialog scan nếu đang hiển thị
            showBLEScanDialog = false
            if (!smbConnected) {
                navController.navigate(Screen.ConnectionScreen.route)
            }
        }
    }


    val transferProgress = viewModel.transferProgress
    val transferSpeed = viewModel.transferSpeed
    val isTransferring = viewModel.isTransferringFileSMB

    LaunchedEffect(isTransferring) {
        if (!isTransferring && isUploading) {
            isUploading = false
            refreshTrigger++
            viewModel.retrieveRemoteFilesSMB(viewModel.currentRemotePath)
        } else if (!isTransferring && isDownloading) {
            isDownloading = false
            refreshTrigger++
            viewModel.refreshCurrentLocalFolder()
        }
    }

    LaunchedEffect(isRefreshingRemote) {
        if (isRefreshingRemote) {
            viewModel.retrieveRemoteFilesSMB(viewModel.currentRemotePath)
            isRefreshingRemote = false
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
                Lifecycle.Event.ON_STOP -> stateHolder.viewModel.updateSMBState(SMBState.DISCONNECTED)
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
                                // Mở cài đặt Bluetooth
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

    // BLE Scan Dialog
    if (showBLEScanDialog && !bleConnected) {
        Dialog(onDismissRequest = { showBLEScanDialog = false }) {
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
                        text = stringResource(R.string.scan_ble_devices),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isScanning) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.scanning_ble_devices),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (foundDevices.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.ble_device_found),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            items(foundDevices) { device ->
                                Button(
                                    onClick = {
                                        isConnecting = true
                                        viewModel.connectToBluetoothDevice(device) { success ->
                                            isConnecting = false
                                            if (success) {
                                                // Đóng dialog scan nếu đang hiển thị
                                                showBLEScanDialog = false
                                                // Chuyển sang màn hình ConnectionScreen
                                                navController?.navigate(Screen.ConnectionScreen.route)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    enabled = !isConnecting
                                ) {
                                    Text(device.name ?: device.address)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.ble_no_devices_found),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                isScanning = true
                                foundDevices = emptyList()
                                viewModel.scanForBluetoothDevices { devices ->
                                    foundDevices = devices
                                    isScanning = false
                                }
                            },
                            enabled = !isScanning && !isConnecting
                        ) {
                            Text(stringResource(R.string.scan_ble_devices))
                        }
                        Button(
                            onClick = { showBLEScanDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray
                            )
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                }
            }
        }
    }

    Scaffold { innerPaddings ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (viewModel.showTransferDialog.value && viewModel.lastTransferStats != null && viewModel.lastTransferredFileName != null) {
                TransferCompleteDialog(
                    fileName = viewModel.lastTransferredFileName!!,
                    fileSize = viewModel.lastTransferStats!!.fileSize,
                    avgSpeedMBps = viewModel.lastTransferStats!!.avgSpeedMBps,
                    maxSpeedMBps = viewModel.lastTransferStats!!.maxSpeedMBps,
                    timeTakenSeconds = viewModel.lastTransferStats!!.timeTakenSeconds,
                    onDismiss = {
                        viewModel.showTransferDialog.value = false
                    }
                )
            }
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
                        .background(Color.White, shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .padding(top = 5.dp, start = 5.dp, end = 5.dp)
                ) {
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
                                text = stringResource(R.string.title_tdmouse_drive),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    top = 3.dp,
                                    bottom = 3.dp
                                )
                            )
                            Text(
                                text = if (bleConnected) "BLE Connected" else "BLE Disconnected",
                                color = if (bleConnected) Color.Green else Color.Red,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    top = 3.dp,
                                    bottom = 3.dp,
                                    end = 16.dp
                                )
                            )
                        }
                    }
                    if (viewModel.currentRemotePath.isNotEmpty()) {
                        IconButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .height(40.dp),
                            onClick = {
                                viewModel.navigateUpRemote()
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
                        state = remoteSwipeRefreshState,
                        onRefresh = { isRefreshingRemote = true }
                    ) {
                        LazyColumn(
                            state = remoteListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 8.dp)
                                .onGloballyPositioned { coordinates ->
                                    remoteListPosition = coordinates.localToRoot(Offset.Zero)
                                    remoteListSize = coordinates.size
                                }
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val itemHeight = 50.dp.toPx()
                                            val scrollOffset = remoteListState.firstVisibleItemIndex * itemHeight + remoteListState.firstVisibleItemScrollOffset
                                            val adjustedY = offset.y + scrollOffset
                                            val index = (adjustedY / itemHeight).toInt()
                                            val file = viewModel.remoteFiles.getOrNull(index)
                                            draggedFile = file
                                            dragOffset = remoteListPosition?.plus(offset) ?: offset
                                            dragSource = "remote"
                                        },
                                        onDragEnd = {
                                            if (draggedFile != null && dragOffset != null && dragSource == "remote") {
                                                val localYTop = localListPosition?.y ?: 0f
                                                val localYBottom = localYTop + (localListSize?.height ?: 0)
                                                if (dragOffset!!.y in localYTop..localYBottom) {
                                                    isDownloading = true
                                                    viewModel.downloadFileOrFolder(draggedFile!!)
                                                }
                                            }
                                            draggedFile = null
                                            dragOffset = null
                                            dragSource = null
                                        },
                                        onDragCancel = {
                                            draggedFile = null
                                            dragOffset = null
                                            dragSource = null
                                        },
                                        onDrag = { _, dragAmount ->
                                            dragOffset = dragOffset?.plus(dragAmount)
                                        }
                                    )
                                }
                        ) {
                            items(viewModel.remoteFiles, key = { it.path + it.fileName }) { file ->
                                FileItem(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            viewModel.openRemoteFolder(file)
                                        } else {
                                            viewModel.openRemoteFile(file)
                                        }
                                    },
                                    onSwipeToDelete = {
                                        fileToDelete = file
                                        isRemoteFile = true
                                        showDeleteDialog = true
                                    },
                                    refreshTrigger = refreshTrigger
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White, shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .padding(top = 5.dp, start = 5.dp, end = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color = colorResource(R.color.light_gray_ED), shape = RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = stringResource(R.string.title_phone_drive),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, top = 3.dp, bottom = 3.dp)
                        )
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
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val itemHeight = 50.dp.toPx()
                                            val scrollOffset = localListState.firstVisibleItemIndex * itemHeight + localListState.firstVisibleItemScrollOffset
                                            val adjustedY = offset.y + scrollOffset
                                            val index = (adjustedY / itemHeight).toInt()
                                            val file = viewModel.localFiles.getOrNull(index)
                                            draggedFile = file
                                            dragOffset = localListPosition?.plus(offset) ?: offset
                                            dragSource = "local"
                                        },
                                        onDragEnd = {
                                            if (draggedFile != null && dragOffset != null && dragSource == "local") {
                                                val remoteYTop = remoteListPosition?.y ?: 0f
                                                val remoteYBottom = remoteYTop + (remoteListSize?.height ?: 0)
                                                if (dragOffset!!.y in remoteYTop..remoteYBottom) {
                                                    isUploading = true
                                                    viewModel.uploadFileOrFolder(draggedFile!!)
                                                }
                                            }
                                            draggedFile = null
                                            dragOffset = null
                                            dragSource = null
                                        },
                                        onDragCancel = {
                                            draggedFile = null
                                            dragOffset = null
                                            dragSource = null
                                        },
                                        onDrag = { _, dragAmount ->
                                            dragOffset = dragOffset?.plus(dragAmount)
                                        }
                                    )
                                }
                        ) {
                            items(viewModel.localFiles, key = { it.path + it.fileName }) { file ->
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
                                        isRemoteFile = false
                                        showDeleteDialog = true
                                    },
                                    refreshTrigger = refreshTrigger
                                )
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

            if (isUploading || isDownloading) {
                ProgressDialog(
                    title = if (isUploading) stringResource(R.string.uploading) else stringResource(R.string.downloading),
                    content = transferSpeed,
                    progress = transferProgress
                )
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
                                viewModel.deleteFile(fileToDelete!!, isRemoteFile)
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