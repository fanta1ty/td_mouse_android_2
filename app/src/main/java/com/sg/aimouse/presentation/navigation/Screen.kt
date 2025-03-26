package com.sg.aimouse.presentation.navigation

sealed class Screen(val route: String) {
    data object ConnectionScreen : Screen("connection")
    data object HomeScreen : Screen("home")
    data object PhoneScreen : Screen("phone")
    data object MouseScreen : Screen("mouse")
}