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
    var isBlocked: Boolean = false
)

/**
 * Data class representing network topology
 */
data class NetworkTopology(
    val gatewayDevice: NetworkDevice?,
    val allDevices: List<NetworkDevice>,
    val devicesByType: Map<String, List<NetworkDevice>>,
    val unknownDevices: List<NetworkDevice>
)