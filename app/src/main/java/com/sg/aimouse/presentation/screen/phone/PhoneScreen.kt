package com.sg.aimouse.presentation.screen.phone

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sg.aimouse.presentation.component.FileItem
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.screen.phone.state.PhoneStateHolder
import com.sg.aimouse.util.viewModelFactory

@Composable
fun PhoneScreen(innerPaddings: PaddingValues) {
    val stateHolder = rememberPhoneStateHolder()
    val viewModel = stateHolder.viewModel
    BackHandler(onBack = stateHolder::navigateBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPaddings)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(items = viewModel.files) { index, item ->
                FileItem(item) { _ ->
                    Toast.makeText(
                        stateHolder.activity,
                        "Currently in development",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                if (index < viewModel.files.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(8.dp),
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}

@Composable
fun rememberPhoneStateHolder(
    activity: ComponentActivity = LocalActivity.current,
    viewModel: PhoneViewModel = viewModel(factory = viewModelFactory { PhoneViewModel() })
): PhoneStateHolder {
    return remember { PhoneStateHolder(activity, viewModel) }
}