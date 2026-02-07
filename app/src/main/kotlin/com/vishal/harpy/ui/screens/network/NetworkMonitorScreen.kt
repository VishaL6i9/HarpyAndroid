package com.vishal.harpy.ui.screens.network

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.LoadingState
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel
import com.vishal.harpy.ui.screens.network.components.DeviceCard
import com.vishal.harpy.ui.screens.network.components.FilterChips
import com.vishal.harpy.ui.screens.network.components.LoadingOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkMonitorScreen(
    viewModel: NetworkMonitorViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {}
) {
    val filteredDevices by viewModel.filteredDevices.collectAsStateWithLifecycle()
    val loadingState by viewModel.loadingState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val filterIPv4 by viewModel.filterIPv4.collectAsStateWithLifecycle()
    val filterIPv6 by viewModel.filterIPv6.collectAsStateWithLifecycle()
    val scanSuccess by viewModel.scanSuccess.collectAsStateWithLifecycle()
    val testPingResult by viewModel.testPingResult.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    
    var showUnblockDialog by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<NetworkDevice?>(null) }
    var showDeviceActionsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(scanSuccess) {
        if (scanSuccess) {
            viewModel.resetScanSuccess()
        }
    }

    LaunchedEffect(testPingResult) {
        testPingResult?.let { (ip, success) ->
            val message = if (success) "Ping to $ip successful" else "Ping to $ip failed"
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.resetPingResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Monitor") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.scanNetwork() },
                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                text = { Text("Scan Network") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Filter chips
                if (filteredDevices.isNotEmpty()) {
                    FilterChips(
                        filterIPv4 = filterIPv4,
                        filterIPv6 = filterIPv6,
                        onIPv4Toggle = { viewModel.toggleIPv4Filter() },
                        onIPv6Toggle = { viewModel.toggleIPv6Filter() },
                        deviceCount = filteredDevices.size
                    )
                }

                // Unblock all button
                AnimatedVisibility(
                    visible = filteredDevices.any { it.isBlocked },
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Button(
                        onClick = { showUnblockDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unblock All Devices")
                    }
                }

                // Device list
                if (filteredDevices.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredDevices, key = { it.macAddress }) { device ->
                            DeviceCard(
                                device = device,
                                onBlockClick = {
                                    if (device.isGateway && !device.isBlocked) {
                                        selectedDevice = device
                                        showDeviceActionsSheet = true
                                    } else if (device.isBlocked) {
                                        viewModel.unblockDevice(device)
                                    } else {
                                        viewModel.blockDevice(device)
                                    }
                                },
                                onPinClick = { viewModel.toggleDevicePin(device) },
                                onEditNameClick = {
                                    selectedDevice = device
                                    showDeviceActionsSheet = true
                                },
                                onPingClick = { viewModel.testPing(device) }
                            )
                        }
                    }
                }
            }

            // Loading overlay
            LoadingOverlay(loadingState = loadingState)

            // Error snackbar
            error?.let { errorMessage ->
                LaunchedEffect(errorMessage) {
                    // Show snackbar or handle error
                }
            }
        }
    }

    // Dialogs and sheets
    if (showUnblockDialog) {
        UnblockAllDialog(
            onConfirm = {
                viewModel.unblockAllDevices()
                showUnblockDialog = false
            },
            onDismiss = { showUnblockDialog = false }
        )
    }

    if (showDeviceActionsSheet && selectedDevice != null) {
        DeviceActionsBottomSheet(
            device = selectedDevice!!,
            onDismiss = { showDeviceActionsSheet = false },
            viewModel = viewModel
        )
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DevicesOther,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No devices found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tap the scan button to discover devices on your network",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun UnblockAllDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unblock All Devices?") },
        text = { Text("This will remove all active device blocks and restore network access for all blocked devices.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Unblock All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActionsBottomSheet(
    device: NetworkDevice,
    onDismiss: () -> Unit,
    viewModel: NetworkMonitorViewModel
) {
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showNuclearWarning by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = device.deviceName ?: device.ipAddress,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = device.macAddress,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    showEditNameDialog = true
                    onDismiss()
                }
            ) {
                ListItem(
                    headlineContent = { Text("Edit Name") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.toggleDevicePin(device)
                    onDismiss()
                }
            ) {
                ListItem(
                    headlineContent = { Text(if (device.isPinned) "Unpin" else "Pin") },
                    leadingContent = { Icon(Icons.Default.PushPin, contentDescription = null) }
                )
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.testPing(device)
                    onDismiss()
                }
            ) {
                ListItem(
                    headlineContent = { Text("Test Ping") },
                    leadingContent = { Icon(Icons.Default.NetworkPing, contentDescription = null) }
                )
            }
            
            if (device.isGateway && !device.isBlocked) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showNuclearWarning = true
                    }
                ) {
                    ListItem(
                        headlineContent = { Text("Block Gateway (Nuclear)") },
                        leadingContent = { Icon(Icons.Default.Warning, contentDescription = null) }
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showEditNameDialog) {
        EditDeviceNameDialog(
            device = device,
            onConfirm = { newName ->
                viewModel.setDeviceName(device, newName)
                showEditNameDialog = false
            },
            onDismiss = { showEditNameDialog = false }
        )
    }

    if (showNuclearWarning) {
        NuclearWarningDialog(
            onConfirm = {
                viewModel.blockDevice(device)
                showNuclearWarning = false
                onDismiss()
            },
            onDismiss = { showNuclearWarning = false }
        )
    }
}

@Composable
fun EditDeviceNameDialog(
    device: NetworkDevice,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(device.deviceName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Device Name") },
        text = {
            Column {
                Text("Device: ${device.ipAddress}")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device Name") },
                    placeholder = { Text("e.g., My Laptop, Guest Phone") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.takeIf { it.isNotBlank() }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NuclearWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text("⚠️ NUCLEAR OPTION ⚠️") },
        text = { Text("Are you sure? Blocking the Gateway will disconnect EVERY device on this WiFi network from the internet.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("ACTIVATE NUCLEAR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abort")
            }
        }
    )
}
