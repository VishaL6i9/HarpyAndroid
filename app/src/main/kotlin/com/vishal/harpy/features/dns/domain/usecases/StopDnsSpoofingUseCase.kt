package com.vishal.harpy.features.dns.domain.usecases

import com.vishal.harpy.features.dns.domain.repository.DnsRepository
import com.vishal.harpy.core.utils.NetworkResult
import javax.inject.Inject

class StopDnsSpoofingUseCase @Inject constructor(
    private val repository: DnsRepository
) {
    suspend operator fun invoke(domain: String): NetworkResult<Boolean> {
        return repository.stopDNSSpoofing(domain)
    }
}
