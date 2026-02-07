package com.vishal.harpy.features.dhcp.domain.usecases

import com.vishal.harpy.features.dhcp.domain.repository.DhcpRepository
import com.vishal.harpy.core.utils.NetworkResult
import javax.inject.Inject

class StopDhcpSpoofingUseCase @Inject constructor(
    private val repository: DhcpRepository
) {
    suspend operator fun invoke(): NetworkResult<Boolean> {
        return repository.stopDHCPSpoofing()
    }
}
