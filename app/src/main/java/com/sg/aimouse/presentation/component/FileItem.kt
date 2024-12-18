package com.sg.aimouse.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sg.aimouse.R
import com.sg.aimouse.model.File

@Composable
fun FileItem(file: File, onClick: (File) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 8.dp)
            .noRippleClickable { onClick(file) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            modifier = Modifier.size(30.dp),
            painter = if (!file.isDirectory) {
                painterResource(R.drawable.ic_file)
            } else {
                painterResource(R.drawable.ic_folder)
            },
            contentDescription = null,
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                file.shortenFileName,
                fontSize = 16.sp,
                color = Color.Black
            )

            if (!file.isDirectory) {
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    file.formatedFileSize,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}