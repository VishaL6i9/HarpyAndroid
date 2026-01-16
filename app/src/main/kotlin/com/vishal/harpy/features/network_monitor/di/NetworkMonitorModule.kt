package com.vishal.harpy.features.network_monitor.di

import android.content.Context
import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.features.network_monitor.data.repository.impl.NetworkMonitorRepositoryImpl
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
    fun provideRepository(@ApplicationContext context: Context): NetworkMonitorRepository {
        return NetworkMonitorRepositoryImpl(context)
    }
}