package com.sg.aimouse.presentation.screen.home.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sg.aimouse.presentation.screen.home.state.DrawerItem
import com.sg.aimouse.presentation.ui.theme.Red100

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
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.85f),
                drawerContainerColor = Color.White
            ) {
                Spacer(Modifier.height(32.dp))

                drawerItems.forEachIndexed { index, item ->
                    NavigationDrawerItem(
                        selected = index == selectedDrawerIndex,
                        onClick = { onItemClick(index) },
                        label = { Text(item.title) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Red100
                        ),
                        icon = {
                            Icon(
                                painter = painterResource(item.drawableIcon),
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    )
}