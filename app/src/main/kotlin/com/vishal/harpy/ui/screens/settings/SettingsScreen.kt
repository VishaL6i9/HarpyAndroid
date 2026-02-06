package com.vishal.harpy.ui.screens.settings

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
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NetworkMonitorViewModel = hiltViewModel()
) {
    var showAboutScreen by remember { mutableStateOf(false) }
    var showClearNamesDialog by remember { mutableStateOf(false) }

    if (showAboutScreen) {
        // Show AboutScreen as a full-screen replacement
        AboutScreen(onNavigateBack = { showAboutScreen = false })
    } else {
        // Show normal settings screen
        SettingsContent(
            onNavigateBack = onNavigateBack,
            onShowAbout = { showAboutScreen = true },
            onShowClearNamesDialog = { showClearNamesDialog = true },
            viewModel = viewModel
        )
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    onNavigateBack: () -> Unit,
    onShowAbout: () -> Unit,
    onShowClearNamesDialog: () -> Unit,
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
                        summary = "Configure network scanning behavior",
                        icon = Icons.Outlined.NetworkCheck,
                        onClick = { /* TODO: Navigate to scan settings */ }
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        title = "Interface",
                        summary = "Select network interface (wlan0, eth0)",
                        icon = Icons.Outlined.Router,
                        onClick = { /* TODO: Navigate to interface settings */ }
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
                        onClick = { viewModel.unblockAllDevices() }
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
                        onClick = { /* TODO: Navigate to root settings */ }
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        title = "Logging",
                        summary = "View and export application logs",
                        icon = Icons.Outlined.Description,
                        onClick = { /* TODO: Navigate to logging settings */ }
                    )
                    
                    SettingsDivider()
                    
                    SettingsItem(
                        title = "Debug Mode",
                        summary = "Enable advanced debugging features",
                        icon = Icons.Outlined.BugReport,
                        onClick = { /* TODO: Navigate to debug settings */ }
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
    onClick: () -> Unit
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
