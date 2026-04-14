package com.vishal.harpy.ui.screens.dhcp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel
import com.vishal.harpy.core.utils.SpoofingSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DHCPSpoofingScreen(
    viewModel: NetworkMonitorViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {},
    onManageSessions: () -> Unit = {}
) {
    val dhcpSessions by viewModel.dhcpSessions.collectAsStateWithLifecycle()
    var showStartDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DHCP Spoofing") },
                actions = {
                    IconButton(onClick = onManageSessions) {
                        Icon(Icons.Default.List, contentDescription = "Manage Sessions")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showStartDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New DHCP Rule")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Info Card
            ActiveDhcpHeader()

            if (dhcpSessions.isEmpty()) {
                EmptySessionsState(
                    title = "DHCP Spoofing",
                    description = "Intercept and modify DHCP responses to assign custom network configurations to target devices. Requires root access.",
                    icon = Icons.Default.Info
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "DHCP Rules (${dhcpSessions.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(dhcpSessions, key = { it.id }) { session ->
                        DhcpSessionCard(
                            session = session,
                            onStop = { viewModel.stopDHCPSpoofing() }
                        )
                    }
                }
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
}

@Composable
fun ActiveDhcpHeader() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "DHCP Spoofing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Intercept and modify DHCP responses to assign custom network configurations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DhcpSessionCard(
    session: SpoofingSession.Dhcp,
    onStop: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Active Spoofing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Interface: ${session.interfaceName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                IconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = "Rules Applied:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            session.rules.forEach { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = rule.targetMac,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(12.dp))
                    Text(
                        text = rule.spoofedIP,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySessionsState(
    title: String,
    description: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StartDHCPSpoofingDialog(
    onConfirm: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: NetworkMonitorViewModel = hiltViewModel()
) {
    val detectedInterface by viewModel.detectedInterface.collectAsStateWithLifecycle()
    val networkDevices by viewModel.networkDevices.collectAsStateWithLifecycle()
    val networkTopology by viewModel.networkTopology.collectAsStateWithLifecycle()
    
    var targetMac by remember { mutableStateOf("") }
    var spoofedIp by remember { mutableStateOf("") }
    var gatewayIp by remember { mutableStateOf("") }
    var dnsServer by remember { mutableStateOf("") }
    var interface_ by remember { mutableStateOf("") }
    var showMacDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (networkDevices.isEmpty()) {
            viewModel.scanNetwork()
        }
    }

    LaunchedEffect(networkTopology, detectedInterface, viewModel.detectedIp) {
        if (interface_.isEmpty()) interface_ = detectedInterface ?: "wlan0"
        if (gatewayIp.isEmpty()) gatewayIp = networkTopology?.gatewayDevice?.ipAddress ?: ""
        if (dnsServer.isEmpty()) dnsServer = viewModel.detectedIp.value ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start DHCP Spoofing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box {
                    OutlinedTextField(
                        value = targetMac,
                        onValueChange = { targetMac = it },
                        label = { Text("Target Device MAC") },
                        placeholder = { Text("Select from list") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { showMacDropdown = true },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                    )
                    DropdownMenu(
                        expanded = showMacDropdown,
                        onDismissRequest = { showMacDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        networkDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text("${device.getDisplayName()} (${device.macAddress})") },
                                onClick = {
                                    targetMac = device.macAddress
                                    showMacDropdown = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = spoofedIp,
                    onValueChange = { spoofedIp = it },
                    label = { Text("Spoofed IP") },
                    placeholder = { Text("192.168.x.x") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = gatewayIp,
                    onValueChange = { gatewayIp = it },
                    label = { Text("Gateway IP") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(targetMac, spoofedIp, gatewayIp, dnsServer, interface_) },
                enabled = targetMac.isNotBlank() && spoofedIp.isNotBlank()
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
