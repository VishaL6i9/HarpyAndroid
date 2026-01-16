package com.vishal.harpy.features.network_monitor.di

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.features.network_monitor.data.repository.impl.NetworkMonitorRepositoryImpl
import com.vishal.harpy.features.network_monitor.domain.usecases.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkMonitorModule {

    @Provides
    @Singleton
    fun provideRepository(): NetworkMonitorRepository {
        return NetworkMonitorRepositoryImpl()
    }

    @Provides
    @Singleton
    fun provideScanNetworkUseCase(repository: NetworkMonitorRepository): ScanNetworkUseCase {
        return ScanNetworkUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideIsDeviceRootedUseCase(repository: NetworkMonitorRepository): IsDeviceRootedUseCase {
        return IsDeviceRootedUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideBlockDeviceUseCase(repository: NetworkMonitorRepository): BlockDeviceUseCase {
        return BlockDeviceUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideUnblockDeviceUseCase(repository: NetworkMonitorRepository): UnblockDeviceUseCase {
        return UnblockDeviceUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideMapNetworkTopologyUseCase(repository: NetworkMonitorRepository): MapNetworkTopologyUseCase {
        return MapNetworkTopologyUseCase(repository)
    }
}