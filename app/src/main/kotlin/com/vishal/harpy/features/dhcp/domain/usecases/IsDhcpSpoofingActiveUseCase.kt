package com.vishal.harpy.features.dhcp.domain.usecases

import com.vishal.harpy.features.dhcp.domain.repository.DhcpRepository
import javax.inject.Inject

class IsDhcpSpoofingActiveUseCase @Inject constructor(
    private val repository: DhcpRepository
) {
    operator fun invoke(): Boolean {
        return repository.isDHCPSpoofingActive()
    }
}
