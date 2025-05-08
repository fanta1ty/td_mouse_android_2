package com.sg.aimouse.presentation.screen.bletest

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.navigation.Screen
import com.sg.aimouse.util.BLEConnectionTester
import com.sg.aimouse.presentation.screen.connect.ConnectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BLETestScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    var connectionDetails by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var sambaConnectionResult by remember { mutableStateOf("") }

    val connectionViewModel = remember { ConnectionViewModel(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kiểm tra kết nối BLE") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hiển thị thông tin kết nối
            Text(
                text = "Thông tin kết nối BLE",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = connectionDetails,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Nút kiểm tra kết nối
            Button(
                onClick = {
                    isLoading = true
//                    connectionDetails = BLEConnectionTester.getConnectionDetails(context)

                    BLEConnectionTester.testConnection(context) { success, message ->
                        testResult = if (success) {
                            "✅ Kết nối BLE hoạt động: $message"
                        } else {
                            "❌ Kết nối BLE không hoạt động: $message"
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}