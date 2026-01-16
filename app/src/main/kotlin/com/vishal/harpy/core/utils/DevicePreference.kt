package com.vishal.harpy.core.utils

/**
 * Represents user preferences for a network device
 * Persisted to SharedPreferences
 */
data class DevicePreference(
    val macAddress: String,
    val deviceName: String? = null,
    val isPinned: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
) {
    fun getDisplayName(defaultVendor: String?): String {
        return deviceName ?: defaultVendor ?: "Unknown Device"
    }
}
