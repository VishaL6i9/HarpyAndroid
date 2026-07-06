package com.vishal.harpy.ui.screens.ios_attack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishal.harpy.features.ios_attack.presentation.IosAttackViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosAttackScreen(
    viewModel: IosAttackViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshState()
    }

    // Error toasts
    LaunchedEffect(error) {
        error?.let {
            android.widget.Toast.makeText(
                androidx.compose.ui.platform.LocalContext.current,
                it,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "iOS Void Attacks",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
            InfoCard()

            // Target configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Target Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = uiState.targetMac,
                        onValueChange = { viewModel.updateTargetMac(it) },
                        label = { Text("Target MAC") },
                        placeholder = { Text("aa:bb:cc:dd:ee:ff") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.targetIp,
                        onValueChange = { viewModel.updateTargetIp(it) },
                        label = { Text("Target IP") },
                        placeholder = { Text("192.168.1.100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.routerIp,
                        onValueChange = { viewModel.updateRouterIp(it) },
                        label = { Text("Router IP") },
                        placeholder = { Text("192.168.1.1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.routerMac,
                        onValueChange = { viewModel.updateRouterMac(it) },
                        label = { Text("Router MAC") },
                        placeholder = { Text("xx:xx:xx:xx:xx:xx") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        text = "Interface: ${settings.networkInterface}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Attack cards
            Text(
                "Void Arsenal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // PRIMARY VOID: DHCP Self-Gateway
            AttackCard(
                title = "① DHCP Self-Implosion",
                description = "Forces iOS to delete its default gateway by sending a unicast DHCPACK with router = client's own IP and /32 subnet mask. The iOS kernel installs a host route pointing to itself — outbound traffic collapses.",
                icon = Icons.Outlined.Router,
                isActive = uiState.isDhcpSelfGatewayActive,
                isLoading = uiState.isLoading,
                requires = "MAC, Router IP, Router MAC",
                onStart = { viewModel.startDhcpSelfGateway() },
                onStop = { viewModel.stopDhcpSelfGateway() }
            )

            // SECONDARY VOID: ICMP Redirect
            AttackCard(
                title = "② ICMP Redirect Forge",
                description = "Sends forged ICMP Type 5 redirect packets claiming the client is its own gateway for the entire IPv4 space (0.0.0.0/1 + 128.0.0.0/1). iOS inserts dynamic routes that void all external destinations.",
                icon = Icons.Outlined.NetworkCheck,
                isActive = uiState.isIcmpRedirectActive,
                isLoading = uiState.isLoading,
                requires = "MAC, IP, Router IP, Router MAC",
                onStart = { viewModel.startIcmpRedirect() },
                onStop = { viewModel.stopIcmpRedirect() }
            )

            // TERTIARY VOID: DNS Nullification
            AttackCard(
                title = "③ DNS Nullification",
                description = "Sends a unicast DHCPACK with DNS Server (Option 6) set to 0.0.0.0. Network stays up but no hostnames resolve — browsers spin, iMessage fails, App Store breaks. The user perceives total internet death.",
                icon = Icons.Outlined.Dns,
                isActive = uiState.isDnsNullifyActive,
                isLoading = uiState.isLoading,
                requires = "MAC, Router IP, Router MAC",
                onStart = { viewModel.startDnsNullification() },
                onStop = { viewModel.stopDnsNullification() }
            )

            // FAILSAFE: TCP RST
            AttackCard(
                title = "④ TCP RST Asymmetry",
                description = "Sniffs every TCP SYN from the target and immediately responds with a forged RST spoofing the destination server. Every connection attempt is killed at the handshake level. Pure Layer 4 — works when DHCP is locked.",
                icon = Icons.Outlined.Security,
                isActive = uiState.isTcpRstActive,
                isLoading = uiState.isLoading,
                requires = "MAC, IP",
                onStart = { viewModel.startTcpRst() },
                onStop = { viewModel.stopTcpRst() }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "iOS Void Attacks — Layer 3 & 4 Offensive Toolkit",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Four independent vectors engineered to nullify iOS network connectivity without touching ARP. " +
                        "Each bypasses iOS protections differently. All require root. Toggle on/off independently.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun AttackCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    isLoading: Boolean,
    requires: String,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Requires: $requires",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Status indicator
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = if (isActive) "ACTIVE" else "IDLE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { if (isActive) onStop() else onStart() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isActive) "STOP ATTACK" else "DEPLOY")
            }
        }
    }
}
