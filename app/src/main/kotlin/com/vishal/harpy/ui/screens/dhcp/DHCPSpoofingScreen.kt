package com.vishal.harpy.ui.screens.dhcp

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
fun DHCPSpoofingScreen(
    viewModel: NetworkMonitorViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {}
) {
    var showStartDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showRemoveRuleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DHCP Spoofing") },
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
                            text = "DHCP Spoofing",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "Intercept and modify DHCP responses to assign custom network configurations to target devices. Requires root access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { showStartDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start DHCP Spoofing")
            }

            Button(
                onClick = { viewModel.stopDHCPSpoofing() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stop DHCP Spoofing")
            }

            OutlinedButton(
                onClick = { showAddRuleDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add DHCP Rule")
            }

            OutlinedButton(
                onClick = { showRemoveRuleDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Remove DHCP Rule")
            }

            OutlinedButton(
                onClick = {
                    val isActive = viewModel.isDHCPSpoofingActive()
                    // Show status
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Check DHCP Status")
            }
        }
    }

    if (showStartDialog) {
        StartDHCPSpoofingDialog(
            onConfirm = { targetMac, spoofedIp, gatewayIp, dnsServer, interface_ ->
                viewModel.startDHCPSpoofing(
                    interfaceName = interface_,
                    targetMacs = arrayOf(targetMac),
                    spoofedIPs = arrayOf(spoofedIp),
                    gatewayIPs = arrayOf(gatewayIp),
                    subnetMasks = arrayOf("255.255.255.0"),
                    dnsServers = arrayOf(dnsServer)
                )
                showStartDialog = false
            },
            onDismiss = { showStartDialog = false }
        )
    }

    if (showAddRuleDialog) {
        AddDHCPRuleDialog(
            onConfirm = { targetMac, spoofedIp ->
                // Add rule logic
                showAddRuleDialog = false
            },
            onDismiss = { showAddRuleDialog = false }
        )
    }

    if (showRemoveRuleDialog) {
        RemoveDHCPRuleDialog(
            onConfirm = { targetMac ->
                // Remove rule logic
                showRemoveRuleDialog = false
            },
            onDismiss = { showRemoveRuleDialog = false }
        )
    }
}

@Composable
fun StartDHCPSpoofingDialog(
    onConfirm: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var targetMac by remember { mutableStateOf("aa:bb:cc:dd:ee:ff") }
    var spoofedIp by remember { mutableStateOf("192.168.1.100") }
    var gatewayIp by remember { mutableStateOf("192.168.1.1") }
    var dnsServer by remember { mutableStateOf("8.8.8.8") }
    var interface_ by remember { mutableStateOf("wlan0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start DHCP Spoofing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetMac,
                    onValueChange = { targetMac = it },
                    label = { Text("Target MAC") },
                    placeholder = { Text("aa:bb:cc:dd:ee:ff") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = spoofedIp,
                    onValueChange = { spoofedIp = it },
                    label = { Text("Spoofed IP") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = gatewayIp,
                    onValueChange = { gatewayIp = it },
                    label = { Text("Gateway IP") },
                    placeholder = { Text("192.168.1.1") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = dnsServer,
                    onValueChange = { dnsServer = it },
                    label = { Text("DNS Server") },
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
                onClick = { onConfirm(targetMac, spoofedIp, gatewayIp, dnsServer, interface_) },
                enabled = targetMac.isNotBlank() && spoofedIp.isNotBlank() && 
                         gatewayIp.isNotBlank() && dnsServer.isNotBlank()
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
fun AddDHCPRuleDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var targetMac by remember { mutableStateOf("aa:bb:cc:dd:ee:ff") }
    var spoofedIp by remember { mutableStateOf("192.168.1.100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add DHCP Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetMac,
                    onValueChange = { targetMac = it },
                    label = { Text("Target MAC") },
                    placeholder = { Text("aa:bb:cc:dd:ee:ff") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = spoofedIp,
                    onValueChange = { spoofedIp = it },
                    label = { Text("Spoofed IP") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(targetMac, spoofedIp) },
                enabled = targetMac.isNotBlank() && spoofedIp.isNotBlank()
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
fun RemoveDHCPRuleDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var targetMac by remember { mutableStateOf("aa:bb:cc:dd:ee:ff") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove DHCP Rule") },
        text = {
            OutlinedTextField(
                value = targetMac,
                onValueChange = { targetMac = it },
                label = { Text("Target MAC") },
                placeholder = { Text("aa:bb:cc:dd:ee:ff") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(targetMac) },
                enabled = targetMac.isNotBlank()
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
