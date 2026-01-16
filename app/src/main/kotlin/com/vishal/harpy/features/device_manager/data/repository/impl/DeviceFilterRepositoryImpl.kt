package com.vishal.harpy.features.device_manager.data.repository.impl

import com.vishal.harpy.features.device_manager.data.repository.DeviceFilterRepository
import com.vishal.harpy.core.utils.NetworkDevice
import android.util.Log

class DeviceFilterRepositoryImpl : DeviceFilterRepository {
    
    companion object {
        private const val TAG = "DeviceFilterRepoImpl"
    }
    
    private val blacklist = mutableSetOf<String>() // IP addresses
    private val whitelist = mutableSetOf<String>() // IP addresses
    
    override suspend fun addToBlacklist(device: NetworkDevice): Boolean {
        synchronized(blacklist) {
            val result = blacklist.add(device.ipAddress)
            // Remove from whitelist if it was there
            removeFromWhitelist(device)
            Log.d(TAG, "Added ${device.ipAddress} to blacklist")
            return result
        }
    }
    
    override suspend fun addToWhitelist(device: NetworkDevice): Boolean {
        synchronized(whitelist) {
            val result = whitelist.add(device.ipAddress)
            // Remove from blacklist if it was there
            removeFromBlacklist(device)
            Log.d(TAG, "Added ${device.ipAddress} to whitelist")
            return result
        }
    }
    
    override suspend fun removeFromBlacklist(device: NetworkDevice): Boolean {
        synchronized(blacklist) {
            val result = blacklist.remove(device.ipAddress)
            Log.d(TAG, "Removed ${device.ipAddress} from blacklist")
            return result
        }
    }
    
    override suspend fun removeFromWhitelist(device: NetworkDevice): Boolean {
        synchronized(whitelist) {
            val result = whitelist.remove(device.ipAddress)
            Log.d(TAG, "Removed ${device.ipAddress} from whitelist")
            return result
        }
    }
    
    override suspend fun isBlacklisted(device: NetworkDevice): Boolean {
        synchronized(blacklist) {
            return device.ipAddress in blacklist
        }
    }
    
    override suspend fun isWhitelisted(device: NetworkDevice): Boolean {
        synchronized(whitelist) {
            return device.ipAddress in whitelist
        }
    }
    
    override suspend fun getBlacklistedDevices(allDevices: List<NetworkDevice>): List<NetworkDevice> {
        synchronized(blacklist) {
            return allDevices.filter { it.ipAddress in blacklist }
        }
    }
    
    override suspend fun getWhitelistedDevices(allDevices: List<NetworkDevice>): List<NetworkDevice> {
        synchronized(whitelist) {
            return allDevices.filter { it.ipAddress in whitelist }
        }
    }
    
    override suspend fun clearBlacklist() {
        synchronized(blacklist) {
            blacklist.clear()
            Log.d(TAG, "Blacklist cleared")
        }
    }
    
    override suspend fun clearWhitelist() {
        synchronized(whitelist) {
            whitelist.clear()
            Log.d(TAG, "Whitelist cleared")
        }
    }
    
    override suspend fun getBlacklistCount(): Int {
        synchronized(blacklist) {
            return blacklist.size
        }
    }
    
    override suspend fun getWhitelistCount(): Int {
        synchronized(whitelist) {
            return whitelist.size
        }
    }
}