package com.vishal.harpy.ui.screens.network.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InterfaceWarningBanner(
    selectedInterface: String,
    detectedInterface: String?,
    onSwitchClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Only show if there's a mismatch and we actually detected something for comparison
    val showWarning = detectedInterface != null && selectedInterface != activeToShort(detectedInterface) 
        && activeToShort(selectedInterface) != activeToShort(detectedInterface)

    AnimatedVisibility(
        visible = showWarning,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning"
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interface Mismatch",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Selected: $selectedInterface | Active: ${detectedInterface ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (detectedInterface != null) {
                    Button(
                        onClick = { onSwitchClick(detectedInterface) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 32.dp)
                    ) {
                        Text("Switch", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Helper to normalize interface names (e.g., removing sub-interface indices if any)
 * to avoid false positives in detection.
 */
private fun activeToShort(name: String): String {
    return name.split(":")[0].trim()
}
