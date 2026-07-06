package com.vishal.harpy.features.ios_attack.di

import android.content.Context
import com.vishal.harpy.features.ios_attack.data.IosAttackRepositoryImpl
import com.vishal.harpy.features.ios_attack.domain.IosAttackRepository
import com.vishal.harpy.features.network_monitor.domain.usecases.IsDeviceRootedUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IosAttackModule {

    @Provides
    @Singleton
    fun provideIosAttackRepository(
        @ApplicationContext context: Context,
        isDeviceRootedUseCase: IsDeviceRootedUseCase
    ): IosAttackRepository {
        return IosAttackRepositoryImpl(context, isDeviceRootedUseCase)
    }
}
