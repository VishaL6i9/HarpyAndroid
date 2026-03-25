package com.vishal.harpy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vishal.harpy.core.service.ServiceController

@Composable
fun ServiceControlCard(
    serviceController: ServiceController,
    modifier: Modifier = Modifier
) {
    val isServiceRunning = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isServiceRunning.value = serviceController.isServiceRunning()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Circle,
                contentDescription = null,
                tint = if (isServiceRunning.value) Color.Green else Color.Gray,
                modifier = Modifier.padding(4.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Service Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isServiceRunning.value) "Active" else "Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    serviceController.startNotificationService()
                    isServiceRunning.value = true
                },
                enabled = !isServiceRunning.value,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start")
            }

            Button(
                onClick = {
                    serviceController.stopNotificationService()
                    isServiceRunning.value = false
                },
                enabled = isServiceRunning.value,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop")
            }
        }
    }
}
