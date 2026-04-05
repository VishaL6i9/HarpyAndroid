package com.vishal.harpy.features.network_monitor.domain.usecases

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import javax.inject.Inject

/**
 * Use case to get the system's current active network interface name.
 */
class GetActiveInterfaceUseCase @Inject constructor(
    private val repository: NetworkMonitorRepository
) {
    operator fun invoke(): String? {
        return repository.getActiveInterface()
    }
}
