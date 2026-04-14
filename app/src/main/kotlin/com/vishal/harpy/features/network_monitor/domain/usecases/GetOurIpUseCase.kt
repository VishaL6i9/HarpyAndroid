package com.vishal.harpy.features.network_monitor.domain.usecases

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import javax.inject.Inject

class GetOurIpUseCase @Inject constructor(
    private val repository: NetworkMonitorRepository
) {
    operator fun invoke(interfaceName: String? = null): String? {
        return repository.getOurIp(interfaceName)
    }
}
