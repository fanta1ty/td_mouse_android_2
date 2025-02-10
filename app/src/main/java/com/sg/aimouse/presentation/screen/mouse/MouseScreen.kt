@file:OptIn(ExperimentalMaterialApi::class)

package com.sg.aimouse.presentation.screen.mouse

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sg.aimouse.R
import com.sg.aimouse.presentation.component.FileItem
import com.sg.aimouse.presentation.component.LoadingDialog
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.component.LocalParentViewModel
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import com.sg.aimouse.presentation.screen.mouse.state.MouseStateHolder

@Composable
fun MouseScreen() {
    val stateHolder = rememberMouseStateHolder()
    val viewModel = stateHolder.viewModel
    BackHandler(onBack = stateHolder::navigateBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(stateHolder.pullRefreshState)
    ) {
        if (!viewModel.remoteFiles.isEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val files = viewModel.remoteFiles
                itemsIndexed(items = files) { index, item ->
                    FileItem(item) { file -> stateHolder.onFileItemClick(file) }

                    if (index < files.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(8.dp),
                            thickness = 1.dp
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    modifier = Modifier.size(200.dp),
                    painter = painterResource(R.drawable.ic_empty_file),
                    contentDescription = null
                )
            }
        }

        PullRefreshIndicator(
            modifier = Modifier.align(Alignment.TopCenter),
            refreshing = false,
            state = stateHolder.pullRefreshState,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }

    if (viewModel.isTransferringFileSMB) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                progress = {
                    viewModel.transferProgress / 100f // Convert percentage to 0-1 range
                },
                modifier = Modifier.size(80.dp),
                strokeWidth = 6.dp,
                trackColor = ProgressIndicatorDefaults.circularTrackColor,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Transferring... ${viewModel.transferProgress.toInt()}%",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Speed: ${viewModel.transferSpeed} KB/s",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun rememberMouseStateHolder(
    activity: ComponentActivity = LocalActivity.current,
    viewModel: HomeViewModel = LocalParentViewModel.current as HomeViewModel,
    pullRefreshState: PullRefreshState = rememberPullRefreshState(
        refreshing = false,
        onRefresh = { viewModel.retrieveRemoteFilesSMB() }
    )
): MouseStateHolder {
    return remember { MouseStateHolder(activity, viewModel, pullRefreshState) }
}