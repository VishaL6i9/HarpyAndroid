package com.vishal.harpy.features.network_monitor.domain.usecases

import com.vishal.harpy.features.network_monitor.domain.usecases.base.NoneParamUseCase
import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.core.utils.NetworkDevice

class BlockDeviceUseCase(
    private val repository: NetworkMonitorRepository
) {
    suspend operator fun invoke(device: NetworkDevice): Boolean {
        return repository.blockDevice(device)
    }
}