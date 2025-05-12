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
                title = { Text("Test BLE Connection") },
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
            // Show connection information
            Text(
                text = "BLE Connection Information",
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
            
            // button to test BLE connection
            Button(
                onClick = {
                    isLoading = true
                    connectionDetails = BLEConnectionTester.getConnectionDetails(context)
                    
                    BLEConnectionTester.testConnection(context) { success, message ->
                        testResult = if (success) {
                            "✅ BLE connection working: $message"
                        } else {
                            "❌ BLE connection not working: $message"
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
                Text("Test BLE connection")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // button to navigate to Samba connection screen
            OutlinedButton(
                onClick = {
                    navController.navigate(Screen.ConnectionScreen.route)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Navigate to Samba Connection Screen")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Show the result of the BLE connection test
            if (testResult.isNotEmpty()) {
                Text(
                    text = "Result of BLE Connection Test",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = testResult,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
            }
            
            // Show the result of the Samba connection
            if (sambaConnectionResult.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Navigate to Samba Connection",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = sambaConnectionResult,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
            }
        }
    }
    
    // Update connection details when the screen is displayed
    LaunchedEffect(Unit) {
        connectionDetails = BLEConnectionTester.getConnectionDetails(context)
    }
}
