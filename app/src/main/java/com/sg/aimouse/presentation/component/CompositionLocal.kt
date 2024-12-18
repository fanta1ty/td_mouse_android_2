package com.sg.aimouse.presentation.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController

val LocalNavController = compositionLocalOf<NavHostController> {
    noLocalValueProvidedFor("LocalNavController")
}

val LocalActivity = compositionLocalOf<ComponentActivity> {
    noLocalValueProvidedFor("LocalActivity")
}

val LocalParentViewModel = compositionLocalOf<ViewModel> {
    noLocalValueProvidedFor("LocalParentViewModel")
}

@Suppress("SameParameterValue")
private fun noLocalValueProvidedFor(name: String): Nothing {
    error("$name not yet provided")
}