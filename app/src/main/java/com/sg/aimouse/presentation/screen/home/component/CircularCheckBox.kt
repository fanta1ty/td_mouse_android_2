package com.sg.aimouse.presentation.screen.home.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sg.aimouse.model.File
import com.sg.aimouse.presentation.component.noRippleClickable
import com.sg.aimouse.presentation.ui.theme.Orange500

@Composable
fun CircularCheckBox(
    file: File,
    onCheckedChange: (File) -> Unit,
) {
    val checkBoxColor = if (file.isSelected.value) Orange500 else Color.White

    Box(
        modifier = Modifier
            .size(24.dp)
            .background(
                color = checkBoxColor,
                shape = CircleShape
            )
            .border(BorderStroke(1.dp, Color.Gray), CircleShape)
            .noRippleClickable { onCheckedChange(file) },
        contentAlignment = Alignment.Center
    ) {
        if (file.isSelected.value) {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = Icons.Default.Check,
                tint = Color.White,
                contentDescription = null
            )
        }
    }
}