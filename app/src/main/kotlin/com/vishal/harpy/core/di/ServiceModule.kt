package com.vishal.harpy.core.di

import android.content.Context
import com.vishal.harpy.core.service.HarpyNotificationManager
import com.vishal.harpy.core.service.ServiceController
import com.vishal.harpy.core.state.SpoofingStateManager
import com.vishal.harpy.core.utils.PerformanceMonitor
import com.vishal.harpy.core.utils.RootCommandExecutor
import com.vishal.harpy.core.utils.ThemeManager
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

    @Singleton
    @Provides
    fun provideThemeManager(
        @ApplicationContext context: Context
    ): ThemeManager = ThemeManager(context)

    @Singleton
    @Provides
    fun provideSpoofingStateManager(): SpoofingStateManager = SpoofingStateManager()

    @Singleton
    @Provides
    fun providePerformanceMonitor(
        rootCommandExecutor: RootCommandExecutor
    ): PerformanceMonitor = PerformanceMonitor(rootCommandExecutor)

    @Singleton
    @Provides
    fun provideRootCommandExecutor(): RootCommandExecutor = RootCommandExecutor()
}
