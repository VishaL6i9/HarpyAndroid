package com.vishal.harpy.ui.screens.network.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChips(
    filterIPv4: Boolean,
    filterIPv6: Boolean,
    onIPv4Toggle: () -> Unit,
    onIPv6Toggle: () -> Unit,
    deviceCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterIPv4,
                    onClick = onIPv4Toggle,
                    label = { Text("IPv4") }
                )
                
                FilterChip(
                    selected = filterIPv6,
                    onClick = onIPv6Toggle,
                    label = { Text("IPv6") }
                )
            }
            
            Text(
                text = "$deviceCount device${if (deviceCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
