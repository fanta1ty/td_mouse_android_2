@file:OptIn(ExperimentalMaterial3Api::class)

package com.sg.aimouse.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.sg.aimouse.R
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.navigation.NavGraph
import com.sg.aimouse.presentation.screen.home.state.HomeStateHolder
import com.sg.aimouse.presentation.ui.theme.AiMouseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SuppressLint("SourceLockedOrientationActivity")
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        setContent {
            val stateHolder = rememberHomeStateHolder()

            AiMouseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ModalNavigationDrawer(
                        drawerState = stateHolder.drawerState,
                        drawerContent = {
                            Spacer(modifier = Modifier.height(16.dp))

                            ModalDrawerSheet {
                                stateHolder.drawerItems.forEachIndexed { index, item ->
                                    NavigationDrawerItem(
                                        modifier = Modifier
                                            .padding(NavigationDrawerItemDefaults.ItemPadding),
                                        selected = index == stateHolder.selectedDrawerIndex,
                                        label = { Text(item.title) },
                                        icon = {
                                            Icon(
                                                painter = painterResource(item.drawableIcon),
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            if (stateHolder.selectedDrawerIndex != index) {
                                                stateHolder.navController.navigate(item.destination) {
                                                    popUpTo(
                                                        stateHolder.drawerItems[stateHolder.selectedDrawerIndex].destination
                                                    ) { inclusive = true }
                                                }

                                                stateHolder.updateSelectedDrawerIndex(index)
                                            }

                                            stateHolder.coroutineScope.launch {
                                                stateHolder.drawerState.close()
                                            }
                                        }
                                    )
                                }
                            }
                        }
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
                            CompositionLocalProvider(LocalActivity provides this) {
                                NavGraph(innerPaddings, stateHolder.navController)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberHomeStateHolder(
    context: Context = LocalContext.current,
    navController: NavHostController = rememberNavController(),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
): HomeStateHolder {
    return remember { HomeStateHolder(context, navController, coroutineScope, drawerState) }
}