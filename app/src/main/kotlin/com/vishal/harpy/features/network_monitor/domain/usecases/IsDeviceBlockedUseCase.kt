package com.vishal.harpy.features.network_monitor.domain.usecases

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import javax.inject.Inject

class IsDeviceBlockedUseCase @Inject constructor(
    private val repository: NetworkMonitorRepository
) {
    operator fun invoke(ipAddress: String): Boolean {
        return repository.isDeviceBlocked(ipAddress)
    }
}
