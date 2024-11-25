package com.sg.aimouse.presentation.screen.home.state

import android.content.Context
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.sg.aimouse.R
import com.sg.aimouse.presentation.navigation.Screen
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterialApi::class)
class HomeStateHolder(
    val context: Context,
    val navController: NavHostController,
    val coroutineScope: CoroutineScope,
    val drawerState: DrawerState
) {
    var selectedDrawerIndex by mutableIntStateOf(0)
        private set

    val drawerItems = listOf<DrawerItem>(
        DrawerItem(
            Screen.PhoneScreen.route,
            context.getString(R.string.from_phone),
            R.drawable.ic_phone
        ),
        DrawerItem(
            Screen.MouseScreen.route,
            context.getString(R.string.from_mouse),
            R.drawable.ic_mouse
        )
    )

    fun updateSelectedDrawerIndex(index: Int) {
        selectedDrawerIndex = index
    }
}