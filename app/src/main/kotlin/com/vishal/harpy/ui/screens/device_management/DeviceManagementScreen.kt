package com.vishal.harpy.ui.screens.device_management

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: NetworkMonitorViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Blocked", "Whitelisted", "All Devices")
    
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val blacklistedDevices by viewModel.blacklistedDevices.collectAsStateWithLifecycle()
    val whitelistedDevices by viewModel.whitelistedDevices.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Device Management",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // Tab Content
            when (selectedTab) {
                0 -> BlockedDevicesTab(blacklistedDevices, viewModel)
                1 -> WhitelistedDevicesTab(whitelistedDevices, viewModel)
                2 -> AllDevicesTab(devices, blacklistedDevices, whitelistedDevices, viewModel)
            }
        }
    }
}

@Composable
private fun BlockedDevicesTab(
    blockedDevices: List<NetworkDevice>,
    viewModel: NetworkMonitorViewModel
) {
    if (blockedDevices.isEmpty()) {
        EmptyStateMessage("No blocked devices")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(blockedDevices) { device ->
                DeviceCard(
                    device = device,
                    isBlocked = true,
                    isWhitelisted = false,
                    onBlock = { viewModel.unblockDevice(device) },
                    blockButtonText = "Unblock"
                )
            }
        }
    }
}

@Composable
private fun WhitelistedDevicesTab(
    whitelistedDevices: List<NetworkDevice>,
    viewModel: NetworkMonitorViewModel
) {
    if (whitelistedDevices.isEmpty()) {
        EmptyStateMessage("No whitelisted devices")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(whitelistedDevices) { device ->
                DeviceCard(
                    device = device,
                    isBlocked = false,
                    isWhitelisted = true,
                    onBlock = { viewModel.removeFromWhitelist(device) },
                    blockButtonText = "Remove from Whitelist"
                )
            }
        }
    }
}

@Composable
private fun AllDevicesTab(
    allDevices: List<NetworkDevice>,
    blockedDevices: List<NetworkDevice>,
    whitelistedDevices: List<NetworkDevice>,
    viewModel: NetworkMonitorViewModel
) {
    if (allDevices.isEmpty()) {
        EmptyStateMessage("No devices found")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(allDevices) { device ->
                val isBlocked = blockedDevices.any { it.ipAddress == device.ipAddress }
                val isWhitelisted = whitelistedDevices.any { it.ipAddress == device.ipAddress }
                
                DeviceCard(
                    device = device,
                    isBlocked = isBlocked,
                    isWhitelisted = isWhitelisted,
                    onBlock = {
                        if (isBlocked) {
                            viewModel.unblockDevice(device)
                        } else {
                            viewModel.blockDevice(device)
                        }
                    },
                    blockButtonText = if (isBlocked) "Unblock" else "Block"
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: NetworkDevice,
    isBlocked: Boolean,
    isWhitelisted: Boolean,
    onBlock: () -> Unit,
    blockButtonText: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isBlocked -> MaterialTheme.colorScheme.errorContainer
                isWhitelisted -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.deviceName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = device.ipAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = device.macAddress,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isBlocked) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text("Blocked", modifier = Modifier.padding(4.dp))
                        }
                    }
                    if (isWhitelisted) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Text("Whitelisted", modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onBlock,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBlocked) MaterialTheme.colorScheme.error 
                                   else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isBlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(blockButtonText)
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PhoneDisabled,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
