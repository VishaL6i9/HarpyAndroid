package com.vishal.harpy.features.device_manager.data.repository

import com.vishal.harpy.core.utils.NetworkDevice

interface DeviceFilterRepository {
    suspend fun addToBlacklist(device: NetworkDevice): Boolean
    suspend fun addToWhitelist(device: NetworkDevice): Boolean
    suspend fun removeFromBlacklist(device: NetworkDevice): Boolean
    suspend fun removeFromWhitelist(device: NetworkDevice): Boolean
    suspend fun isBlacklisted(device: NetworkDevice): Boolean
    suspend fun isWhitelisted(device: NetworkDevice): Boolean
    suspend fun getBlacklistedDevices(allDevices: List<NetworkDevice>): List<NetworkDevice>
    suspend fun getWhitelistedDevices(allDevices: List<NetworkDevice>): List<NetworkDevice>
    suspend fun clearBlacklist()
    suspend fun clearWhitelist()
    suspend fun getBlacklistCount(): Int
    suspend fun getWhitelistCount(): Int
}