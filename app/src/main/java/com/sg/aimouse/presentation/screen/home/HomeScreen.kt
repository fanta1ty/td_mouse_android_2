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
import androidx.compose.ui.res.painterResource
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
import androidx.navigation.NavController
import com.sg.aimouse.presentation.navigation.Screen

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

    LaunchedEffect(Unit) {
        connectionViewModel.connectSMB(
            ipAddress = "192.168.1.1",
            username = "smbuser",
            password = "123456",
            rootDir = "sambashare"
        ) { isConnected ->
            if (isConnected) {
                // Optionally handle successful connection, e.g., show a toast or log
                println("Samba connected successfully!")
            } else {
                // Optionally handle failed connection
                println("Samba connection failed: ${connectionViewModel.lastErrorMessage}")
            }
        }
    }

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
                }
                Lifecycle.Event.ON_STOP -> stateHolder.viewModel.updateSMBState(SMBState.DISCONNECTED)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
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
                            IconButton(
                                onClick = {
                                    navController?.navigate(Screen.ConnectionScreen.route)
                                },
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow),
                                    contentDescription = "Samba Connect",
                                    tint = Color.Black
                                )
                            }
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