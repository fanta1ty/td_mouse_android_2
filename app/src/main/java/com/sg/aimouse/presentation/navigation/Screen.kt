package com.sg.aimouse.presentation.navigation

sealed class Screen(val route: String) {
    data object HomeScreen : Screen("home")
    data object PhoneScreen : Screen("phone")
    data object MouseScreen : Screen("mouse")
}