package com.sg.aimouse.presentation.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sg.aimouse.presentation.component.LocalNavController
import com.sg.aimouse.presentation.screen.home.HomeScreen
import com.sg.aimouse.presentation.screen.mouse.MouseScreen
import com.sg.aimouse.presentation.screen.phone.PhoneScreen

@Composable
fun NavGraph(innerPaddings: PaddingValues) {
    val navController = LocalNavController.current

    NavHost(
        modifier = Modifier.padding(innerPaddings),
        navController = navController,
        startDestination = Screen.MouseScreen.route
    ) {
        composable(route = Screen.HomeScreen.route) { HomeScreen() }
        composable(route = Screen.MouseScreen.route) { MouseScreen() }
        composable(route = Screen.PhoneScreen.route) { PhoneScreen() }
    }
}