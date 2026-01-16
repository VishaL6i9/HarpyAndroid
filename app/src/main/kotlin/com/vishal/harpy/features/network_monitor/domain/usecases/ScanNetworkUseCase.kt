package com.vishal.harpy.features.network_monitor.domain.usecases

import com.vishal.harpy.features.network_monitor.domain.usecases.base.NoneParamUseCase
import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.core.utils.NetworkDevice

class ScanNetworkUseCase(
    private val repository: NetworkMonitorRepository
) : NoneParamUseCase<List<NetworkDevice>>() {
    override suspend fun invoke(): List<NetworkDevice> {
        return repository.scanNetwork()
    }
}