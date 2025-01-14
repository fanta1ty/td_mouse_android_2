@file:OptIn(ExperimentalMaterial3Api::class)

package com.sg.aimouse.presentation.screen.home

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.sg.aimouse.R
import com.sg.aimouse.presentation.component.LocalNavController
import com.sg.aimouse.presentation.component.LocalParentViewModel
import com.sg.aimouse.presentation.navigation.NavGraph
import com.sg.aimouse.presentation.screen.home.component.NavDrawer
import com.sg.aimouse.presentation.screen.home.state.HomeStateHolder
import com.sg.aimouse.util.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val stateHolder = rememberHomeStateHolder()

    NavDrawer(
        stateHolder.selectedDrawerIndex,
        stateHolder.drawerState,
        stateHolder.drawerItems,
        onItemClick = stateHolder::onDrawerItemClick
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            stateHolder.coroutineScope.launch {
                                stateHolder.drawerState.open()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        ) { innerPaddings ->
            CompositionLocalProvider(
                LocalNavController provides stateHolder.navController,
                LocalParentViewModel provides stateHolder.viewModel
            ) { NavGraph(innerPaddings) }
        }
    }
}

@Composable
fun rememberHomeStateHolder(
    context: Context = LocalContext.current,
    viewModel: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(context) }),
    navController: NavHostController = rememberNavController(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
): HomeStateHolder {
    return remember {
        HomeStateHolder(
            context, viewModel, navController, coroutineScope, drawerState
        )
    }
}