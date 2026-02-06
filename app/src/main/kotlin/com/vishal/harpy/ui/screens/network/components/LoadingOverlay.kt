package com.vishal.harpy.ui.screens.network.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.LoadingState

@Composable
fun LoadingOverlay(loadingState: LoadingState) {
    AnimatedVisibility(
        visible = loadingState != LoadingState.None,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = when (loadingState) {
                            LoadingState.Scanning -> "Scanning network..."
                            LoadingState.Blocking -> "Blocking device..."
                            LoadingState.Unblocking -> "Unblocking device..."
                            LoadingState.MappingTopology -> "Mapping network topology..."
                            LoadingState.TestingPing -> "Testing ping..."
                            LoadingState.DNSSpoofing -> "Configuring DNS spoofing..."
                            LoadingState.DHCPSpoofing -> "Configuring DHCP spoofing..."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
