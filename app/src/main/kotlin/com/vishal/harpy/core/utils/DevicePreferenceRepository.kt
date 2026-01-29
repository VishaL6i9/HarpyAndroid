package com.vishal.harpy.core.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DevicePreferenceRepository(context: Context) {
    companion object {
        private const val TAG = "DevicePreferenceRepo"
        private const val PREFS_NAME = "device_preferences"
        private const val KEY_PREFIX = "device_"
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Get preference for a specific device by MAC address
     */
    fun getDevicePreference(macAddress: String): DevicePreference? {
        return try {
            val key = KEY_PREFIX + macAddress.replace(":", "_")
            val json = sharedPreferences.getString(key, null) ?: return null
            parseDevicePreference(json, macAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device preference: ${e.message}")
            null
        }
    }
    
    /**
     * Save or update device preference
     */
    suspend fun saveDevicePreference(preference: DevicePreference) {
        withContext(Dispatchers.IO) {
            try {
                val key = KEY_PREFIX + preference.macAddress.replace(":", "_")
                val json = JSONObject().apply {
                    put("macAddress", preference.macAddress)
                    put("deviceName", preference.deviceName)
                    put("isPinned", preference.isPinned)
                    put("isBlocked", preference.isBlocked)
                    put("lastSeen", preference.lastSeen)
                }.toString()
                
                sharedPreferences.edit().putString(key, json).apply()
                Log.d(TAG, "Saved preference for ${preference.macAddress}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving device preference: ${e.message}")
            }
        }
    }
    
    /**
     * Set device name for a device
     */
    suspend fun setDeviceName(macAddress: String, deviceName: String?) {
        val preference = getDevicePreference(macAddress) ?: DevicePreference(macAddress)
        saveDevicePreference(preference.copy(deviceName = deviceName))
    }
    
    /**
     * Toggle pin status for a device
     */
    suspend fun togglePin(macAddress: String) {
        val preference = getDevicePreference(macAddress) ?: DevicePreference(macAddress)
        saveDevicePreference(preference.copy(isPinned = !preference.isPinned))
    }
    
    /**
     * Set blocked status for a device
     */
    suspend fun setBlockedStatus(macAddress: String, isBlocked: Boolean) {
        val preference = getDevicePreference(macAddress) ?: DevicePreference(macAddress)
        saveDevicePreference(preference.copy(isBlocked = isBlocked))
    }
    
    /**
     * Get all blocked devices (returns MAC addresses)
     */
    fun getAllBlockedDevices(): List<String> {
        return try {
            val blockedDevices = mutableListOf<String>()
            sharedPreferences.all.forEach { (key, value) ->
                if (key.startsWith(KEY_PREFIX) && value is String) {
                    val preference = parseDevicePreference(value, "")
                    if (preference?.isBlocked == true) {
                        preference.macAddress.let { blockedDevices.add(it) }
                    }
                }
            }
            blockedDevices
        } catch (e: Exception) {
            Log.e(TAG, "Error getting blocked devices: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Unblock all devices
     */
    suspend fun unblockAllDevices() {
        withContext(Dispatchers.IO) {
            try {
                val allPreferences = mutableListOf<DevicePreference>()
                sharedPreferences.all.forEach { (key, value) ->
                    if (key.startsWith(KEY_PREFIX) && value is String) {
                        val preference = parseDevicePreference(value, "")
                        if (preference != null && preference.isBlocked) {
                            allPreferences.add(preference.copy(isBlocked = false))
                        }
                    }
                }
                
                // Save all updated preferences
                allPreferences.forEach { preference ->
                    saveDevicePreference(preference)
                }
                
                Log.d(TAG, "Unblocked ${allPreferences.size} devices")
            } catch (e: Exception) {
                Log.e(TAG, "Error unblocking all devices: ${e.message}")
            }
        }
    }
    
    /**
     * Clean up blocked state for devices not in the current scan
     * This helps remove stale blocked states for devices that are no longer on the network
     */
    suspend fun cleanupStaleBlockedDevices(currentDeviceMacs: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                val staleDevices = mutableListOf<DevicePreference>()
                sharedPreferences.all.forEach { (key, value) ->
                    if (key.startsWith(KEY_PREFIX) && value is String) {
                        val preference = parseDevicePreference(value, "")
                        if (preference != null && preference.isBlocked && 
                            !currentDeviceMacs.contains(preference.macAddress)) {
                            staleDevices.add(preference.copy(isBlocked = false))
                        }
                    }
                }
                
                // Save all updated preferences
                staleDevices.forEach { preference ->
                    saveDevicePreference(preference)
                }
                
                if (staleDevices.isNotEmpty()) {
                    Log.d(TAG, "Cleaned up ${staleDevices.size} stale blocked devices")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up stale blocked devices: ${e.message}")
            }
        }
    }
    
    /**
     * Get all pinned devices
     */
    fun getPinnedDevices(): List<String> {
        return try {
            val pinnedDevices = mutableListOf<String>()
            sharedPreferences.all.forEach { (key, value) ->
                if (key.startsWith(KEY_PREFIX) && value is String) {
                    val preference = parseDevicePreference(value, "")
                    if (preference?.isPinned == true) {
                        preference.macAddress.let { pinnedDevices.add(it) }
                    }
                }
            }
            pinnedDevices
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pinned devices: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Delete preference for a device
     */
    suspend fun deleteDevicePreference(macAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                val key = KEY_PREFIX + macAddress.replace(":", "_")
                sharedPreferences.edit().remove(key).apply()
                Log.d(TAG, "Deleted preference for $macAddress")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting device preference: ${e.message}")
            }
        }
    }
    
    /**
     * Clear all preferences
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                sharedPreferences.edit().clear().apply()
                Log.d(TAG, "Cleared all device preferences")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing preferences: ${e.message}")
            }
        }
    }
    
    private fun parseDevicePreference(json: String, macAddress: String): DevicePreference? {
        return try {
            val obj = JSONObject(json)
            DevicePreference(
                macAddress = obj.getString("macAddress"),
                deviceName = obj.optString("deviceName", "").takeIf { it.isNotEmpty() },
                isPinned = obj.optBoolean("isPinned", false),
                isBlocked = obj.optBoolean("isBlocked", false),
                lastSeen = obj.optLong("lastSeen", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing device preference: ${e.message}")
            null
        }
    }
}
