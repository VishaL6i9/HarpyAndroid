package com.vishal.harpy.core.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Tracks detailed spoofing statistics and logs for analytics and debugging
 */
object SpoofingStatsTracker {
    private const val TAG = "SpoofingStatsTracker"
    
    data class SpoofingEvent(
        val timestamp: Long,
        val deviceMac: String,
        val deviceIp: String,
        val deviceName: String,
        val spoofingType: String, // "DNS" or "DHCP"
        val targetValue: String, // DNS domain or DHCP IP
        val status: String, // "STARTED", "STOPPED", "FAILED"
        val details: String = ""
    )
    
    data class SpoofingStats(
        val totalDnsEvents: Int = 0,
        val totalDhcpEvents: Int = 0,
        val totalDevicesSpoofed: Int = 0,
        val successfulEvents: Int = 0,
        val failedEvents: Int = 0,
        val averageSessionDuration: Long = 0L
    )
    
    private val eventQueue = ConcurrentLinkedQueue<SpoofingEvent>()
    private val sessionStartTimes = mutableMapOf<String, Long>()
    
    /**
     * Log a spoofing event
     */
    fun logSpoofingEvent(
        deviceMac: String,
        deviceIp: String,
        deviceName: String,
        spoofingType: String,
        targetValue: String,
        status: String,
        details: String = ""
    ) {
        val event = SpoofingEvent(
            timestamp = System.currentTimeMillis(),
            deviceMac = deviceMac,
            deviceIp = deviceIp,
            deviceName = deviceName,
            spoofingType = spoofingType,
            targetValue = targetValue,
            status = status,
            details = details
        )
        
        eventQueue.offer(event)
        
        // Log to file
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val timeStr = dateFormat.format(Date(event.timestamp))
        val logMessage = "[$timeStr] SPOOF_EVENT: $spoofingType | Device: $deviceName ($deviceMac) | " +
                "Target: $targetValue | Status: $status | Details: $details"
        
        LogUtils.d(TAG, logMessage)
        
        // Track session duration
        val sessionKey = "$deviceMac:$spoofingType"
        when (status) {
            "STARTED" -> sessionStartTimes[sessionKey] = System.currentTimeMillis()
            "STOPPED" -> {
                sessionStartTimes.remove(sessionKey)?.let { startTime ->
                    val duration = System.currentTimeMillis() - startTime
                    LogUtils.d(TAG, "Session duration for $deviceName: ${duration / 1000}s")
                }
            }
        }
    }
    
    /**
     * Get current spoofing statistics
     */
    fun getStatistics(): SpoofingStats {
        var dnsCount = 0
        var dhcpCount = 0
        var successCount = 0
        var failCount = 0
        val uniqueDevices = mutableSetOf<String>()
        var totalDuration = 0L
        var eventCount = 0
        
        eventQueue.forEach { event ->
            when (event.spoofingType) {
                "DNS" -> dnsCount++
                "DHCP" -> dhcpCount++
            }
            
            when (event.status) {
                "STARTED" -> successCount++
                "FAILED" -> failCount++
            }
            
            uniqueDevices.add(event.deviceMac)
            eventCount++
        }
        
        val avgDuration = if (eventCount > 0) totalDuration / eventCount else 0L
        
        return SpoofingStats(
            totalDnsEvents = dnsCount,
            totalDhcpEvents = dhcpCount,
            totalDevicesSpoofed = uniqueDevices.size,
            successfulEvents = successCount,
            failedEvents = failCount,
            averageSessionDuration = avgDuration
        )
    }
    
    /**
     * Get all events (for detailed logs)
     */
    fun getAllEvents(): List<SpoofingEvent> = eventQueue.toList()
    
    /**
     * Get events for a specific device
     */
    fun getDeviceEvents(deviceMac: String): List<SpoofingEvent> {
        return eventQueue.filter { it.deviceMac == deviceMac }
    }
    
    /**
     * Get events for a specific spoofing type
     */
    fun getEventsByType(spoofingType: String): List<SpoofingEvent> {
        return eventQueue.filter { it.spoofingType == spoofingType }
    }
    
    /**
     * Clear all statistics
     */
    fun clearStatistics() {
        eventQueue.clear()
        sessionStartTimes.clear()
        LogUtils.d(TAG, "Spoofing statistics cleared")
    }
    
    /**
     * Export statistics as formatted string
     */
    fun exportStatistics(): String {
        val stats = getStatistics()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val exportDate = dateFormat.format(Date())
        
        val sb = StringBuilder()
        sb.append("=== Harpy Spoofing Statistics ===\n")
        sb.append("Export Date: $exportDate\n\n")
        sb.append("Summary:\n")
        sb.append("- Total DNS Events: ${stats.totalDnsEvents}\n")
        sb.append("- Total DHCP Events: ${stats.totalDhcpEvents}\n")
        sb.append("- Unique Devices Spoofed: ${stats.totalDevicesSpoofed}\n")
        sb.append("- Successful Events: ${stats.successfulEvents}\n")
        sb.append("- Failed Events: ${stats.failedEvents}\n")
        sb.append("- Average Session Duration: ${stats.averageSessionDuration / 1000}s\n\n")
        
        sb.append("Detailed Events:\n")
        getAllEvents().forEach { event ->
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))
            sb.append("[$timeStr] ${event.spoofingType} | ${event.deviceName} (${event.deviceMac}) | ")
            sb.append("${event.targetValue} | ${event.status}\n")
        }
        
        return sb.toString()
    }
}
