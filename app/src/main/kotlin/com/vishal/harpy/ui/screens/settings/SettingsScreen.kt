package com.vishal.harpy.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NetworkMonitorViewModel = hiltViewModel()
) {
    var showAboutScreen by remember { mutableStateOf(false) }
    var showClearNamesDialog by remember { mutableStateOf(false) }
    var showScanSettingsDialog by remember { mutableStateOf(false) }
    var showInterfaceSettingsDialog by remember { mutableStateOf(false) }
    var showLoggingDialog by remember { mutableStateOf(false) }
    var showRootHelperDialog by remember { mutableStateOf(false) }
    var showUnblockAllDialog by remember { mutableStateOf(false) }

    val settings by viewModel.appSettings.collectAsStateWithLifecycle()

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
            onShowAbout = { showAboutScreen = true },
            onShowClearNamesDialog = { showClearNamesDialog = true },
            onShowScanSettings = { showScanSettingsDialog = true },
            onShowInterfaceSettings = { showInterfaceSettingsDialog = true },
            onShowLogging = { showLoggingDialog = true },
            onShowRootHelper = { showRootHelperDialog = true },
            onShowUnblockAll = { showUnblockAllDialog = true },
            settings = settings,
            viewModel = viewModel
        )
    }

    // Success feedback
    val context = androidx.compose.ui.platform.LocalContext.current
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    onNavigateBack: () -> Unit,
    onShowAbout: () -> Unit,
    onShowClearNamesDialog: () -> Unit,
    onShowScanSettings: () -> Unit,
    onShowInterfaceSettings: () -> Unit,
    onShowLogging: () -> Unit,
    onShowRootHelper: () -> Unit,
    onShowUnblockAll: () -> Unit,
    settings: com.vishal.harpy.core.utils.AppSettings,
    viewModel: NetworkMonitorViewModel
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

            // Device Management Section
            item {
                SettingsSectionHeader(title = "Device Management")
            }

            item {
                SettingsCard {
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
    onDismiss: () -> Unit
) {
    var selectedInterface by remember { mutableStateOf(currentInterface) }
    val interfaces = listOf("wlan0", "eth0", "rmnet0", "auto")

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
