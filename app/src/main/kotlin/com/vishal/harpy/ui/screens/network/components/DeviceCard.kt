package com.vishal.harpy.ui.screens.network.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vishal.harpy.core.utils.NetworkDevice

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceCard(
    device: NetworkDevice,
    onBlockClick: () -> Unit,
    onPinClick: () -> Unit,
    onEditNameClick: () -> Unit,
    onPingClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEditNameClick,
                onLongClick = onEditNameClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isBlocked) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (device.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = device.deviceName ?: device.ipAddress,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (device.deviceName != null) {
                        Text(
                            text = device.ipAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = device.macAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (!device.vendor.isNullOrEmpty()) {
                        Text(
                            text = device.vendor ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (device.isGateway) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Gateway") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Router,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    
                    if (device.isBlocked) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Blocked") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBlockClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (device.isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = if (device.isBlocked) "Unblock" else "Block",
                        tint = if (device.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                
                IconButton(
                    onClick = onPinClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (device.isPinned) Icons.Filled.PushPin else Icons.Default.PushPin,
                        contentDescription = if (device.isPinned) "Unpin" else "Pin",
                        tint = if (device.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(
                    onClick = onEditNameClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Name"
                    )
                }
                
                IconButton(
                    onClick = onPingClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkPing,
                        contentDescription = "Test Ping"
                    )
                }
            }
        }
    }
}
