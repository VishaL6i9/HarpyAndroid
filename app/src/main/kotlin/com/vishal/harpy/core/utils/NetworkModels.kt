package com.vishal.harpy.core.utils

/**
 * Data class representing a network device
 */
data class NetworkDevice(
    val ipAddress: String,
    val macAddress: String,
    val hostname: String? = null,
    val vendor: String? = null,
    val deviceType: String? = null, // e.g., phone, laptop, IoT device
    val hwType: String? = null,    // Hardware type (ethernet, wifi, etc.)
    val mask: String? = null,      // Network mask
    val deviceInterface: String? = null, // Network interface (wlan0, eth0, etc.)
    var isBlocked: Boolean = false,
    var deviceName: String? = null, // User-defined device name (e.g., "My Laptop", "Guest Phone")
    var isPinned: Boolean = false   // Whether device is pinned
) {
    /**
     * Get display name - prioritizes device name over vendor
     */
    fun getDisplayName(): String {
        return deviceName ?: vendor ?: "Unknown Device"
    }
}

/**
 * Data class representing network topology
 */
data class NetworkTopology(
    val gatewayDevice: NetworkDevice?,
    val allDevices: List<NetworkDevice>,
    val devicesByType: Map<String, List<NetworkDevice>>,
    val unknownDevices: List<NetworkDevice>
)