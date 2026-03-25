package com.vishal.harpy.ui.screens.status

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishal.harpy.core.di.ServiceEntryPoint
import com.vishal.harpy.core.state.SpoofingStateManager
import com.vishal.harpy.core.utils.SpoofingStatsTracker
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedStatusScreen(
    onNavigateBack: () -> Unit,
    savedScrollOffset: Int = 0,
    onScrollOffsetChanged: (Int) -> Unit = {},
    viewModel: NetworkMonitorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val spoofingStateManager = remember {
        EntryPointAccessors.fromApplication(context, ServiceEntryPoint::class.java).getSpoofingStateManager()
    }
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val spoofingState by spoofingStateManager.spoofingState.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    
    val stats = SpoofingStatsTracker.getStatistics()
    val allEvents = SpoofingStatsTracker.getAllEvents()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Detailed Status",
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
        val scrollState = rememberLazyListState(
            initialFirstVisibleItemIndex = savedScrollOffset shr 16,
            initialFirstVisibleItemScrollOffset = savedScrollOffset and 0xFFFF
        )
        
        LaunchedEffect(scrollState) {
            snapshotFlow { 
                (scrollState.firstVisibleItemIndex shl 16) or (scrollState.firstVisibleItemScrollOffset and 0xFFFF)
            }
                .collect { offset ->
                    onScrollOffsetChanged(offset)
                }
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = scrollState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Spoofing Status
            item {
                StatusSection(title = "Active Spoofing") {
                    ActiveSpoofingCard(spoofingState)
                }
            }
            
            // Network Information
            item {
                StatusSection(title = "Network Information") {
                    NetworkInfoCard(settings, devices.size)
                }
            }
            
            // Spoofing Statistics
            item {
                StatusSection(title = "Statistics") {
                    StatisticsCard(stats)
                }
            }
            
            // Affected Devices
            item {
                StatusSection(title = "Affected Devices") {
                    AffectedDevicesCard(devices)
                }
            }
            
            // Recent Events
            item {
                StatusSection(title = "Recent Events") {
                    RecentEventsCard(allEvents.takeLast(10))
                }
            }
        }
    }
}

@Composable
private fun StatusSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        content()
    }
}

@Composable
private fun ActiveSpoofingCard(spoofingState: com.vishal.harpy.core.state.SpoofingState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (spoofingState.isAnySpoofingActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (spoofingState.isAnySpoofingActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (spoofingState.isAnySpoofingActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = if (spoofingState.isAnySpoofingActive)
                        Icons.Outlined.CheckCircle
                    else
                        Icons.Outlined.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (spoofingState.isAnySpoofingActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (spoofingState.isAnySpoofingActive) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (spoofingState.isDnsSpoofingActive) {
                        StatusBadge("DNS", Icons.Outlined.Dns)
                    }
                    if (spoofingState.isDhcpSpoofingActive) {
                        StatusBadge("DHCP", Icons.Outlined.Router)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .height(32.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NetworkInfoCard(
    settings: com.vishal.harpy.core.utils.AppSettings,
    deviceCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow("Network Interface", settings.networkInterface)
            HorizontalDivider()
            InfoRow("Primary DNS", settings.customDnsServer)
            HorizontalDivider()
            InfoRow("Fallback DNS", settings.fallbackDnsServer)
            HorizontalDivider()
            InfoRow("DHCP Lease Time", "${settings.dhcpLeaseTimeSeconds}s (${settings.dhcpLeaseTimeSeconds / 60}m)")
            HorizontalDivider()
            InfoRow("Devices on Network", deviceCount.toString())
            HorizontalDivider()
            InfoRow("Whitelist Mode", if (settings.enableWhitelist) "Enabled" else "Disabled")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatisticsCard(stats: SpoofingStatsTracker.SpoofingStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatRow("Total DNS Events", stats.totalDnsEvents.toString(), Icons.Outlined.Dns)
            HorizontalDivider()
            StatRow("Total DHCP Events", stats.totalDhcpEvents.toString(), Icons.Outlined.Router)
            HorizontalDivider()
            StatRow("Unique Devices Spoofed", stats.totalDevicesSpoofed.toString(), Icons.Outlined.Devices)
            HorizontalDivider()
            StatRow("Successful Events", stats.successfulEvents.toString(), Icons.Outlined.CheckCircle)
            HorizontalDivider()
            StatRow("Failed Events", stats.failedEvents.toString(), Icons.Outlined.Error)
            HorizontalDivider()
            StatRow("Avg Session Duration", "${stats.averageSessionDuration / 1000}s", Icons.Outlined.Timer)
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AffectedDevicesCard(devices: List<com.vishal.harpy.core.utils.NetworkDevice>) {
    val blockedDevices = devices.filter { it.isBlocked }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (blockedDevices.isEmpty()) {
                Text(
                    text = "No devices currently blocked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Blocked: ${blockedDevices.size} device(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                blockedDevices.take(5).forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.deviceName ?: device.ipAddress,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = device.ipAddress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text("Blocked", modifier = Modifier.padding(4.dp))
                        }
                    }
                }
                
                if (blockedDevices.size > 5) {
                    Text(
                        text = "+${blockedDevices.size - 5} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentEventsCard(events: List<SpoofingStatsTracker.SpoofingEvent>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (events.isEmpty()) {
                Text(
                    text = "No recent events",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                events.forEach { event ->
                    EventItem(event)
                    if (event != events.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventItem(event: SpoofingStatsTracker.SpoofingEvent) {
    val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    val timeStr = timeFormat.format(java.util.Date(event.timestamp))
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Badge(
                    containerColor = when (event.spoofingType) {
                        "DNS" -> MaterialTheme.colorScheme.primary
                        "DHCP" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.secondary
                    }
                ) {
                    Text(event.spoofingType, modifier = Modifier.padding(2.dp))
                }
                Text(
                    text = event.deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = event.targetValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Badge(
                containerColor = when (event.status) {
                    "STARTED" -> MaterialTheme.colorScheme.primary
                    "STOPPED" -> MaterialTheme.colorScheme.secondary
                    "FAILED" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(event.status, modifier = Modifier.padding(2.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
