package com.vishal.harpy.features.network_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vishal.harpy.features.network_monitor.domain.usecases.*

@Suppress("UNCHECKED_CAST")
class NetworkMonitorViewModelFactory(
    private val scanNetworkUseCase: ScanNetworkUseCase,
    private val isDeviceRootedUseCase: IsDeviceRootedUseCase,
    private val blockDeviceUseCase: BlockDeviceUseCase,
    private val unblockDeviceUseCase: UnblockDeviceUseCase,
    private val mapNetworkTopologyUseCase: MapNetworkTopologyUseCase
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NetworkMonitorViewModel::class.java)) {
            return NetworkMonitorViewModel(
                scanNetworkUseCase,
                isDeviceRootedUseCase,
                blockDeviceUseCase,
                unblockDeviceUseCase,
                mapNetworkTopologyUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}