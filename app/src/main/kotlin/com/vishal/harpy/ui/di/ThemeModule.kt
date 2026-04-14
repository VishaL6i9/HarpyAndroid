package com.vishal.harpy.ui.di

import androidx.compose.runtime.Composable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Base theme provider interface for dependency injection.
 * Flavor-specific implementations will be provided by flavor-specific modules.
 */
interface ThemeProvider {
    @Composable
    fun applyTheme(darkTheme: Boolean, content: @Composable () -> Unit)
}

@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {
    // This will be overridden by flavor-specific modules
}
