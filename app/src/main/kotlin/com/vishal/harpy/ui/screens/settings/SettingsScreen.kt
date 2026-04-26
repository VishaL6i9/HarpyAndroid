package com.vishal.harpy.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel
import com.vishal.harpy.core.utils.LogUtils
import com.vishal.harpy.core.service.ServiceController
import com.vishal.harpy.core.di.ServiceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDeviceManagement: () -> Unit = {},
    onNavigateToPerformanceMonitor: () -> Unit = {},
    onNavigateToBlockingMethodSettings: () -> Unit = {},
    savedScrollOffset: Int = 0,
    onScrollOffsetChanged: (Int) -> Unit = {},
    viewModel: NetworkMonitorViewModel = hiltViewModel()
) {
    var showAboutScreen by remember { mutableStateOf(false) }
    var showClearNamesDialog by remember { mutableStateOf(false) }
    var showScanSettingsDialog by remember { mutableStateOf(false) }
    var showInterfaceSettingsDialog by remember { mutableStateOf(false) }
    var showLoggingDialog by remember { mutableStateOf(false) }
    var showRootHelperDialog by remember { mutableStateOf(false) }
    var showUnblockAllDialog by remember { mutableStateOf(false) }
    var showDnsSettingsDialog by remember { mutableStateOf(false) }
    var showDhcpSettingsDialog by remember { mutableStateOf(false) }
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val settings by viewModel.appSettings.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val serviceController = remember {
        EntryPointAccessors.fromApplication(context, ServiceEntryPoint::class.java).getServiceController()
    }

    val themeManager = remember {
        EntryPointAccessors.fromApplication(context, ServiceEntryPoint::class.java).getThemeManager()
    }
    val themeMode by themeManager.themeMode.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    BackHandler {
        if (showAboutScreen) {
            showAboutScreen = false
        } else {
            onNavigateBack()
        }
    }

    if (showAboutScreen) {
        // Show AboutScreen as a full-screen replacement
        AboutScreen(onNavigateBack = { showAboutScreen = false })
    } else {
        // Show normal settings screen
        SettingsContent(
            onNavigateBack = onNavigateBack,
            onNavigateToDeviceManagement = onNavigateToDeviceManagement,
            onNavigateToPerformanceMonitor = onNavigateToPerformanceMonitor,
            onNavigateToBlockingMethodSettings = onNavigateToBlockingMethodSettings,
            onShowAbout = { showAboutScreen = true },
            onShowClearNamesDialog = { showClearNamesDialog = true },
            onShowScanSettings = { showScanSettingsDialog = true },
            onShowInterfaceSettings = { showInterfaceSettingsDialog = true },
            onShowLogging = { showLoggingDialog = true },
            onShowRootHelper = { showRootHelperDialog = true },
            onShowUnblockAll = { showUnblockAllDialog = true },
            onShowDnsSettings = { showDnsSettingsDialog = true },
            onShowDhcpSettings = { showDhcpSettingsDialog = true },
            onShowWhitelist = { showWhitelistDialog = true },
            onShowTheme = { showThemeDialog = true },
            settings = settings,
            viewModel = viewModel,
            serviceController = serviceController,
            themeMode = themeMode,
            themeManager = themeManager
        )
    }

    // Success feedback
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(error) {
        error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Dialogs
    if (showClearNamesDialog) {
        ClearNamesDialog(
            onConfirm = {
                viewModel.clearAllDeviceNames()
                showClearNamesDialog = false
            },
            onDismiss = { showClearNamesDialog = false }
        )
    }

    if (showScanSettingsDialog) {
        ScanSettingsDialog(
            currentTimeout = settings.scanTimeoutSeconds,
            onConfirm = { timeout ->
                viewModel.updateScanTimeout(timeout)
                showScanSettingsDialog = false
            },
            onDismiss = { showScanSettingsDialog = false }
        )
    }

    if (showInterfaceSettingsDialog) {
        InterfaceSettingsDialog(
            currentInterface = settings.networkInterface,
            onConfirm = { ifName ->
                viewModel.updateNetworkInterface(ifName)
                showInterfaceSettingsDialog = false
            },
            onDismiss = { showInterfaceSettingsDialog = false }
        )
    }

    if (showLoggingDialog) {
        val logCount by viewModel.logCount.collectAsStateWithLifecycle()
        LoggingDialog(
            logCount = logCount,
            onClean = { viewModel.cleanLogs() },
            onClear = { viewModel.clearCurrentLog() },
            onDismiss = { showLoggingDialog = false }
        )
    }
    
    if (showRootHelperDialog) {
        RootHelperDialog(
            onDismiss = { showRootHelperDialog = false }
        )
    }

    if (showUnblockAllDialog) {
        AlertDialog(
            onDismissRequest = { showUnblockAllDialog = false },
            title = { Text("Unblock All Devices") },
            text = { Text("Are you sure you want to remove blocks from all devices? This will restore their network access.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unblockAllDevices()
                        showUnblockAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Unblock All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnblockAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDnsSettingsDialog) {
        DnsSettingsDialog(
            currentDns = settings.customDnsServer,
            currentFallback = settings.fallbackDnsServer,
            onConfirm = { dns, fallback ->
                viewModel.updateDnsSettings(dns, fallback)
                showDnsSettingsDialog = false
            },
            onDismiss = { showDnsSettingsDialog = false }
        )
    }

    if (showDhcpSettingsDialog) {
        DhcpSettingsDialog(
            currentLeaseTime = settings.dhcpLeaseTimeSeconds,
            onConfirm = { leaseTime ->
                viewModel.updateDhcpLeaseTime(leaseTime)
                showDhcpSettingsDialog = false
            },
            onDismiss = { showDhcpSettingsDialog = false }
        )
    }

    if (showWhitelistDialog) {
        WhitelistDialog(
            enableWhitelist = settings.enableWhitelist,
            onConfirm = { enabled ->
                viewModel.updateWhitelistEnabled(enabled)
                showWhitelistDialog = false
            },
            onDismiss = { showWhitelistDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = themeMode,
            onConfirm = { theme ->
                scope.launch {
                    themeManager.setThemeMode(theme)
                }
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    onNavigateBack: () -> Unit,
    onNavigateToDeviceManagement: () -> Unit,
    onNavigateToPerformanceMonitor: () -> Unit,
    onNavigateToBlockingMethodSettings: () -> Unit,
    onShowAbout: () -> Unit,
    onShowClearNamesDialog: () -> Unit,
    onShowScanSettings: () -> Unit,
    onShowInterfaceSettings: () -> Unit,
    onShowLogging: () -> Unit,
    onShowRootHelper: () -> Unit,
    onShowUnblockAll: () -> Unit,
    onShowDnsSettings: () -> Unit,
    onShowDhcpSettings: () -> Unit,
    onShowWhitelist: () -> Unit,
    onShowTheme: () -> Unit,
    settings: com.vishal.harpy.core.utils.AppSettings,
    viewModel: NetworkMonitorViewModel,
    serviceController: ServiceController,
    themeMode: com.vishal.harpy.core.utils.ThemeMode,
    themeManager: com.vishal.harpy.core.utils.ThemeManager,
    savedScrollOffset: Int = 0,
    onScrollOffsetChanged: (Int) -> Unit = {}
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
            // Appearance Section
            item {
                SettingsSectionHeader(title = "Appearance")
            }

            item {
                SettingsCard {
                    SettingsItem(
                        title = "Theme",
                        summary = "Current: ${themeMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        icon = Icons.Outlined.Palette,
                        onClick = onShowTheme
                    )
                }
            }

            // Network Section
            item {
                SettingsSectionHeader(title = "Network")
            }

            item {
                SettingsCard {
                    SettingsItem(
                        title = "Scan Settings",
                        summary = "Configure network scanning behavior (Current: ${settings.scanTimeoutSeconds}s)",
                        icon = Icons.Outlined.NetworkCheck,
                        onClick = onShowScanSettings
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        title = "Interface",
                        summary = "Select network interface (Current: ${settings.networkInterface})",
                        icon = Icons.Outlined.Router,
                        onClick = onShowInterfaceSettings
                    )
                }
            }

            // Spoofing Configuration Section
            item {
                SettingsSectionHeader(title = "Spoofing Configuration")
            }

            item {
                SettingsCard {
                    SettingsItem(
                        title = "DNS Settings",
                        summary = "Configure custom DNS server (Current: ${settings.customDnsServer})",
                        icon = Icons.Outlined.Dns,
                        onClick = onShowDnsSettings
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        title = "DHCP Settings",
                        summary = "Configure DHCP lease time (Current: ${settings.dhcpLeaseTimeSeconds}s)",
                        icon = Icons.Outlined.Settings,
                        onClick = onShowDhcpSettings
                    )
                }
            }

            // Device Management Section
            item {
                SettingsSectionHeader(title = "Device Management")
            }

            item {
                SettingsCard {
                    SettingsItem(
                        title = "Manage Devices",
                        summary = "View and manage blocked/whitelisted devices",
                        icon = Icons.Outlined.Devices,
                        onClick = onNavigateToDeviceManagement
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        title = "Whitelist Mode",
                        summary = "Enable whitelist to only spoof whitelisted devices",
                        icon = Icons.Outlined.CheckCircle,
                        onClick = onShowWhitelist,
                        trailingContent = {
                            Switch(
                                checked = settings.enableWhitelist,
                                onCheckedChange = { onShowWhitelist() }
                            )
                        }
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        title = "Clear All Device Names",
                        summary = "Remove all custom device names",
                        icon = Icons.Outlined.Edit,
                        onClick = onShowClearNamesDialog
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        title = "Unblock All Devices",
                        summary = "Remove all active device blocks",
                        icon = Icons.Outlined.Block,
                        onClick = onShowUnblockAll
                    )
                }
            }

            // Advanced Section
            item {
                SettingsSectionHeader(title = "Advanced")
            }

            item {
                SettingsCard {
                    SettingsItem(
                        title = "Blocking Method",
                        summary = "Configure how devices are blocked (Current: ${settings.blockingMethod.name.lowercase().replaceFirstChar { it.uppercase() }})",
                        icon = Icons.Outlined.Security,
                        onClick = onNavigateToBlockingMethodSettings
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        title = "Root Helper",
                        summary = "Configure root helper binary settings",
                        icon = Icons.Outlined.Security,
                        onClick = onShowRootHelper
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        title = "Logging",
                        summary = "View and export application logs",
                        icon = Icons.Outlined.Description,
                        onClick = onShowLogging
                    )
                    
                    SettingsDivider()

                    SettingsItem(
                        title = "Debug Mode",
                        summary = "Disable log rotation to maintain a continuous file for easier troubleshooting",
                        icon = Icons.Outlined.BugReport,
                        onClick = { viewModel.updateDebugMode(!settings.isDebugMode) },
                        trailingContent = {
                            Switch(
                                checked = settings.isDebugMode,
                                onCheckedChange = { viewModel.updateDebugMode(it) }
                            )
                        }
                    )

                    SettingsDivider()

                    SettingsItem(
                        title = "Performance Monitor",
                        summary = "Track CPU and memory usage in real-time",
                        icon = Icons.Outlined.Analytics,
                        onClick = onNavigateToPerformanceMonitor
                    )
                }
            }

            // Service Control Section
            item {
                SettingsSectionHeader(title = "Service")
            }

            item {
                SettingsCard {
                    com.vishal.harpy.ui.components.ServiceControlCard(
                        serviceController = serviceController,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // About Section
            item {
                SettingsSectionHeader(title = "About")
            }

            item {
                SettingsCard {
                    SettingsItem(
                        title = "About Harpy",
                        summary = "Version, developer info, and licenses",
                        icon = Icons.Outlined.Info,
                        onClick = onShowAbout
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(16.dp))
                trailingContent()
            }
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun ClearNamesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        title = { Text("Clear All Device Names?") },
        text = { Text("This will remove all custom device names. This action cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear All")
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
fun ScanSettingsDialog(
    currentTimeout: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var timeout by remember { mutableStateOf(currentTimeout.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Settings") },
        text = {
            Column {
                Text("Scan Timeout: ${timeout.toInt()} seconds")
                Slider(
                    value = timeout,
                    onValueChange = { timeout = it },
                    valueRange = 5f..60f,
                    steps = 55
                )
                Text(
                    text = "A longer timeout may find more devices but makes scanning slower.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(timeout.toInt()) }) {
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
fun InterfaceSettingsDialog(
    currentInterface: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: NetworkMonitorViewModel = hiltViewModel()
) {
    var selectedInterface by remember { mutableStateOf(currentInterface) }
    val availableInterfaces = remember { viewModel.getAvailableNetworkInterfaces() }
    val interfaces = remember(availableInterfaces) {
        availableInterfaces + listOf("auto").filter { it !in availableInterfaces }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Network Interface") },
        text = {
            Column {
                interfaces.forEach { ifName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedInterface = ifName }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (ifName == selectedInterface),
                            onClick = { selectedInterface = ifName }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(ifName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedInterface) }) {
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
fun LoggingDialog(
    logCount: Int,
    onClean: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Application Logs")
        },
        text = {
            Column {
                Text(text = "Logs are being captured in real-time. You can manage them here.")
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Total log files: $logCount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Log file is located in: HarpyAndroid/logs/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Current", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = onClean,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clean All", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun RootHelperDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Root Helper Info")
        },
        text = {
            Column {
                Text(text = "Harpy uses a native binary to perform advanced network operations.")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Status: Initialized",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The helper enables ARP spoofing, DHCP spoofing, and advanced network scanning.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


@Composable
fun DnsSettingsDialog(
    currentDns: String,
    currentFallback: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var dnsServer by remember { mutableStateOf(currentDns) }
    var fallbackServer by remember { mutableStateOf(currentFallback) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DNS Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Configure custom DNS servers for spoofing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = dnsServer,
                    onValueChange = { dnsServer = it },
                    label = { Text("Primary DNS") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = fallbackServer,
                    onValueChange = { fallbackServer = it },
                    label = { Text("Fallback DNS") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text(
                    text = "Fallback DNS is used if primary server is unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dnsServer, fallbackServer) }) {
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
fun DhcpSettingsDialog(
    currentLeaseTime: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var leaseTime by remember { mutableStateOf(currentLeaseTime.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DHCP Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Configure DHCP lease time for spoofed devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "Lease Time: ${leaseTime.toInt()} seconds (${leaseTime.toInt() / 60} minutes)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Slider(
                    value = leaseTime,
                    onValueChange = { leaseTime = it },
                    valueRange = 300f..86400f, // 5 minutes to 24 hours
                    steps = 100
                )
                
                Text(
                    text = "Longer lease times mean devices stay spoofed longer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(leaseTime.toInt()) }) {
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
fun WhitelistDialog(
    enableWhitelist: Boolean,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var whitelistEnabled by remember { mutableStateOf(enableWhitelist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Whitelist Mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "When enabled, only whitelisted devices will be spoofed. All other devices are ignored.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { whitelistEnabled = !whitelistEnabled }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = whitelistEnabled,
                        onCheckedChange = { whitelistEnabled = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Whitelist Mode")
                }
                
                if (whitelistEnabled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = "Whitelist mode is active. Add devices to whitelist in Device Management.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(whitelistEnabled) }) {
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
fun ThemeDialog(
    currentTheme: com.vishal.harpy.core.utils.ThemeMode,
    onConfirm: (com.vishal.harpy.core.utils.ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    val themes = listOf(
        com.vishal.harpy.core.utils.ThemeMode.LIGHT,
        com.vishal.harpy.core.utils.ThemeMode.DARK,
        com.vishal.harpy.core.utils.ThemeMode.SYSTEM
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                themes.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTheme = theme }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (theme == selectedTheme),
                            onClick = { selectedTheme = theme }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(theme.name.lowercase().replaceFirstChar { it.uppercase() })
                            Text(
                                text = when (theme) {
                                    com.vishal.harpy.core.utils.ThemeMode.LIGHT -> "Always use light theme"
                                    com.vishal.harpy.core.utils.ThemeMode.DARK -> "Always use dark theme"
                                    com.vishal.harpy.core.utils.ThemeMode.SYSTEM -> "Follow system settings"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedTheme) }) {
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
