package com.vishal.harpy.core.utils

/**
 * Data class representing the application's general settings.
 */
data class AppSettings(
    val scanTimeoutSeconds: Int = 10,
    val networkInterface: String = "wlan0",
    val isDebugMode: Boolean = false,
    val isVerboseLogging: Boolean = false
)
