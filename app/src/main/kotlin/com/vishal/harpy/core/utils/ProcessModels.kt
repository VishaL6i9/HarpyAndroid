package com.vishal.harpy.core.utils

import kotlin.math.roundToInt

/**
 * Represents detailed information about a running process
 * Obtained via root access to /proc filesystem
 */
data class ProcessInfo(
    val pid: Int,
    val name: String,
    val cmdline: String,
    val uid: Int,
    val state: ProcessState,
    val cpuTimeUser: Long,      // User mode CPU time (jiffies)
    val cpuTimeSystem: Long,    // Kernel mode CPU time (jiffies)
    val memoryVmSize: Long,     // Virtual memory size (KB)
    val memoryVmRss: Long,      // Resident set size (KB)
    val memoryVmData: Long,     // Data segment size (KB)
    val threads: Int,
    val parentPid: Int,
    val startTime: Long,        // Process start time (jiffies since boot)
    val ioReadBytes: Long = 0L,     // Bytes read (from /proc/[pid]/io)
    val ioWriteBytes: Long = 0L,    // Bytes written (from /proc/[pid]/io)
    val openFiles: Int = 0          // Number of open file descriptors
) {
    val totalCpuTime: Long
        get() = cpuTimeUser + cpuTimeSystem

    val memoryVmSizeMb: Float
        get() = memoryVmSize / 1024f

    val memoryVmRssMb: Float
        get() = memoryVmRss / 1024f

    val memoryVmDataMb: Float
        get() = memoryVmData / 1024f

    val ioReadBytesMb: Float
        get() = ioReadBytes / (1024f * 1024f)

    val ioWriteBytesMb: Float
        get() = ioWriteBytes / (1024f * 1024f)

    /**
     * Process state enum from /proc/[pid]/stat
     */
    enum class ProcessState(val code: Char, val description: String) {
        RUNNING('R', "Running or runnable"),
        SLEEPING('S', "Interruptible sleep"),
        WAITING('D', "Uninterruptible sleep (I/O)"),
        STOPPED('T', "Stopped"),
        TRACED('t', "Tracing stop"),
        DEAD('X', "Dead"),
        ZOMBIE('Z', "Zombie"),
        IDLE('I', "Idle kernel thread"),
        PARKED('P', "Parked"),
        UNKNOWN('?', "Unknown");

        companion object {
            fun fromCode(code: Char): ProcessState {
                return values().find { it.code == code } ?: UNKNOWN
            }
        }
    }
}

/**
 * System-wide CPU information
 */
data class CpuInfo(
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long,
    val iowait: Long,
    val irq: Long,
    val softirq: Long,
    val steal: Long = 0,
    val guest: Long = 0,
    val guestNice: Long = 0,
    val calculatedUsagePercent: Float = -1f  // -1 means use computed usagePercent
) {
    val total: Long
        get() = user + nice + system + idle + iowait + irq + softirq + steal + guest + guestNice

    val active: Long
        get() = total - idle - iowait

    val usagePercent: Float
        get() = if (calculatedUsagePercent >= 0) calculatedUsagePercent else if (total > 0) (active.toFloat() / total) * 100f else 0f
}

/**
 * System memory information from /proc/meminfo
 */
data class MemoryInfo(
    val total: Long,        // Total memory (KB)
    val free: Long,         // Free memory (KB)
    val available: Long,    // Available memory (KB)
    val buffers: Long,      // Buffers (KB)
    val cached: Long,       // Cached (KB)
    val swapTotal: Long,    // Swap total (KB)
    val swapFree: Long      // Swap free (KB)
) {
    val used: Long
        get() = total - available

    val totalMb: Float
        get() = total / 1024f

    val freeMb: Float
        get() = free / 1024f

    val availableMb: Float
        get() = available / 1024f

    val usedMb: Float
        get() = used / 1024f

    val usagePercent: Float
        get() = if (total > 0) (used.toFloat() / total) * 100f else 0f

    val swapUsedMb: Float
        get() = (swapTotal - swapFree) / 1024f

    val swapTotalMb: Float
        get() = swapTotal / 1024f
}

/**
 * CPU frequency information
 */
data class CpuFrequency(
    val cpuId: Int,
    val currentFreq: Long,  // Current frequency (KHz)
    val minFreq: Long,      // Minimum frequency (KHz)
    val maxFreq: Long,      // Maximum frequency (KHz)
    val governor: String    // CPU governor (performance, powersave, etc.)
) {
    val currentFreqMhz: Float
        get() = currentFreq / 1000f

    val minFreqMhz: Float
        get() = minFreq / 1000f

    val maxFreqMhz: Float
        get() = maxFreq / 1000f

    val usagePercent: Float
        get() = if (maxFreq > 0) (currentFreq.toFloat() / maxFreq) * 100f else 0f
}

/**
 * Thermal zone information
 */
data class ThermalInfo(
    val zoneId: Int,
    val name: String,
    val temperature: Float,     // Temperature in Celsius
    val type: String,
    val policy: String = ""
) {
    val isCritical: Boolean
        get() = temperature > 80f

    val isWarning: Boolean
        get() = temperature > 60f
}

/**
 * Network connection information
 */
data class NetworkConnection(
    val protocol: String,       // tcp, udp, tcp6, udp6
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val state: String,
    val pid: Int,
    val processName: String
)
