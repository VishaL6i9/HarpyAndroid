package com.vishal.harpy.features.network_monitor.data.repository

import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.core.utils.NetworkTopology

interface NetworkMonitorRepository {
    suspend fun scanNetwork(): List<NetworkDevice>
    suspend fun isDeviceRooted(): Boolean
    suspend fun blockDevice(device: NetworkDevice): Boolean
    suspend fun unblockDevice(device: NetworkDevice): Boolean
    suspend fun mapNetworkTopology(): NetworkTopology
}