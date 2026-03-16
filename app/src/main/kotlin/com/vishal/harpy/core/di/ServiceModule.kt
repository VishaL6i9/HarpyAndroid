package com.vishal.harpy.core.di

import android.content.Context
import com.vishal.harpy.core.service.HarpyNotificationManager
import com.vishal.harpy.core.service.ServiceController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Singleton
    @Provides
    fun provideHarpyNotificationManager(
        @ApplicationContext context: Context
    ): HarpyNotificationManager = HarpyNotificationManager(context)

    @Singleton
    @Provides
    fun provideServiceController(
        @ApplicationContext context: Context
    ): ServiceController = ServiceController(context)
}
