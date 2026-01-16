package com.vishal.harpy.features.network_monitor.di

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.features.network_monitor.data.repository.impl.NetworkMonitorRepositoryImpl
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
}