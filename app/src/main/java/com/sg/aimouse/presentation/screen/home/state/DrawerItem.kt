package com.sg.aimouse.presentation.screen.home.state

import androidx.annotation.DrawableRes

data class DrawerItem(
    val destination: String,
    val title: String,
    @DrawableRes val drawableIcon: Int,
)
