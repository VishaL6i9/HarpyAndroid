package com.vishal.harpy.features.dhcp.di

import android.content.Context
import com.vishal.harpy.features.dhcp.data.repository.DhcpRepositoryImpl
import com.vishal.harpy.features.dhcp.domain.repository.DhcpRepository
import com.vishal.harpy.features.network_monitor.domain.usecases.IsDeviceRootedUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DhcpModule {

    @Provides
    @Singleton
    fun provideDhcpRepository(
        @ApplicationContext context: Context,
        isDeviceRootedUseCase: IsDeviceRootedUseCase
    ): DhcpRepository {
        return DhcpRepositoryImpl(context, isDeviceRootedUseCase)
    }
}
