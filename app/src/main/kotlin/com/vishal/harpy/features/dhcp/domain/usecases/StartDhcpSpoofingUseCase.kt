package com.vishal.harpy.features.dhcp.domain.usecases

import com.vishal.harpy.features.dhcp.domain.repository.DhcpRepository
import com.vishal.harpy.core.utils.NetworkResult
import javax.inject.Inject

class StartDhcpSpoofingUseCase @Inject constructor(
    private val repository: DhcpRepository
) {
    suspend operator fun invoke(
        interfaceName: String = "wlan0",
        targetMacs: Array<String>,
        spoofedIPs: Array<String>,
        gatewayIPs: Array<String>,
        subnetMasks: Array<String>,
        dnsServers: Array<String>
    ): NetworkResult<Boolean> {
        return repository.startDHCPSpoofing(
            interfaceName,
            targetMacs,
            spoofedIPs,
            gatewayIPs,
            subnetMasks,
            dnsServers
        )
    }
}
