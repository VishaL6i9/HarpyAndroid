package com.vishal.harpy.features.network_monitor.domain.usecases

import com.vishal.harpy.features.network_monitor.domain.usecases.base.NoneParamUseCase
import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository

class IsDeviceRootedUseCase(
    private val repository: NetworkMonitorRepository
) : NoneParamUseCase<Boolean>() {
    override suspend fun invoke(): Boolean {
        return repository.isDeviceRooted()
    }
}