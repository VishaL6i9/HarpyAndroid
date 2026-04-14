package com.vishal.harpy.features.spoofing.di

import com.vishal.harpy.core.state.SpoofingSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpoofingModule {

    @Provides
    @Singleton
    fun provideSpoofingSessionManager(): SpoofingSessionManager {
        return SpoofingSessionManager()
    }
}
