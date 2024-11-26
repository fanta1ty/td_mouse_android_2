package com.sg.aimouse.presentation.screen.home.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.sg.aimouse.presentation.screen.home.state.DrawerItem

@Composable
fun NavDrawer(
    selectedDrawerIndex: Int,
    drawerState: DrawerState,
    drawerItems: List<DrawerItem>,
    onItemClick: (Int) -> Unit,
    content: @Composable (() -> Unit)
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        content = content,
        drawerContent = {
            ModalDrawerSheet {
                drawerItems.forEachIndexed { index, item ->
                    NavigationDrawerItem(
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding),
                        selected = index == selectedDrawerIndex,
                        label = { Text(item.title) },
                        icon = {
                            Icon(
                                painter = painterResource(item.drawableIcon),
                                contentDescription = null
                            )
                        },
                        onClick = { onItemClick(index) }
                    )
                }
            }
        }
    )
}