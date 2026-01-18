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
    val isGranted: Boolean
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
    }

    fun getAllPermissions(context: Context): List<PermissionInfo> {
        return REQUIRED_PERMISSIONS.map { perm ->
            val isGranted = isPermissionGranted(context, perm.permission)
            perm.copy(isGranted = isGranted)
        }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        return when (permission) {
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
        return getAllPermissions(context).filter { !it.isGranted }
    }

    fun areAllPermissionsGranted(context: Context): Boolean {
        return getMissingPermissions(context).isEmpty()
    }
}