package com.vishal.harpy.features.dhcp.domain.repository

import com.vishal.harpy.core.utils.NetworkResult

interface DhcpRepository {
    suspend fun startDHCPSpoofing(
        interfaceName: String,
        targetMacs: Array<String>,
        spoofedIPs: Array<String>,
        gatewayIPs: Array<String>,
        subnetMasks: Array<String>,
        dnsServers: Array<String>
    ): NetworkResult<Boolean>
    
    suspend fun stopDHCPSpoofing(): NetworkResult<Boolean>
    fun isDHCPSpoofingActive(): Boolean
}
