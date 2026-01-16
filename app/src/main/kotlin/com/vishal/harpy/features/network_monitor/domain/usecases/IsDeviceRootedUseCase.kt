package com.vishal.harpy.features.network_monitor.domain.usecases

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.core.utils.NetworkResult

class IsDeviceRootedUseCase(
    private val repository: NetworkMonitorRepository
) {
    suspend operator fun invoke(): NetworkResult<Boolean> {
        return repository.isDeviceRooted()
    }
}