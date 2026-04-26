package com.vishal.harpy.ui.screens.dns

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
fun DNSSpoofingScreen(
    viewModel: NetworkMonitorViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {},
    onManageSessions: () -> Unit = {}
) {
    val dnsSessions by viewModel.dnsSessions.collectAsStateWithLifecycle()
    var showStartDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<SpoofingSession.Dns?>(null) }

    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS Spoofing") },
                actions = {
                    if (dnsSessions.any { !it.isActive }) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Inactive")
                        }
                    }
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
                Icon(Icons.Default.Add, contentDescription = "New DNS Rule")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Info Card
            ActiveDnsHeader()

            if (dnsSessions.isEmpty()) {
                EmptySessionsState(
                    title = "DNS Spoofing",
                    description = "Redirect DNS queries for specific domains to custom IP addresses. Requires root access.",
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
                            text = "DNS Rules (${dnsSessions.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(dnsSessions, key = { it.id }) { session ->
                        DnsSessionCard(
                            session = session,
                            onStop = { viewModel.stopDNSSpoofing(session.domain) },
                            onResume = { viewModel.startDNSSpoofing(session.domain, session.spoofedIP, session.interfaceName) },
                            onEdit = {
                                editingSession = session
                                showEditDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showStartDialog) {
        val networkDevices by viewModel.networkDevices.collectAsStateWithLifecycle()
        val detectedIp by viewModel.detectedIp.collectAsStateWithLifecycle()
        val detectedInterface by viewModel.detectedInterface.collectAsStateWithLifecycle()
        
        StartDNSSpoofingDialog(
            networkDevices = networkDevices,
            detectedIp = detectedIp,
            detectedInterface = detectedInterface,
            availableInterfaces = viewModel.getAvailableNetworkInterfaces(),
            onConfirm = { domain, ip, interface_ ->
                viewModel.startDNSSpoofing(domain, ip, interface_)
                showStartDialog = false
            },
            onDismiss = { showStartDialog = false }
        )
    }

    if (showEditDialog && editingSession != null) {
        val networkDevices by viewModel.networkDevices.collectAsStateWithLifecycle()
        val detectedIp by viewModel.detectedIp.collectAsStateWithLifecycle()
        
        EditDNSSpoofingDialog(
            session = editingSession!!,
            networkDevices = networkDevices,
            detectedIp = detectedIp,
            availableInterfaces = viewModel.getAvailableNetworkInterfaces(),
            onConfirm = { domain, ip, interface_ ->
                val oldDomain = editingSession!!.domain
                val oldIp = editingSession!!.spoofedIP
                val oldInterface = editingSession!!.interfaceName
                
                // Only restart if domain changed (creates new rule)
                // Otherwise just update IP/interface on same rule
                if (domain != oldDomain) {
                    // Domain changed: stop old, start new
                    viewModel.stopDNSSpoofing(oldDomain)
                    viewModel.startDNSSpoofing(domain, ip, interface_)
                } else if (ip != oldIp || interface_ != oldInterface) {
                    // Only IP or interface changed: stop and restart with same domain
                    viewModel.stopDNSSpoofing(oldDomain)
                    viewModel.startDNSSpoofing(domain, ip, interface_)
                }
                // If nothing changed, do nothing
                
                showEditDialog = false
                editingSession = null
            },
            onDismiss = {
                showEditDialog = false
                editingSession = null
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Inactive Rules") },
            text = { Text("Remove all stopped DNS rules?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearInactiveDNSRules()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ActiveDnsHeader() {
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
                    text = "DNS Spoofing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Redirect DNS queries for specific domains to custom IP addresses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DnsSessionCard(
    session: SpoofingSession.Dns,
    onStop: () -> Unit,
    onResume: () -> Unit,
    onEdit: () -> Unit = {}
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
                        text = session.domain,
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
                    onClick = if (session.isActive) onStop else onResume,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (session.isActive) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        },
                        contentColor = if (session.isActive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Icon(
                        if (session.isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (session.isActive) "Stop" else "Resume"
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Redirects to:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = session.spoofedIP,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(12.dp))
            
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .align(Alignment.End)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Edit")
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
fun StartDNSSpoofingDialog(
    networkDevices: List<com.vishal.harpy.core.utils.NetworkDevice>,
    detectedIp: String?,
    detectedInterface: String?,
    availableInterfaces: List<String>,
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf("example.com") }
    var ip by remember { mutableStateOf("") }
    var interface_ by remember { mutableStateOf("") }
    var showIpDropdown by remember { mutableStateOf(false) }
    var showInterfaceDropdown by remember { mutableStateOf(false) }
    
    android.util.Log.d("DNSStartDialog", "=== DNS Start Dialog Debug ===")
    android.util.Log.d("DNSStartDialog", "networkDevices.size: ${networkDevices.size}")
    android.util.Log.d("DNSStartDialog", "detectedIp: $detectedIp")
    android.util.Log.d("DNSStartDialog", "Device IPs breakdown:")
    val unknownCount = networkDevices.count { it.ipAddress == "Unknown" }
    val validCount = networkDevices.count { it.ipAddress != "Unknown" }
    android.util.Log.d("DNSStartDialog", "  - Valid IPs: $validCount")
    android.util.Log.d("DNSStartDialog", "  - Unknown IPs: $unknownCount")
    if (validCount > 0) {
        android.util.Log.d("DNSStartDialog", "Valid device IPs: ${networkDevices.filter { it.ipAddress != "Unknown" }.map { it.ipAddress }}")
    }
    
    val deviceIps = remember(networkDevices, detectedIp) {
        val ips = mutableListOf<String>()
        detectedIp?.let { ips.add(it) }
        networkDevices.forEach { device ->
            if (device.ipAddress != "Unknown" && !ips.contains(device.ipAddress)) {
                ips.add(device.ipAddress)
            }
        }
        android.util.Log.d("DNSStartDialog", "Computed deviceIps: $ips")
        ips.distinct()
    }
    
    val hasScannedDevices = remember(networkDevices) {
        networkDevices.any { it.ipAddress != "Unknown" }
    }

    LaunchedEffect(detectedInterface, detectedIp) {
        if (interface_.isEmpty()) interface_ = detectedInterface ?: "wlan0"
        if (ip.isEmpty()) ip = detectedIp ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start DNS Spoofing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!hasScannedDevices) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Run network scan first to populate device list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain") },
                    placeholder = { Text("example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = { Text("Spoofed IP") },
                        placeholder = { Text("Select or enter IP") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { 
                            Icon(
                                Icons.Default.ArrowDropDown, 
                                null,
                                modifier = Modifier.clickable { showIpDropdown = !showIpDropdown }
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = showIpDropdown,
                        onDismissRequest = { showIpDropdown = false }
                    ) {
                        deviceIps.forEach { deviceIp ->
                            DropdownMenuItem(
                                text = { Text(deviceIp) },
                                onClick = {
                                    ip = deviceIp
                                    showIpDropdown = false
                                }
                            )
                        }
                    }
                }
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = interface_,
                        onValueChange = { interface_ = it },
                        label = { Text("Network Interface") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showInterfaceDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showInterfaceDropdown,
                        onDismissRequest = { showInterfaceDropdown = false }
                    ) {
                        availableInterfaces.forEach { iface ->
                            DropdownMenuItem(
                                text = { Text(iface) },
                                onClick = {
                                    interface_ = iface
                                    showInterfaceDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
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
fun EditDNSSpoofingDialog(
    session: SpoofingSession.Dns,
    networkDevices: List<com.vishal.harpy.core.utils.NetworkDevice>,
    detectedIp: String?,
    availableInterfaces: List<String>,
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by remember { mutableStateOf(session.domain) }
    var ip by remember { mutableStateOf(session.spoofedIP) }
    var interface_ by remember { mutableStateOf(session.interfaceName) }
    var showIpDropdown by remember { mutableStateOf(false) }
    var showInterfaceDropdown by remember { mutableStateOf(false) }
    
    android.util.Log.d("DNSEditDialog", "=== DNS Edit Dialog Debug ===")
    android.util.Log.d("DNSEditDialog", "networkDevices.size: ${networkDevices.size}")
    android.util.Log.d("DNSEditDialog", "detectedIp: $detectedIp")
    android.util.Log.d("DNSEditDialog", "Device IPs breakdown:")
    val unknownCount = networkDevices.count { it.ipAddress == "Unknown" }
    val validCount = networkDevices.count { it.ipAddress != "Unknown" }
    android.util.Log.d("DNSEditDialog", "  - Valid IPs: $validCount")
    android.util.Log.d("DNSEditDialog", "  - Unknown IPs: $unknownCount")
    if (validCount > 0) {
        android.util.Log.d("DNSEditDialog", "Valid device IPs: ${networkDevices.filter { it.ipAddress != "Unknown" }.map { it.ipAddress }}")
    }
    
    val deviceIps = remember(networkDevices, detectedIp) {
        val ips = mutableListOf<String>()
        detectedIp?.let { ips.add(it) }
        networkDevices.forEach { device ->
            if (device.ipAddress != "Unknown" && !ips.contains(device.ipAddress)) {
                ips.add(device.ipAddress)
            }
        }
        android.util.Log.d("DNSEditDialog", "Computed deviceIps: $ips")
        ips.distinct()
    }
    
    val hasScannedDevices = remember(networkDevices) {
        networkDevices.any { it.ipAddress != "Unknown" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit DNS Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!hasScannedDevices) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Run network scan first to populate device list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = { Text("Spoofed IP") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { 
                            Icon(
                                Icons.Default.ArrowDropDown, 
                                null,
                                modifier = Modifier.clickable { showIpDropdown = !showIpDropdown }
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = showIpDropdown,
                        onDismissRequest = { showIpDropdown = false }
                    ) {
                        deviceIps.forEach { deviceIp ->
                            DropdownMenuItem(
                                text = { Text(deviceIp) },
                                onClick = {
                                    ip = deviceIp
                                    showIpDropdown = false
                                }
                            )
                        }
                    }
                }
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = interface_,
                        onValueChange = { interface_ = it },
                        label = { Text("Network Interface") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showInterfaceDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showInterfaceDropdown,
                        onDismissRequest = { showInterfaceDropdown = false }
                    ) {
                        availableInterfaces.forEach { iface ->
                            DropdownMenuItem(
                                text = { Text(iface) },
                                onClick = {
                                    interface_ = iface
                                    showInterfaceDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(domain, ip, interface_) },
                enabled = domain.isNotBlank() && ip.isNotBlank()
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
