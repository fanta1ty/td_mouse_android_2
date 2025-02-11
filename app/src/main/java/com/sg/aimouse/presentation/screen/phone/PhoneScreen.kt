@file:OptIn(ExperimentalMaterialApi::class)

package com.sg.aimouse.presentation.screen.phone

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sg.aimouse.R
import com.sg.aimouse.presentation.component.FileItem
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.component.LocalParentViewModel
import com.sg.aimouse.presentation.component.ProgressDialog
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import com.sg.aimouse.presentation.screen.phone.state.PhoneStateHolder

@Composable
fun PhoneScreen() {
    val stateHolder = rememberPhoneStateHolder()
    val viewModel = stateHolder.viewModel
    BackHandler(onBack = stateHolder::navigateBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(stateHolder.pullRefreshState)
    ) {
        if (!viewModel.localFiles.isEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items = viewModel.localFiles) { index, item ->
                    FileItem(item) { file ->
                        if (!file.isDirectory) {
                            stateHolder.onFileItemClick(file)
                        }
                    }

                    if (index < viewModel.localFiles.lastIndex) {
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
        ProgressDialog(
            title = stringResource(R.string.transferring_file),
            content = viewModel.transferSpeed.toString(),
            progress = viewModel.transferProgress
        )
    }
}

@Composable
fun rememberPhoneStateHolder(
    activity: ComponentActivity = LocalActivity.current,
    viewModel: HomeViewModel = LocalParentViewModel.current as HomeViewModel,
    pullRefreshState: PullRefreshState = rememberPullRefreshState(
        refreshing = false,
        onRefresh = { viewModel.retrieveLocalFiles() }
    )
): PhoneStateHolder {
    return remember { PhoneStateHolder(activity, viewModel, pullRefreshState) }
}