package com.vishal.harpy.features.network_monitor.domain.usecases

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.core.utils.NetworkResult
import javax.inject.Inject

class BlockDeviceUseCase @Inject constructor(
    private val repository: NetworkMonitorRepository
) {
    suspend operator fun invoke(device: NetworkDevice): NetworkResult<Boolean> {
        return repository.blockDevice(device)
    }
}