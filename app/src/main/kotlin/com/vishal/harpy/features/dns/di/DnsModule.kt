package com.vishal.harpy.features.dns.di

import android.content.Context
import com.vishal.harpy.features.dns.data.repository.DnsRepositoryImpl
import com.vishal.harpy.features.dns.domain.repository.DnsRepository
import com.vishal.harpy.features.network_monitor.domain.usecases.IsDeviceRootedUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DnsModule {

    @Provides
    @Singleton
    fun provideDnsRepository(
        @ApplicationContext context: Context,
        isDeviceRootedUseCase: IsDeviceRootedUseCase
    ): DnsRepository {
        return DnsRepositoryImpl(context, isDeviceRootedUseCase)
    }
}
