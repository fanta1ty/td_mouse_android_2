package com.sg.aimouse.presentation.screen.home.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sg.aimouse.R
import com.sg.aimouse.model.File
import com.sg.aimouse.presentation.component.noRippleClickable

@Composable
fun FileListColumn(
    files: List<File>,
    onItemClick: (File) -> Unit,
    header: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(color = Color.White)
    ) {
        header()

        if (files.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items = files) { index, item ->
                    FileItem(item, onClick = onItemClick)

                    if (index < files.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(8.dp),
                            thickness = 1.dp
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    modifier = Modifier.size(200.dp),
                    painter = painterResource(R.drawable.ic_empty_file),
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun MouseColumnHeader(
    onTransfer: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.files_on_tdmouse),
            fontWeight = FontWeight.W600
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .noRippleClickable(onClick = onDelete),
                painter = painterResource(R.drawable.ic_delete),
                tint = Color.Unspecified,
                contentDescription = null,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .noRippleClickable(onClick = onTransfer),
                painter = painterResource(R.drawable.ic_file_transfer_right),
                tint = Color.Unspecified,
                contentDescription = null,
            )
        }
    }
}

@Composable
fun PhoneColumnHeader(
    onTransfer: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .size(24.dp)
                .noRippleClickable(onClick = onTransfer),
            painter = painterResource(R.drawable.ic_file_transfer_left),
            tint = Color.Unspecified,
            contentDescription = null,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            modifier = Modifier
                .size(24.dp)
                .noRippleClickable(onClick = onDelete),
            painter = painterResource(R.drawable.ic_delete),
            tint = Color.Unspecified,
            contentDescription = null,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                stringResource(R.string.files_on_local),
                fontWeight = FontWeight.W600
            )
        }
    }
}