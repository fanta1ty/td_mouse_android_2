@file:OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)

package com.sg.aimouse.presentation.screen.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sg.aimouse.R
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.screen.home.component.HomeLifecycleEffect
import com.sg.aimouse.presentation.screen.home.component.HomePager
import com.sg.aimouse.presentation.screen.home.component.StoragePermissionRequiredDialog
import com.sg.aimouse.presentation.screen.home.state.HomeStateHolder
import com.sg.aimouse.util.viewModelFactory

@Composable
fun HomeScreen() {
    val stateHolder = rememberHomeStateHolder()

    HomeLifecycleEffect(stateHolder)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { innerPaddings -> HomePager(innerPaddings, stateHolder) }

    StoragePermissionRequiredDialog(
        shouldShow = stateHolder.shouldShowStoragePermissionRequiredDialog,
        onPositiveClickEvent = stateHolder::navigateToAppPermissionSettings
    )
}

@Composable
fun rememberHomeStateHolder(
    activity: ComponentActivity = LocalActivity.current,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    viewModel: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(activity) }),
    pagerState: PagerState = PagerState { 2 },
    pullRefreshState: PullRefreshState = rememberPullRefreshState(
        refreshing = false,
        onRefresh = {
            viewModel.retrieveLocalFiles()
            viewModel.retrieveRemoteFilesSMB()
        }
    )
): HomeStateHolder {
    return remember {
        HomeStateHolder(
            activity, lifecycleOwner, viewModel, pagerState, pullRefreshState
        )
    }
}