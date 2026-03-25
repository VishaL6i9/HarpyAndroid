package com.vishal.harpy.ui.screens.performance

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishal.harpy.ui.viewmodel.PerformanceMonitorViewModel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "PerfMonitorScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceMonitorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProcessList: () -> Unit = {},
    savedScrollOffset: Int = 0,
    onScrollOffsetChanged: (Int) -> Unit = {},
    viewModel: PerformanceMonitorViewModel = hiltViewModel()
) {
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()
    val isRootAvailable by viewModel.isRootAvailable.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // Auto-start monitoring when screen is in view
    LaunchedEffect(Unit) {
        Log.d(TAG, "LaunchedEffect: Starting monitoring")
        viewModel.startMonitoring()
    }
    
    // Auto-pause monitoring when screen is not in view
    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "DisposableEffect: Stopping monitoring (screen disposed)")
            viewModel.stopMonitoring()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Performance Monitor",
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
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshMetrics() }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = {
                        if (isMonitoring) {
                            viewModel.stopMonitoring()
                        } else {
                            viewModel.startMonitoring()
                        }
                    }) {
                        Icon(
                            if (isMonitoring) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (isMonitoring) "Pause" else "Resume",
                            tint = if (isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Log.d(TAG, "Scaffold content: metrics=${metrics != null}, isRootAvailable=$isRootAvailable")
        
        // Show loading indicator while waiting for first metrics update
        if (metrics == null) {
            Log.d(TAG, "Showing loading indicator")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading performance data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            return@Scaffold
        }

        Log.d(TAG, "Rendering metrics content: CPU=${metrics!!.cpuInfo.usagePercent.toInt()}%, Memory=${metrics!!.memoryInfo.usedMb}MB")

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
                .padding(paddingValues),
            state = scrollState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Root Status Banner
            item {
                RootStatusBanner(isRootAvailable = isRootAvailable)
            }

            // Error Banner
            error?.let { errorMessage ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // CPU Usage Card
            if (metrics != null) {
                item {
                    CpuUsageCard(metrics!!)
                }

                // CPU Frequencies (only with root)
                if (metrics!!.cpuFrequencies.isNotEmpty()) {
                    item {
                        CpuFrequenciesCard(metrics!!.cpuFrequencies)
                    }
                }

                // Thermal Zones (only with root)
                if (metrics!!.thermalZones.isNotEmpty()) {
                    item {
                        ThermalZonesCard(metrics!!.thermalZones)
                    }
                }

                // Memory Usage Card
                item {
                    MemoryUsageCard(metrics!!)
                }

                // Top Processes Card
                item {
                    TopProcessesCard(
                        processes = metrics!!.topProcesses,
                        isRootAvailable = isRootAvailable,
                        onNavigateToProcessList = onNavigateToProcessList
                    )
                }

                // Network Connections Card (only with root)
                if (metrics!!.networkConnections.isNotEmpty()) {
                    item {
                        NetworkConnectionsCard(metrics!!.networkConnections)
                    }
                }

                // System Stats Card
                item {
                    SystemStatsCard(metrics!!)
                }

                // Info Card
                item {
                    InfoCard(isRootAvailable = isRootAvailable)
                }
            }

            // Loading indicator
            if (isLoading && metrics == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun RootStatusBanner(isRootAvailable: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRootAvailable) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isRootAvailable) Icons.Filled.Security else Icons.Outlined.Security,
                contentDescription = null,
                tint = if (isRootAvailable) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = if (isRootAvailable) "Root Access Available" else "Limited Mode (No Root)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isRootAvailable) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = if (isRootAvailable) {
                        "Full system-wide monitoring enabled"
                    } else {
                        "Grant root for system-wide metrics"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRootAvailable) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun CpuUsageCard(metrics: com.vishal.harpy.core.utils.SystemPerformanceMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "CPU Usage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = metrics.cpuUsageFormatted,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            metrics.cpuInfo.usagePercent > 80 -> MaterialTheme.colorScheme.error
                            metrics.cpuInfo.usagePercent > 60 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = "System-wide CPU",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                CpuGauge(cpuPercent = metrics.cpuInfo.usagePercent / 100f)
            }

            // CPU breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CpuBreakdownItem(
                    label = "User",
                    value = metrics.cpuInfo.user,
                    total = metrics.cpuInfo.total,
                    color = MaterialTheme.colorScheme.primary
                )
                CpuBreakdownItem(
                    label = "System",
                    value = metrics.cpuInfo.system,
                    total = metrics.cpuInfo.total,
                    color = MaterialTheme.colorScheme.secondary
                )
                CpuBreakdownItem(
                    label = "Idle",
                    value = metrics.cpuInfo.idle,
                    total = metrics.cpuInfo.total,
                    color = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

@Composable
fun CpuBreakdownItem(label: String, value: Long, total: Long, color: Color) {
    val percent = if (total > 0) (value.toFloat() / total) * 100f else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${percent.toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CpuGauge(cpuPercent: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "cpuGauge")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val startAngle = 135f
            val sweep = cpuPercent * 270f

            drawArc(
                color = surfaceColor,
                startAngle = startAngle,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )

            drawArc(
                color = when {
                    cpuPercent > 0.8f -> errorColor
                    cpuPercent > 0.6f -> tertiaryColor
                    else -> primaryColor
                },
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )
        }

        Text(
            text = "${(cpuPercent * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CpuFrequenciesCard(frequencies: List<com.vishal.harpy.core.utils.CpuFrequency>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Timeline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "CPU Frequencies",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            frequencies.forEach { freq ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Core ${freq.cpuId}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${freq.currentFreqMhz.toInt()} MHz",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = freq.governor,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThermalZonesCard(thermalZones: List<com.vishal.harpy.core.utils.ThermalInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (thermalZones.any { it.isCritical }) {
                MaterialTheme.colorScheme.errorContainer
            } else if (thermalZones.any { it.isWarning }) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Thermostat,
                    contentDescription = null,
                    tint = if (thermalZones.any { it.isCritical }) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else if (thermalZones.any { it.isWarning }) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Thermal Zones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (thermalZones.any { it.isCritical }) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else if (thermalZones.any { it.isWarning }) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            thermalZones.forEach { zone ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = zone.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = zone.type,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${zone.temperature}°C",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            zone.isCritical -> MaterialTheme.colorScheme.error
                            zone.isWarning -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MemoryUsageCard(metrics: com.vishal.harpy.core.utils.SystemPerformanceMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Memory Usage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = metrics.memoryUsageFormatted,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            metrics.memoryInfo.usagePercent > 80 -> MaterialTheme.colorScheme.error
                            metrics.memoryInfo.usagePercent > 60 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = "of ${metrics.memoryTotalFormatted}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { metrics.memoryInfo.usagePercent / 100f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        color = when {
                            metrics.memoryInfo.usagePercent > 80 -> MaterialTheme.colorScheme.error
                            metrics.memoryInfo.usagePercent > 60 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Text(
                        text = metrics.memoryPercentFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Memory breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MemoryBreakdownItem("Free", metrics.memoryInfo.freeMb, MaterialTheme.colorScheme.primary)
                MemoryBreakdownItem("Buffers", metrics.memoryInfo.buffers / 1024f, MaterialTheme.colorScheme.secondary)
                MemoryBreakdownItem("Cached", metrics.memoryInfo.cached / 1024f, MaterialTheme.colorScheme.tertiary)
            }

            // Swap info (if available)
            if (metrics.memoryInfo.swapTotal > 0) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Swap",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${metrics.memoryInfo.swapUsedMb.roundToInt()} / ${metrics.memoryInfo.swapTotalMb.roundToInt()} MB",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun MemoryBreakdownItem(label: String, valueMb: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${valueMb.roundToInt()} MB",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TopProcessesCard(
    processes: List<com.vishal.harpy.core.utils.ProcessInfo>,
    isRootAvailable: Boolean,
    onNavigateToProcessList: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Top Processes (RAM)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onNavigateToProcessList,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = "View All",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            processes.take(5).forEach { process ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = process.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "PID: ${process.pid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${process.memoryVmRssMb.roundToInt()} MB",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun NetworkConnectionsCard(connections: List<com.vishal.harpy.core.utils.NetworkConnection>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Network Connections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Badge {
                    Text(connections.size.toString())
                }
            }

            connections.take(5).forEach { conn ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${conn.protocol.uppercase()} ${conn.remoteAddress}:${conn.remotePort}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = conn.processName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = conn.state,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (conn.state) {
                            "ESTABLISHED" -> MaterialTheme.colorScheme.primary
                            "LISTEN" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SystemStatsCard(metrics: com.vishal.harpy.core.utils.SystemPerformanceMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "System Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Outlined.Devices,
                    label = "Processes",
                    value = metrics.processCount.toString(),
                    valueColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                StatItem(
                    icon = Icons.Outlined.Memory,
                    label = "CPU Cores",
                    value = metrics.cpuFrequencies.size.toString(),
                    valueColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                StatItem(
                    icon = Icons.Outlined.Thermostat,
                    label = "Max Temp",
                    value = "${metrics.maxTemperature.toInt()}°C",
                    valueColor = if (metrics.maxTemperature > 60) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Outlined.NetworkPing,
                    label = "Connections",
                    value = metrics.networkConnections.size.toString(),
                    valueColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                StatItem(
                    icon = Icons.Outlined.Timer,
                    label = "Updated",
                    value = formatTimestamp(metrics.timestamp),
                    valueColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, valueColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = valueColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
fun InfoCard(isRootAvailable: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "ROOT Performance Monitoring",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            if (isRootAvailable) {
                Text(
                    text = "Root access enables system-wide CPU/memory monitoring, per-process stats, network connections, thermal data, and CPU frequency info. All data is read directly from /proc and /sys filesystems.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                Text(
                    text = "Grant root access to unlock: system-wide CPU usage, all process monitoring, network connections per app, thermal zone temperatures, CPU frequencies, and process management capabilities.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "N/A"
    }
}

private fun Float.roundToInt(): Int = toInt()
