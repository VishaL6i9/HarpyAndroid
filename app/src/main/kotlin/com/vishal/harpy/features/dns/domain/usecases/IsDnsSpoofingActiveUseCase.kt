package com.vishal.harpy.features.dns.domain.usecases

import com.vishal.harpy.features.dns.domain.repository.DnsRepository
import javax.inject.Inject

class IsDnsSpoofingActiveUseCase @Inject constructor(
    private val repository: DnsRepository
) {
    operator fun invoke(domain: String): Boolean {
        return repository.isDNSSpoofingActive(domain)
    }
}
