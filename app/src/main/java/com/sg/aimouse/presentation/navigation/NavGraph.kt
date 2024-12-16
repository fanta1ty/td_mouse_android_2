package com.sg.aimouse.presentation.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sg.aimouse.presentation.component.LocalNavController
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import com.sg.aimouse.presentation.screen.mouse.MouseScreen
import com.sg.aimouse.presentation.screen.phone.PhoneScreen
import com.sg.aimouse.util.sharedViewModel
import com.sg.aimouse.util.viewModelFactory

@Composable
fun NavGraph(
    innerPaddings: PaddingValues,
    navController: NavHostController
) {
    val context = LocalContext.current

    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = Screen.PhoneScreen.route
        ) {
            composable(route = Screen.PhoneScreen.route) { entry ->
                val sharedViewModel = entry.sharedViewModel<HomeViewModel>(
                    navController, viewModelFactory { HomeViewModel(context) }
                )

                PhoneScreen(innerPaddings, sharedViewModel)
            }

            composable(route = Screen.MouseScreen.route) { entry ->
                val sharedViewModel = entry.sharedViewModel<HomeViewModel>(
                    navController, viewModelFactory { HomeViewModel(context) }
                )

                MouseScreen(innerPaddings, sharedViewModel)
            }
        }
    }
}