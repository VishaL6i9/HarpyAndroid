package com.vishal.harpy.features.dns.domain.usecases

import com.vishal.harpy.features.dns.domain.repository.DnsRepository
import com.vishal.harpy.core.utils.NetworkResult
import javax.inject.Inject

class StartDnsSpoofingUseCase @Inject constructor(
    private val repository: DnsRepository
) {
    suspend operator fun invoke(domain: String, spoofedIP: String, interfaceName: String = "wlan0"): NetworkResult<Boolean> {
        return repository.startDNSSpoofing(domain, spoofedIP, interfaceName)
    }
}
