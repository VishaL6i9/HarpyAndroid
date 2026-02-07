package com.vishal.harpy.features.dns.domain.usecases

import com.vishal.harpy.features.dns.domain.repository.DnsRepository
import com.vishal.harpy.core.utils.NetworkResult
import javax.inject.Inject

class StopDnsSpoofingUseCase @Inject constructor(
    private val repository: DnsRepository
) {
    suspend operator fun invoke(domain: String? = null): NetworkResult<Boolean> {
        return if (domain != null) {
            repository.stopDNSSpoofing(domain)
        } else {
             // For now, if no domain is provided, we can return false or handle "stop all" 
             // ideally the repository should support stopAllDNSSpoofing
             // For the existing logic migration, we only support specific domain stopping or need to implement stopAll in repo
             // Refactoring Note: The parameterless stopDNSSpoofing in ViewModel was a stub. 
             // We will assume for now it meant to stop specific ones or we need to expand repository.
             // Given the VM stub: "Stopping all DNS spoofing processes is not implemented yet"
             // We will stick to the domain signature for now and can expand later.
             NetworkResult.success(false) 
        }
    }
}
