package com.vishal.harpy.features.network_monitor.di

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.features.network_monitor.data.repository.impl.NetworkMonitorRepositoryImpl
import com.vishal.harpy.features.network_monitor.domain.usecases.*

object NetworkMonitorModule {
    
    fun provideRepository(): NetworkMonitorRepository {
        return NetworkMonitorRepositoryImpl()
    }
    
    fun provideScanNetworkUseCase(): ScanNetworkUseCase {
        return ScanNetworkUseCase(provideRepository())
    }
    
    fun provideIsDeviceRootedUseCase(): IsDeviceRootedUseCase {
        return IsDeviceRootedUseCase(provideRepository())
    }
    
    fun provideBlockDeviceUseCase(): BlockDeviceUseCase {
        return BlockDeviceUseCase(provideRepository())
    }
    
    fun provideUnblockDeviceUseCase(): UnblockDeviceUseCase {
        return UnblockDeviceUseCase(provideRepository())
    }
    
    fun provideMapNetworkTopologyUseCase(): MapNetworkTopologyUseCase {
        return MapNetworkTopologyUseCase(provideRepository())
    }
    
    fun provideViewModelFactory(): NetworkMonitorViewModelFactory {
        return NetworkMonitorViewModelFactory(
            provideScanNetworkUseCase(),
            provideIsDeviceRootedUseCase(),
            provideBlockDeviceUseCase(),
            provideUnblockDeviceUseCase(),
            provideMapNetworkTopologyUseCase()
        )
    }
}