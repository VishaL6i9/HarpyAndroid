package com.vishal.harpy.ui.screens.dns

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DNSSpoofingScreen(
    viewModel: NetworkMonitorViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {}
) {
    var showStartDialog by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showRemoveRuleDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS Spoofing") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "DNS Spoofing",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "Redirect DNS queries for specific domains to custom IP addresses. Requires root access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { showStartDialog = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = "Start DNS Spoofing",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Button(
                onClick = { showStopDialog = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = "Stop DNS Spoofing",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            OutlinedButton(
                onClick = { showAddRuleDialog = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = "Add DNS Rule",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            OutlinedButton(
                onClick = { showRemoveRuleDialog = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = "Remove DNS Rule",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            OutlinedButton(
                onClick = { showStatusDialog = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = "Check DNS Status",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    if (showStartDialog) {
        StartDNSSpoofingDialog(
            onConfirm = { domain, ip, interface_ ->
                viewModel.startDNSSpoofing(domain, ip, interface_)
                showStartDialog = false
            },
            onDismiss = { showStartDialog = false }
        )
    }

    if (showStopDialog) {
        StopDNSSpoofingDialog(
            onConfirm = { domain ->
                viewModel.stopDNSSpoofing(domain)
                showStopDialog = false
            },
            onDismiss = { showStopDialog = false }
        )
    }

    if (showAddRuleDialog) {
        AddDNSRuleDialog(
            onConfirm = { domain, ip ->
                // Add rule logic
                showAddRuleDialog = false
            },
            onDismiss = { showAddRuleDialog = false }
        )
    }

    if (showRemoveRuleDialog) {
        RemoveDNSRuleDialog(
            onConfirm = { domain ->
                // Remove rule logic
                showRemoveRuleDialog = false
            },
            onDismiss = { showRemoveRuleDialog = false }
        )
    }

    if (showStatusDialog) {
        CheckDNSStatusDialog(
            onConfirm = { domain ->
                val isActive = viewModel.isDNSSpoofingActive(domain)
                // Show status
                showStatusDialog = false
            },
            onDismiss = { showStatusDialog = false }
        )
    }
}

@Composable
fun StartDNSSpoofingDialog(
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("example.com") }
    var ip by remember { mutableStateOf("8.8.8.8") }
    var interface_ by remember { mutableStateOf("wlan0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start DNS Spoofing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain") },
                    placeholder = { Text("example.com") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Spoofed IP") },
                    placeholder = { Text("8.8.8.8") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = interface_,
                    onValueChange = { interface_ = it },
                    label = { Text("Network Interface") },
                    placeholder = { Text("wlan0") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(domain, ip, interface_) },
                enabled = domain.isNotBlank() && ip.isNotBlank()
            ) {
                Text("Start")
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
fun StopDNSSpoofingDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("example.com") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop DNS Spoofing") },
        text = {
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain") },
                placeholder = { Text("example.com") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(domain) },
                enabled = domain.isNotBlank()
            ) {
                Text("Stop")
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
fun AddDNSRuleDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("test.example.com") }
    var ip by remember { mutableStateOf("8.8.8.8") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add DNS Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain") },
                    placeholder = { Text("example.com") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Spoofed IP") },
                    placeholder = { Text("8.8.8.8") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(domain, ip) },
                enabled = domain.isNotBlank() && ip.isNotBlank()
            ) {
                Text("Add")
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
fun RemoveDNSRuleDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("example.com") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove DNS Rule") },
        text = {
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain") },
                placeholder = { Text("example.com") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(domain) },
                enabled = domain.isNotBlank()
            ) {
                Text("Remove")
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
fun CheckDNSStatusDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("example.com") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check DNS Status") },
        text = {
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain") },
                placeholder = { Text("example.com") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(domain) },
                enabled = domain.isNotBlank()
            ) {
                Text("Check")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
