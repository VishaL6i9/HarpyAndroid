package com.vishal.harpy.features.network_monitor.domain.usecases

import com.vishal.harpy.features.network_monitor.domain.usecases.base.NoneParamUseCase
import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.core.utils.NetworkTopology

class MapNetworkTopologyUseCase(
    private val repository: NetworkMonitorRepository
) : NoneParamUseCase<NetworkTopology>() {
    override suspend fun invoke(): NetworkTopology {
        return repository.mapNetworkTopology()
    }
}