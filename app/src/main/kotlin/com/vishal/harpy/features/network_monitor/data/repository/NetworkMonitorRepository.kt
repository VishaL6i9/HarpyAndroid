package com.vishal.harpy.features.network_monitor.data.repository

import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.core.utils.NetworkTopology
import com.vishal.harpy.core.utils.NetworkResult

interface NetworkMonitorRepository {
    suspend fun scanNetwork(): NetworkResult<List<NetworkDevice>>
    suspend fun isDeviceRooted(): NetworkResult<Boolean>
    suspend fun blockDevice(device: NetworkDevice): NetworkResult<Boolean>
    suspend fun unblockDevice(device: NetworkDevice): NetworkResult<Boolean>
    suspend fun mapNetworkTopology(): NetworkResult<NetworkTopology>
    suspend fun testPing(device: NetworkDevice): NetworkResult<Boolean>

    // DNS Spoofing methods
    suspend fun startDNSSpoofing(domain: String, spoofedIP: String, interfaceName: String): NetworkResult<Boolean>
    suspend fun stopDNSSpoofing(domain: String): NetworkResult<Boolean>
    fun isDNSSpoofingActive(domain: String): Boolean
}