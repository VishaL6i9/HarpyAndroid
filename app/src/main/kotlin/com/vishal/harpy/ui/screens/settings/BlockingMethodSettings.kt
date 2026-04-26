package com.vishal.harpy.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishal.harpy.core.utils.BlockingMethod
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingMethodSettings(
    viewModel: NetworkMonitorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedMethod by remember { mutableStateOf(settings.blockingMethod) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Blocking Method",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Select Device Blocking Method",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Choose how devices are blocked on your network. Different methods work better on different devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Method options
            BlockingMethodOption(
                method = BlockingMethod.ARP_SPOOF,
                title = "ARP Spoofing",
                description = "Works best on Android. Intercepts traffic via ARP cache poisoning.",
                isSelected = selectedMethod == BlockingMethod.ARP_SPOOF,
                onSelect = { selectedMethod = it }
            )

            BlockingMethodOption(
                method = BlockingMethod.BLACKHOLE_ROUTE,
                title = "Blackhole Route",
                description = "Drops all packets via kernel routing. Works on iOS and Android.",
                isSelected = selectedMethod == BlockingMethod.BLACKHOLE_ROUTE,
                onSelect = { selectedMethod = it }
            )

            BlockingMethodOption(
                method = BlockingMethod.TRAFFIC_CONTROL,
                title = "Traffic Control (tc)",
                description = "Rate limits or blocks. Most flexible method.",
                isSelected = selectedMethod == BlockingMethod.TRAFFIC_CONTROL,
                onSelect = { selectedMethod = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = {
                    scope.launch {
                        viewModel.updateBlockingMethod(selectedMethod)
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                enabled = selectedMethod != settings.blockingMethod
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BlockingMethodOption(
    method: BlockingMethod,
    title: String,
    description: String,
    isSelected: Boolean,
    onSelect: (BlockingMethod) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onSelect(method) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = { onSelect(method) }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
