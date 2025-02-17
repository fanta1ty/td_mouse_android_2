package com.sg.aimouse.presentation.screen.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sg.aimouse.presentation.screen.home.state.HomeStateHolder
import com.sg.aimouse.presentation.ui.theme.Gray200
import com.sg.aimouse.presentation.ui.theme.Orange500

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomePager(
    innerPaddings: PaddingValues,
    stateHolder: HomeStateHolder,
) {
    val viewModel = stateHolder.viewModel

    HorizontalPager(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPaddings)
            .background(Gray200),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 24.dp),
        pageSpacing = 16.dp,
        state = stateHolder.pagerState
    ) { page ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(stateHolder.pullRefreshState),
        ) {
            when (page) {
                0 -> { // Mouse
                    FileListColumn(
                        files = viewModel.remoteFiles,
                        onItemClick = stateHolder::onMouseFileItemClick,
                        header = {
                            MouseColumnHeader(
                                onDelete = {},
                                onTransfer = {}
                            )
                        }
                    )
                }

                1 -> { // Phone
                    FileListColumn(
                        files = viewModel.localFiles,
                        onItemClick = stateHolder::onPhoneFileItemClick,
                        header = {
                            PhoneColumnHeader(
                                onDelete = {},
                                onTransfer = {}
                            )
                        }
                    )
                }
            }

            PullRefreshIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                refreshing = false,
                state = stateHolder.pullRefreshState,
                contentColor = Orange500
            )
        }
    }
}