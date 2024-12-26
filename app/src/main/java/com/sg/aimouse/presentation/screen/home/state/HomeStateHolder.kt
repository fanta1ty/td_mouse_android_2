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
import com.sg.aimouse.presentation.screen.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
class HomeStateHolder(
    context: Context,
    val viewModel: HomeViewModel,
    val navController: NavHostController,
    val coroutineScope: CoroutineScope,
    val drawerState: DrawerState
) {
    var selectedDrawerIndex by mutableIntStateOf(0)
        private set

    val drawerItems = listOf<DrawerItem>(
        DrawerItem(
            Screen.MouseScreen.route,
            context.getString(R.string.files_on_tdmouse),
            R.drawable.ic_mouse
        ),
        DrawerItem(
            Screen.PhoneScreen.route,
            context.getString(R.string.files_on_local),
            R.drawable.ic_phone
        )
    )

    fun updateSelectedDrawerIndex(index: Int) {
        selectedDrawerIndex = index
    }

    fun onDrawerItemClick(index: Int) {
        if (selectedDrawerIndex != index) {
            navController.navigate(drawerItems[index].destination) {
                popUpTo(
                    drawerItems[selectedDrawerIndex].destination
                ) { inclusive = true }
            }
            updateSelectedDrawerIndex(index)
        }
        coroutineScope.launch { drawerState.close() }
    }
}