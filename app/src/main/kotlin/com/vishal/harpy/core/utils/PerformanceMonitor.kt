package com.vishal.harpy.core.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Comprehensive performance metrics using ROOT access
 */
data class SystemPerformanceMetrics(
    val cpuInfo: CpuInfo,
    val memoryInfo: MemoryInfo,
    val cpuFrequencies: List<CpuFrequency>,
    val thermalZones: List<ThermalInfo>,
    val processCount: Int,
    val topProcesses: List<ProcessInfo>,
    val networkConnections: List<NetworkConnection>,
    val isRootAvailable: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    val cpuUsageFormatted: String
        get() = "${cpuInfo.usagePercent.roundToInt()}%"

    val memoryUsageFormatted: String
        get() = "${memoryInfo.usedMb.roundToInt()} MB"

    val memoryTotalFormatted: String
        get() = "${memoryInfo.totalMb.roundToInt()} MB"

    val memoryPercentFormatted: String
        get() = "${memoryInfo.usagePercent.roundToInt()}%"

    val avgCpuFreqMhz: Float
        get() = cpuFrequencies.map { it.currentFreqMhz }.average().toFloat()

    val maxTemperature: Float
        get() = thermalZones.maxOfOrNull { it.temperature } ?: 0f
}

/**
 * Root-enabled performance monitor with system-wide metrics
 * 
 * Features with ROOT:
 * - System-wide CPU usage from /proc/stat
 * - Per-process CPU and memory for ALL processes
 * - System memory from /proc/meminfo
 * - CPU frequency and governor per core
 * - Thermal zone temperatures
 * - Network connections per process
 * - Process I/O statistics
 * - Process management (kill, set priority)
 */
@Singleton
class PerformanceMonitor @Inject constructor(
    private val rootCommandExecutor: RootCommandExecutor
) {
    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val CLK_TCK = 100L // Clock ticks per second (standard on Android)
    }

    private var lastCpuInfo: CpuInfo? = null
    private var lastProcessCpuTimes: Map<Int, Long> = emptyMap()
    private var lastCpuUsagePercent: Float = 0f

    /**
     * Check if root access is available
     */
    suspend fun isRootAvailable(): Boolean {
        return rootCommandExecutor.isRootAvailable()
    }

    /**
     * Get comprehensive system performance metrics
     */
    suspend fun getSystemPerformanceMetrics(): SystemPerformanceMetrics = withContext(Dispatchers.IO) {
        val isRoot = isRootAvailable()
        Log.d(TAG, "=== Performance Metrics Update ===")
        Log.d(TAG, "Root available: $isRoot")

        val cpuInfo = getCpuInfo(isRoot)
        Log.d(TAG, "CPU usage: ${cpuInfo.usagePercent.toInt()}%")

        val memoryInfo = getMemoryInfo(isRoot)
        Log.d(TAG, "Memory: ${memoryInfo.usedMb.toInt()}/${memoryInfo.totalMb.toInt()} MB (${memoryInfo.usagePercent.toInt()}%)")

        val cpuFrequencies = getCpuFrequencies(isRoot)
        Log.d(TAG, "CPU frequencies: ${cpuFrequencies.size} cores")

        val thermalZones = getThermalZones(isRoot)
        Log.d(TAG, "Thermal zones: ${thermalZones.size} zones, max temp: ${thermalZones.maxOfOrNull { it.temperature } ?: 0}°C")

        val processes = if (isRoot) getAllProcesses() else emptyList()
        Log.d(TAG, "Processes: ${processes.size}")

        val networkConnections = if (isRoot) getNetworkConnections() else emptyList()
        Log.d(TAG, "Network connections: ${networkConnections.size}")

        // Calculate CPU usage as delta between readings
        val cpuUsagePercent = calculateCpuUsage(cpuInfo)

        SystemPerformanceMetrics(
            cpuInfo = cpuInfo.copy(calculatedUsagePercent = cpuUsagePercent),
            memoryInfo = memoryInfo,
            cpuFrequencies = cpuFrequencies,
            thermalZones = thermalZones,
            processCount = processes.size,
            topProcesses = processes.sortedByDescending { it.memoryVmRss }.take(10),
            networkConnections = networkConnections,
            isRootAvailable = isRoot
        )
    }

    /**
     * Get system-wide CPU info from /proc/stat using root
     */
    private suspend fun getCpuInfo(isRoot: Boolean): CpuInfo = withContext(Dispatchers.IO) {
        Log.d(TAG, "getCpuInfo: isRoot=$isRoot")
        
        if (!isRoot) {
            Log.d(TAG, "getCpuInfo: Using fallback (no root)")
            // Fallback: estimate from app CPU
            return@withContext getCpuInfoFallback()
        }

        try {
            // Use root to read /proc/stat
            val result = rootCommandExecutor.execute("cat /proc/stat")
            Log.d(TAG, "getCpuInfo: root command exitCode=${result.exitCode}, output=${result.output.size} lines")
            
            if (!result.isSuccess) {
                Log.e(TAG, "getCpuInfo: root command failed: ${result.error}")
                return@withContext getCpuInfoFallback()
            }

            val cpuLine = result.output.firstOrNull { it.startsWith("cpu ") }
                ?: run {
                    Log.e(TAG, "getCpuInfo: No cpu line found in output")
                    return@withContext getCpuInfoFallback()
                }

            Log.d(TAG, "getCpuInfo: cpuLine=$cpuLine")
            
            val parts = cpuLine.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (parts.size < 8) {
                Log.e(TAG, "getCpuInfo: Not enough parts in cpu line: ${parts.size}")
                return@withContext getCpuInfoFallback()
            }

            CpuInfo(
                user = parts[1].toLong(),
                nice = parts[2].toLong(),
                system = parts[3].toLong(),
                idle = parts[4].toLong(),
                iowait = parts[5].toLong(),
                irq = parts[6].toLong(),
                softirq = parts[7].toLong(),
                steal = parts.getOrNull(8)?.toLongOrNull() ?: 0,
                guest = parts.getOrNull(9)?.toLongOrNull() ?: 0,
                guestNice = parts.getOrNull(10)?.toLongOrNull() ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "getCpuInfo: Exception: ${e.message}", e)
            getCpuInfoFallback()
        }
    }

    /**
     * Fallback CPU info calculation using /proc/self/stat
     */
    private fun getCpuInfoFallback(): CpuInfo {
        return try {
            // Read process CPU time from /proc/self/stat
            val statContent = java.io.File("/proc/self/stat").readText()
            val parts = statContent.split("\\s+".toRegex())
            if (parts.size > 14) {
                val utime = parts[13].toLongOrNull() ?: 0
                val stime = parts[14].toLongOrNull() ?: 0
                CpuInfo(
                    user = utime,
                    nice = 0,
                    system = stime,
                    idle = 0,
                    iowait = 0,
                    irq = 0,
                    softirq = 0
                )
            } else {
                CpuInfo(0, 0, 0, 0, 0, 0, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback CPU read failed: ${e.message}")
            CpuInfo(0, 0, 0, 0, 0, 0, 0)
        }
    }

    /**
     * Calculate CPU usage percentage from delta between readings
     */
    private fun calculateCpuUsage(currentCpu: CpuInfo): Float {
        val lastCpu = lastCpuInfo
        
        // Store current for next reading
        lastCpuInfo = currentCpu
        
        // First reading - no previous data to compare
        if (lastCpu == null) {
            Log.d(TAG, "calculateCpuUsage: First reading, returning 0%")
            lastCpuUsagePercent = 0f
            return 0f
        }
        
        // Calculate deltas
        val totalDelta = currentCpu.total - lastCpu.total
        val activeDelta = currentCpu.active - lastCpu.active
        
        Log.d(TAG, "calculateCpuUsage: totalDelta=$totalDelta, activeDelta=$activeDelta")
        
        // Calculate usage percentage
        val usagePercent = if (totalDelta > 0) {
            (activeDelta.toFloat() / totalDelta) * 100f
        } else {
            0f
        }
        
        lastCpuUsagePercent = usagePercent.coerceIn(0f, 100f)
        Log.d(TAG, "calculateCpuUsage: result=${lastCpuUsagePercent.toInt()}%")
        return lastCpuUsagePercent
    }

    /**
     * Get system memory info from /proc/meminfo using root
     */
    private suspend fun getMemoryInfo(isRoot: Boolean): MemoryInfo = withContext(Dispatchers.IO) {
        Log.d(TAG, "getMemoryInfo: isRoot=$isRoot")
        
        if (!isRoot) {
            Log.d(TAG, "getMemoryInfo: Using fallback (no root)")
            // Fallback to ActivityManager
            return@withContext getMemoryInfoFallback()
        }

        try {
            val result = rootCommandExecutor.execute("cat /proc/meminfo")
            Log.d(TAG, "getMemoryInfo: root command exitCode=${result.exitCode}, output=${result.output.size} lines")
            
            if (!result.isSuccess) {
                Log.e(TAG, "getMemoryInfo: root command failed: ${result.error}")
                return@withContext getMemoryInfoFallback()
            }
            
            val values = mutableMapOf<String, Long>()
            result.output.forEach { line ->
                val parts = line.split(":")
                if (parts.size >= 2) {
                    val key = parts[0].trim()
                    val valueStr = parts[1].trim().split("\\s+".toRegex()).first()
                    values[key] = valueStr.toLongOrNull() ?: 0
                }
            }

            val memoryInfo = MemoryInfo(
                total = values["MemTotal"] ?: 0,
                free = values["MemFree"] ?: 0,
                available = values["MemAvailable"] ?: (
                    (values["MemFree"] ?: 0) + 
                    (values["Buffers"] ?: 0) + 
                    (values["Cached"] ?: 0)
                ),
                buffers = values["Buffers"] ?: 0,
                cached = values["Cached"] ?: 0,
                swapTotal = values["SwapTotal"] ?: 0,
                swapFree = values["SwapFree"] ?: 0
            )
            
            Log.d(TAG, "getMemoryInfo: total=${memoryInfo.totalMb.toInt()}MB, used=${memoryInfo.usedMb.toInt()}MB, available=${memoryInfo.availableMb.toInt()}MB")
            return@withContext memoryInfo
        } catch (e: Exception) {
            Log.e(TAG, "getMemoryInfo: Exception: ${e.message}", e)
            getMemoryInfoFallback()
        }
    }

    /**
     * Fallback memory info from Debug class
     */
    private fun getMemoryInfoFallback(): MemoryInfo {
        return try {
            val runtime = Runtime.getRuntime()
            val total = runtime.totalMemory() / 1024
            val free = runtime.freeMemory() / 1024
            MemoryInfo(
                total = total,
                free = free,
                available = free,
                buffers = 0,
                cached = 0,
                swapTotal = 0,
                swapFree = 0
            )
        } catch (e: Exception) {
            MemoryInfo(0, 0, 0, 0, 0, 0, 0)
        }
    }

    /**
     * Get CPU frequency for each core from /sys/devices/system/cpu/ using root
     */
    private suspend fun getCpuFrequencies(isRoot: Boolean): List<CpuFrequency> = withContext(Dispatchers.IO) {
        if (!isRoot) return@withContext emptyList()

        try {
            val frequencies = mutableListOf<CpuFrequency>()
            
            // Get all CPU info in one command
            val result = rootCommandExecutor.execute(
                "for i in 0 1 2 3 4 5 6 7; do " +
                "echo \"CPU\$i:\"; " +
                "cat /sys/devices/system/cpu/cpu\$i/cpufreq/scaling_cur_freq 2>/dev/null || echo 0; " +
                "cat /sys/devices/system/cpu/cpu\$i/cpufreq/scaling_min_freq 2>/dev/null || echo 0; " +
                "cat /sys/devices/system/cpu/cpu\$i/cpufreq/scaling_max_freq 2>/dev/null || echo 0; " +
                "cat /sys/devices/system/cpu/cpu\$i/cpufreq/scaling_governor 2>/dev/null || echo unknown; " +
                "done"
            )
            
            if (!result.isSuccess || result.output.isEmpty()) {
                return@withContext emptyList()
            }
            
            // Parse output (4 lines per CPU: cur, min, max, governor)
            var i = 0
            while (i < result.output.size) {
                val line = result.output[i]
                if (line.startsWith("CPU")) {
                    val cpuId = line.drop(3).dropLast(1).toIntOrNull()
                    if (cpuId != null && i + 4 <= result.output.size) {
                        val currentFreq = result.output[i + 1].trim().toLongOrNull() ?: 0
                        val minFreq = result.output[i + 2].trim().toLongOrNull() ?: 0
                        val maxFreq = result.output[i + 3].trim().toLongOrNull() ?: 0
                        val governor = result.output[i + 4].trim()
                        
                        if (currentFreq > 0) {
                            frequencies.add(
                                CpuFrequency(
                                    cpuId = cpuId,
                                    currentFreq = currentFreq,
                                    minFreq = minFreq,
                                    maxFreq = maxFreq,
                                    governor = governor
                                )
                            )
                        }
                        i += 5
                        continue
                    }
                }
                i++
            }
            
            frequencies
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e  // Re-throw cancellation
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get thermal zone temperatures from /sys/class/thermal/ using root
     */
    private suspend fun getThermalZones(isRoot: Boolean): List<ThermalInfo> = withContext(Dispatchers.IO) {
        if (!isRoot) return@withContext emptyList()

        try {
            val thermalZones = mutableListOf<ThermalInfo>()
            
            // Get all thermal zone info in one command
            val result = rootCommandExecutor.execute(
                "for zone in /sys/class/thermal/thermal_zone*; do " +
                "if [ -d \"\$zone\" ]; then " +
                "echo \"ZONE:\$(basename \$zone):\"; " +
                "cat \$zone/type 2>/dev/null || echo Unknown; " +
                "cat \$zone/temp 2>/dev/null || echo 0; " +
                "cat \$zone/policy 2>/dev/null || echo; " +
                "fi; " +
                "done"
            )
            
            if (!result.isSuccess || result.output.isEmpty()) {
                return@withContext emptyList()
            }
            
            // Parse output (4 lines per zone: header, type, temp, policy)
            var i = 0
            while (i < result.output.size) {
                val line = result.output[i]
                if (line.startsWith("ZONE:")) {
                    val parts = line.split(":")
                    if (parts.size >= 3 && i + 3 <= result.output.size) {
                        val zoneId = parts[1].drop(12).toIntOrNull()
                        val type = result.output[i + 1].trim()
                        val tempRaw = result.output[i + 2].trim().toLongOrNull() ?: 0
                        val temperature = tempRaw / 1000f
                        val policy = result.output[i + 3].trim()
                        
                        if (zoneId != null) {
                            thermalZones.add(
                                ThermalInfo(
                                    zoneId = zoneId,
                                    name = "Zone $zoneId",
                                    temperature = temperature,
                                    type = type,
                                    policy = policy
                                )
                            )
                        }
                        i += 4
                        continue
                    }
                }
                i++
            }
            thermalZones
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e  // Re-throw cancellation
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get all running processes with detailed info using root
     */
    suspend fun getAllProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        try {
            // Use Android-compatible ps command (toybox/toolbox syntax)
            val result = rootCommandExecutor.execute("ps")
            
            if (!result.isSuccess || result.output.isEmpty()) {
                Log.d(TAG, "getAllProcesses: No data from ps command")
                return@withContext emptyList()
            }
            
            Log.d(TAG, "getAllProcesses: ps returned ${result.output.size} lines")
            
            val processes = mutableListOf<ProcessInfo>()
            
            // Skip header line and parse remaining
            for ((index, line) in result.output.withIndex()) {
                if (line.isBlank()) continue
                if (index == 0 && (line.contains("PID") || line.contains("USER"))) continue  // Skip header
                
                // Android ps format: USER PID PPID VSIZE RSS WCHAN PC NAME
                // Or: USER PID PPID VSIZE RSS WCHAN PC S NAME
                val parts = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                
                // Need at least USER, PID, PPID, VSIZE, RSS, WCHAN, PC, NAME (8 parts minimum)
                // Or with state: 9 parts
                if (parts.size < 8) {
                    continue
                }
                
                // Find PID position (usually second column, but skip USER if present)
                var pidIndex = 0
                if (parts[0].all { it.isLetter() || it == '_' }) {
                    pidIndex = 1  // First column is USER
                }
                
                if (pidIndex + 7 > parts.size) continue
                
                val pid = parts[pidIndex].toIntOrNull()
                val ppid = parts.getOrNull(pidIndex + 1)?.toIntOrNull() ?: 0
                val vmSize = parts.getOrNull(pidIndex + 2)?.toLongOrNull() ?: 0L
                val rss = parts.getOrNull(pidIndex + 3)?.toLongOrNull() ?: 0L
                // WCHAN is at pidIndex + 4, PC/S is at pidIndex + 5
                // Name is the rest
                val nameStartIndex = pidIndex + 6
                val name = if (nameStartIndex < parts.size) {
                    parts.subList(nameStartIndex, parts.size).joinToString(" ")
                } else {
                    "unknown"
                }
                
                if (pid == null || pid <= 0) continue
                
                processes.add(
                    ProcessInfo(
                        pid = pid,
                        name = name,
                        cmdline = name,
                        uid = 0,  // Not easily available from Android ps
                        state = ProcessInfo.ProcessState.SLEEPING,
                        cpuTimeUser = 0,
                        cpuTimeSystem = 0,
                        memoryVmSize = vmSize,
                        memoryVmRss = rss,
                        memoryVmData = 0,
                        threads = 1,
                        parentPid = ppid,
                        startTime = 0,
                        ioReadBytes = 0,
                        ioWriteBytes = 0,
                        openFiles = 0
                    )
                )
            }
            
            Log.d(TAG, "getAllProcesses: Found ${processes.size} valid processes")
            return@withContext processes.sortedByDescending { it.memoryVmRss }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading processes: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get network connections from /proc/net/ using root
     */
    private suspend fun getNetworkConnections(): List<NetworkConnection> = withContext(Dispatchers.IO) {
        val connections = mutableListOf<NetworkConnection>()

        try {
            // Get all network info in fewer commands
            connections.addAll(parseProcNet("tcp", "tcp"))
            connections.addAll(parseProcNet("udp", "udp"))
        } catch (e: Exception) {
            Log.e(TAG, "Error reading network connections: ${e.message}")
        }

        Log.d(TAG, "getNetworkConnections: Found ${connections.size} connections")
        return@withContext connections
    }

    /**
     * Parse /proc/net/tcp, udp files using root
     */
    private suspend fun parseProcNet(netType: String, protocol: String): List<NetworkConnection> {
        return try {
            // Read using root
            val result = rootCommandExecutor.execute("cat /proc/net/$netType")
            if (!result.isSuccess) return emptyList()
            
            val lines = result.output.drop(1) // Skip header
            lines.mapNotNull { line ->
                val parts = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (parts.size < 10) return@mapNotNull null

                val localAddr = parts[1]
                val remoteAddr = parts[2]
                val stateCode = parts[3]

                // Parse addresses
                val (localIp, localPort) = parseAddr(localAddr)
                val (remoteIp, remotePort) = parseAddr(remoteAddr)

                NetworkConnection(
                    protocol = protocol,
                    localAddress = localIp,
                    localPort = localPort,
                    remoteAddress = remoteIp,
                    remotePort = remotePort,
                    state = tcpStateName(stateCode.toIntOrNull(16) ?: 0, protocol),
                    pid = 0,  // Would require searching all processes
                    processName = "unknown"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing /proc/net/$netType: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parse hex address to IP:port
     */
    private fun parseAddr(addr: String): Pair<String, Int> {
        return try {
            val parts = addr.split(":")
            val ipHex = parts[0]
            val portHex = parts[1]

            // Parse IPv4 (little-endian)
            val ipBytes = ipHex.chunked(2).map { it.toInt(16) }
            val ip = if (ipBytes.size == 4) {
                "${ipBytes[3]}.${ipBytes[2]}.${ipBytes[1]}.${ipBytes[0]}"
            } else {
                addr // IPv6, return as-is
            }

            val port = portHex.toInt(16)
            Pair(ip, port)
        } catch (e: Exception) {
            Pair("0.0.0.0", 0)
        }
    }

    /**
     * Get TCP/UDP state name
     */
    private fun tcpStateName(state: Int, protocol: String): String {
        return when {
            protocol.startsWith("udp") -> "ESTABLISHED"
            else -> when (state) {
                0x01 -> "ESTABLISHED"
                0x02 -> "SYN_SENT"
                0x03 -> "SYN_RECV"
                0x04 -> "FIN_WAIT1"
                0x05 -> "FIN_WAIT2"
                0x06 -> "TIME_WAIT"
                0x07 -> "CLOSE"
                0x08 -> "CLOSE_WAIT"
                0x09 -> "LAST_ACK"
                0x0A -> "LISTEN"
                0x0B -> "CLOSING"
                else -> "UNKNOWN"
            }
        }
    }

    /**
     * Kill a process by PID (requires root)
     */
    suspend fun killProcess(pid: Int, signal: String = "TERM"): RootCommandResult {
        return rootCommandExecutor.execute("kill -$signal $pid")
    }

    /**
     * Set process OOM score adjustment (requires root)
     * Lower values = less likely to be killed
     */
    suspend fun setOomScoreAdj(pid: Int, score: Int): RootCommandResult {
        return rootCommandExecutor.execute("echo $score > /proc/$pid/oom_score_adj")
    }

    /**
     * Set process CPU affinity (requires root)
     */
    suspend fun setCpuAffinity(pid: Int, cpuMask: String): RootCommandResult {
        return rootCommandExecutor.execute("taskset -p $cpuMask $pid")
    }

    /**
     * Get process CPU usage delta
     */
    fun calculateProcessCpuUsage(process: ProcessInfo, deltaMs: Long): Float {
        val lastCpu = lastProcessCpuTimes[process.pid] ?: 0L
        val cpuDelta = process.totalCpuTime - lastCpu
        
        if (deltaMs <= 0 || cpuDelta <= 0) return 0f
        
        // Convert jiffies to ms and calculate percentage
        val cpuMs = (cpuDelta * 1000) / CLK_TCK
        val usage = (cpuMs.toFloat() / deltaMs) * 100f
        
        lastProcessCpuTimes = lastProcessCpuTimes + (process.pid to process.totalCpuTime)
        return usage.coerceIn(0f, 100f)
    }

    /**
     * Clear cached CPU times
     */
    fun clearCpuCache() {
        lastProcessCpuTimes = emptyMap()
    }
}
