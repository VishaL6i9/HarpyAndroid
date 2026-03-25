package com.vishal.harpy.ui.screens.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishal.harpy.core.utils.ProcessInfo
import com.vishal.harpy.ui.viewmodel.PerformanceMonitorViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessListScreen(
    onNavigateBack: () -> Unit,
    savedScrollOffset: Int = 0,
    onScrollOffsetChanged: (Int) -> Unit = {},
    viewModel: PerformanceMonitorViewModel = hiltViewModel()
) {
    val processes by viewModel.processes.collectAsStateWithLifecycle()
    val isRootAvailable by viewModel.isRootAvailable.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var showKillDialog by remember { mutableStateOf<ProcessInfo?>(null) }
    var showOomDialog by remember { mutableStateOf<ProcessInfo?>(null) }
    var sortBy by remember { mutableStateOf(SortBy.MEMORY) }
    var sortDescending by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refreshProcesses()
    }

    val filteredProcesses = remember(processes, sortBy, sortDescending, searchQuery) {
        processes
            .filter { process ->
                searchQuery.isBlank() ||
                process.name.contains(searchQuery, ignoreCase = true) ||
                process.cmdline.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                when (sortBy) {
                    SortBy.MEMORY -> compareByDescending<ProcessInfo> { it.memoryVmRss }
                    SortBy.CPU -> compareByDescending<ProcessInfo> { it.totalCpuTime }
                    SortBy.NAME -> compareBy<ProcessInfo> { it.name.lowercase() }
                    SortBy.PID -> compareBy<ProcessInfo> { it.pid }
                }.let { comparator ->
                    if (sortDescending) comparator else comparator.reversed()
                }
            )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Process Manager",
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
                    IconButton(onClick = { viewModel.refreshProcesses() }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.secondary
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
        ) {
            // Search and Filter Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    placeholder = { Text("Search processes...") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )

                SortDropdown(
                    currentSort = sortBy,
                    descending = sortDescending,
                    onSortChange = { sortBy = it },
                    onToggleDescending = { sortDescending = !sortDescending }
                )
            }

            // Root warning if not available
            if (!isRootAvailable) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Limited view - Grant root for full process management",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Process list header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${filteredProcesses.size} processes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Process list
            val scrollState = rememberLazyListState(
                initialFirstVisibleItemIndex = savedScrollOffset shr 16,
                initialFirstVisibleItemScrollOffset = savedScrollOffset and 0xFFFF
            )
            
            // Only collect scroll changes when there's a callback
            if (onScrollOffsetChanged != {}) {
                LaunchedEffect(Unit) {
                    snapshotFlow {
                        (scrollState.firstVisibleItemIndex shl 16) or (scrollState.firstVisibleItemScrollOffset and 0xFFFF)
                    }
                        .collect { offset ->
                            onScrollOffsetChanged(offset)
                        }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = scrollState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProcesses, key = { it.pid }) { process ->
                    ProcessListItem(
                        process = process,
                        isRootAvailable = isRootAvailable,
                        onKillClick = { showKillDialog = process },
                        onOomClick = { showOomDialog = process }
                    )
                }

                if (filteredProcesses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "No processes found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Kill confirmation dialog
    showKillDialog?.let { process ->
        AlertDialog(
            onDismissRequest = { showKillDialog = null },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Kill Process?") },
            text = {
                Column {
                    Text("Are you sure you want to kill:")
                    Text(
                        text = "${process.name} (PID: ${process.pid})",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "This may cause instability if it's a system process.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.killProcess(process.pid, "TERM")
                        }
                        showKillDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Kill")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKillDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // OOM score adjustment dialog
    showOomDialog?.let { process ->
        OomScoreDialog(
            process = process,
            onDismiss = { showOomDialog = null },
            onSetScore = { score ->
                scope.launch {
                    viewModel.setProcessOomScore(process.pid, score)
                }
                showOomDialog = null
            }
        )
    }
}

@Composable
fun ProcessListItem(
    process: ProcessInfo,
    isRootAvailable: Boolean,
    onKillClick: () -> Unit,
    onOomClick: () -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showOptions = true },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (process.state) {
                ProcessInfo.ProcessState.ZOMBIE -> MaterialTheme.colorScheme.errorContainer
                ProcessInfo.ProcessState.RUNNING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Process icon and info
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Process state indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (process.state) {
                                ProcessInfo.ProcessState.RUNNING -> MaterialTheme.colorScheme.primary
                                ProcessInfo.ProcessState.SLEEPING -> MaterialTheme.colorScheme.secondary
                                ProcessInfo.ProcessState.ZOMBIE -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                        )
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = process.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        if (process.parentPid == 1) {
                            Icon(
                                Icons.Outlined.AccountTree,
                                contentDescription = "Root process",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "PID: ${process.pid} • UID: ${process.uid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Memory and actions
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${process.memoryVmRssMb.roundToInt()} MB",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRootAvailable) {
                        IconButton(
                            onClick = onOomClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.PriorityHigh,
                                contentDescription = "Set priority",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(
                            onClick = onKillClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Kill process",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // Options bottom sheet
    if (showOptions) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(onDismissRequest = { showOptions = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = process.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "PID: ${process.pid}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Process details
                InfoRow("State", process.state.description)
                InfoRow("Memory (RSS)", "${process.memoryVmRssMb.roundToInt()} MB")
                InfoRow("Memory (VSize)", "${process.memoryVmSizeMb.roundToInt()} MB")
                InfoRow("Threads", process.threads.toString())
                InfoRow("CPU Time", "${(process.totalCpuTime / 100f)}s")
                if (process.ioReadBytes > 0 || process.ioWriteBytes > 0) {
                    InfoRow("I/O Read", "${process.ioReadBytesMb.roundToInt()} MB")
                    InfoRow("I/O Write", "${process.ioWriteBytesMb.roundToInt()} MB")
                }
                InfoRow("Open Files", process.openFiles.toString())
                if (process.cmdline.isNotBlank() && process.cmdline != process.name) {
                    InfoRow("Command", process.cmdline)
                }

                // Actions
                if (isRootAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onKillClick()
                            showOptions = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kill Process")
                    }

                    OutlinedButton(
                        onClick = {
                            onOomClick()
                            showOptions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PriorityHigh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adjust Priority")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SortDropdown(
    currentSort: SortBy,
    descending: Boolean,
    onSortChange: (SortBy) -> Unit,
    onToggleDescending: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.combinedClickable(
                onClick = { },
                onLongClick = {
                    // Reset to default sort (Memory, descending)
                    if (currentSort != SortBy.MEMORY || !descending) {
                        onSortChange(SortBy.MEMORY)
                    }
                }
            )
        ) {
            Icon(
                when (currentSort) {
                    SortBy.MEMORY -> Icons.Outlined.Memory
                    SortBy.CPU -> Icons.Outlined.Speed
                    SortBy.NAME -> Icons.Outlined.SortByAlpha
                    SortBy.PID -> Icons.Outlined.Numbers
                },
                contentDescription = "Sort",
                tint = if (currentSort == SortBy.MEMORY && descending) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Memory ${if (descending) "↓" else "↑"}") },
                onClick = {
                    if (currentSort == SortBy.MEMORY) {
                        onToggleDescending()
                    } else {
                        onSortChange(SortBy.MEMORY)
                    }
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Memory, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("CPU ${if (descending) "↓" else "↑"}") },
                onClick = {
                    if (currentSort == SortBy.CPU) {
                        onToggleDescending()
                    } else {
                        onSortChange(SortBy.CPU)
                    }
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Speed, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Name ${if (descending) "↓" else "↑"}") },
                onClick = {
                    if (currentSort == SortBy.NAME) {
                        onToggleDescending()
                    } else {
                        onSortChange(SortBy.NAME)
                    }
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Outlined.SortByAlpha, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("PID ${if (descending) "↓" else "↑"}") },
                onClick = {
                    if (currentSort == SortBy.PID) {
                        onToggleDescending()
                    } else {
                        onSortChange(SortBy.PID)
                    }
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Numbers, contentDescription = null)
                }
            )
        }
    }
}

@Composable
fun OomScoreDialog(
    process: ProcessInfo,
    onDismiss: () -> Unit,
    onSetScore: (Int) -> Unit
) {
    var score by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.PriorityHigh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Adjust OOM Score") },
        text = {
            Column {
                Text(
                    text = "${process.name} (PID: ${process.pid})",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Score: $score")
                Slider(
                    value = score.toFloat(),
                    onValueChange = { score = it.toInt() },
                    valueRange = -1000f..1000f,
                    steps = 200
                )
                Text(
                    text = when {
                        score < 0 -> "Lower = Less likely to be killed"
                        score > 0 -> "Higher = More likely to be killed"
                        else -> "Default priority"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSetScore(score) }) {
                Text("Set Score")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class SortBy {
    MEMORY, CPU, NAME, PID
}
