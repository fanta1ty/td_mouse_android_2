package com.sg.aimouse.presentation.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.compositionLocalOf

val LocalActivity = compositionLocalOf<ComponentActivity> {
    noLocalValueProvidedFor("LocalActivity")
}

@Suppress("SameParameterValue")
private fun noLocalValueProvidedFor(name: String): Nothing {
    error("$name not yet provided")
}