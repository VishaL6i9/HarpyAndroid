package com.vishal.harpy.features.network_monitor.di

import android.content.Context
import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.features.network_monitor.data.repository.impl.NetworkMonitorRepositoryImpl
import com.vishal.harpy.core.state.DeviceBlockingConfigRepository
import com.vishal.harpy.core.utils.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkMonitorModule {

    @Provides
    @Singleton
    fun provideRepository(
        @ApplicationContext context: Context,
        deviceBlockingConfigRepository: DeviceBlockingConfigRepository,
        settingsRepository: SettingsRepository
    ): NetworkMonitorRepository {
        return NetworkMonitorRepositoryImpl(context, deviceBlockingConfigRepository, settingsRepository)
    }
}