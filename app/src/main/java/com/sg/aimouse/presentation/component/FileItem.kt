package com.sg.aimouse.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sg.aimouse.R
import com.sg.aimouse.model.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FileItem(
    file: File,
    onClick: (File) -> Unit,
    onSwipeToDelete: (File) -> Unit,
    refreshTrigger: Int
) {
    val dismissState = remember(file.path + file.fileName) {
        DismissState(
            initialValue = DismissValue.Default,
            confirmStateChange = { dismissValue ->
                if (dismissValue == DismissValue.DismissedToStart) {
                    onSwipeToDelete(file)
                    true
                } else {
                    false
                }
            }
        )
    }

    LaunchedEffect(refreshTrigger) {
        if (dismissState.currentValue != DismissValue.Default) {
            dismissState.reset()
        }
    }

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart),
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "Delete",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        },
        dismissContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Color.White)
                    .noRippleClickable { onClick(file) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    modifier = Modifier.size(30.dp),
                    painter = painterResource(getFileIcon(file)),
                    contentDescription = null,
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        file.shortenFileName,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    Row {
                        Column {
                            if (!file.isDirectory) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    file.formatedFileSize,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        Column (
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ){
                            val formattedTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                                .format(Date(file.createdTime))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formattedTime,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    )
}

private fun getFileIcon(file: File): Int {
    if (file.isDirectory) {
        return R.drawable.ic_folder
    }

    val extension = file.fileName.substringAfterLast(".", "").lowercase()
    return when (extension) {
        "txt" -> R.drawable.ic_document
        "jpg", "jpeg", "png" -> R.drawable.ic_picture
        "mp3", "wav" -> R.drawable.ic_music
        "mp4", "avi" -> R.drawable.ic_video
        else -> R.drawable.ic_file
    }
}