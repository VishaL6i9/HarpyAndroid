package com.vishal.harpy.features.network_monitor.data.repository

import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.core.utils.NetworkTopology
import com.vishal.harpy.core.utils.NetworkResult

interface NetworkMonitorRepository {
    suspend fun scanNetwork(interfaceName: String? = null): NetworkResult<List<NetworkDevice>>
    suspend fun isDeviceRooted(): NetworkResult<Boolean>
    suspend fun blockDevice(device: NetworkDevice, interfaceName: String? = null): NetworkResult<Boolean>
    suspend fun unblockDevice(device: NetworkDevice, interfaceName: String? = null): NetworkResult<Boolean>
    suspend fun unblockAllDevices(interfaceName: String? = null): NetworkResult<Int>
    suspend fun mapNetworkTopology(interfaceName: String? = null): NetworkResult<NetworkTopology>
    suspend fun testPing(device: NetworkDevice): NetworkResult<Boolean>
    fun isDeviceBlocked(ipAddress: String): Boolean
    suspend fun restoreBlockedDevices(devices: List<NetworkDevice>, interfaceName: String? = null): NetworkResult<Int>
    fun getActiveInterface(): String?
}