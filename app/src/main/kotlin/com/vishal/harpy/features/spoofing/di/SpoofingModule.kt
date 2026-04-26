package com.vishal.harpy.features.spoofing.di

import android.content.Context
import com.vishal.harpy.core.state.SpoofingSessionManager
import com.vishal.harpy.core.state.SpoofingSessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpoofingModule {

    @Provides
    @Singleton
    fun provideSpoofingSessionRepository(
        @ApplicationContext context: Context
    ): SpoofingSessionRepository {
        return SpoofingSessionRepository(context)
    }

    @Provides
    @Singleton
    fun provideSpoofingSessionManager(
        sessionRepository: SpoofingSessionRepository
    ): SpoofingSessionManager {
        return SpoofingSessionManager(sessionRepository)
    }
}
