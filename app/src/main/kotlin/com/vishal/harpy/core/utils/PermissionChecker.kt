package com.vishal.harpy.core.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

data class PermissionInfo(
    val permission: String,
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val isWarning: Boolean = false
)

object PermissionChecker {

    private val REQUIRED_PERMISSIONS = mutableListOf(
        PermissionInfo(
            permission = Manifest.permission.INTERNET,
            name = "Internet Access",
            description = "Required for network monitoring",
            isGranted = false
        ),
        PermissionInfo(
            permission = Manifest.permission.ACCESS_NETWORK_STATE,
            name = "Access Network State",
            description = "Required to monitor network connections",
            isGranted = false
        ),
        PermissionInfo(
            permission = Manifest.permission.ACCESS_WIFI_STATE,
            name = "Access WiFi State",
            description = "Required to monitor WiFi networks",
            isGranted = false
        ),
        PermissionInfo(
            permission = Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            name = "Change WiFi Multicast State",
            description = "Required for network discovery",
            isGranted = false
        ),
        PermissionInfo(
            permission = Manifest.permission.VIBRATE,
            name = "Vibration",
            description = "Required for haptic feedback",
            isGranted = false
        )
    )

    // Add storage permission conditionally based on Android version
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, use MANAGE_EXTERNAL_STORAGE
            REQUIRED_PERMISSIONS.add(
                PermissionInfo(
                    permission = android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                    name = "Manage External Storage",
                    description = "Required to export logs",
                    isGranted = false
                )
            )
        } else {
            // For older versions, use WRITE_EXTERNAL_STORAGE
            REQUIRED_PERMISSIONS.add(
                PermissionInfo(
                    permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    name = "Write External Storage",
                    description = "Required to export logs",
                    isGranted = false
                )
            )
        }

        // Add DNS property access warning (varies by SDK)
        val dnsWarning = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10+ has stricter property access restrictions
                PermissionInfo(
                    permission = "dns_property_access",
                    name = "DNS Property Access",
                    description = "System DNS properties restricted on Android 10+. Using alternative detection methods.",
                    isGranted = false,
                    isWarning = true
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                // Android 9 has moderate restrictions
                PermissionInfo(
                    permission = "dns_property_access",
                    name = "DNS Property Access",
                    description = "Some DNS properties may require elevated permissions on Android 9+.",
                    isGranted = false,
                    isWarning = true
                )
            }
            else -> {
                // Android 7-8 has minimal restrictions
                PermissionInfo(
                    permission = "dns_property_access",
                    name = "DNS Property Access",
                    description = "DNS property access available with standard permissions.",
                    isGranted = true,
                    isWarning = false
                )
            }
        }
        REQUIRED_PERMISSIONS.add(dnsWarning)
    }

    fun getAllPermissions(context: Context): List<PermissionInfo> {
        return REQUIRED_PERMISSIONS.map { perm ->
            val isGranted = isPermissionGranted(context, perm.permission)
            perm.copy(isGranted = isGranted)
        }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        return when (permission) {
            "dns_property_access" -> {
                // DNS property access is always considered "not granted" as a warning
                // This is informational - the app will handle DNS access gracefully
                false
            }
            android.Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
            }
            else -> {
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    fun getMissingPermissions(context: Context): List<PermissionInfo> {
        return getAllPermissions(context).filter { !it.isGranted && !it.isWarning }
    }

    fun getWarnings(context: Context): List<PermissionInfo> {
        return getAllPermissions(context).filter { it.isWarning }
    }

    fun areAllPermissionsGranted(context: Context): Boolean {
        return getMissingPermissions(context).isEmpty()
    }
}