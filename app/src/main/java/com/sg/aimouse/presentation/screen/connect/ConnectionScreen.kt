package com.sg.aimouse.presentation.screen.connect

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.sg.aimouse.R
import com.sg.aimouse.presentation.component.LocalActivity
import com.sg.aimouse.presentation.component.ProgressDialog
import com.sg.aimouse.presentation.navigation.Screen
import com.sg.aimouse.util.viewModelFactory

@Composable
fun ConnectionScreen(
    activity: ComponentActivity = LocalActivity.current,
    navController: NavHostController
) {
    // Using ViewModel with Activity scope
    val viewModel: ConnectionViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = viewModelFactory { ConnectionViewModel(activity) }
    )
    var ipAddress by remember { mutableStateOf("192.168.1.32") }
    var username by remember { mutableStateOf("smbuser") }
    var password by remember { mutableStateOf("123456") }
    var rootDir by remember { mutableStateOf("sambashare") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) } // State ProgressDialog

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(300.dp).height(180.dp)
            )
            Text(
                text = stringResource(R.string.connect_to_server),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text(stringResource(R.string.ip_address)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.username)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = rootDir,
                onValueChange = { rootDir = it },
                label = { Text(stringResource(R.string.root_dir)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    isConnecting = true // Show ProgressDialog
                    viewModel.connectSMB(ipAddress, username, password, rootDir) { success ->
                        isConnecting = false // Hide ProgressDialog affter conntected
                        if (success) {
                            navController.navigate(Screen.HomeScreen.route) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        } else {
                            errorMessage = viewModel.lastErrorMessage
                            showErrorDialog = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConnecting // Disable button when connecting
            ) {
                Text(stringResource(R.string.connect))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Show ProgressDialog when connecting
        if (isConnecting) {
            ProgressDialog(
                title = stringResource(R.string.connecting),
                content = stringResource(R.string.please_wait),
                progress = 0f // circle
            )
        }

        // Show Error Dialog if error
        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = { Text(stringResource(R.string.connection_failed)) },
                text = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = { showErrorDialog = false }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            )
        }
    }
}