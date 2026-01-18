package com.vishal.harpy.features.network_monitor.domain.usecases

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.core.utils.NetworkResult
import javax.inject.Inject

class ScanNetworkUseCase @Inject constructor(
    val repository: NetworkMonitorRepository  // Changed from private to public
) {
    suspend operator fun invoke(): NetworkResult<List<NetworkDevice>> {
        return repository.scanNetwork()
    }
}