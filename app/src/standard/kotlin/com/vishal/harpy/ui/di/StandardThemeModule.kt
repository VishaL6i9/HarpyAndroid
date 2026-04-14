package com.vishal.harpy.ui.di

import androidx.compose.runtime.Composable
import com.vishal.harpy.ui.theme.StandardTheme
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Standard flavor theme provider implementation.
 * Provides the standard Material Design theme for the standard flavor.
 */
class StandardThemeProvider : ThemeProvider {
    @Composable
    override fun applyTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
        StandardTheme(darkTheme = darkTheme, content = content)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StandardThemeModule {
    @Binds
    @Singleton
    abstract fun bindThemeProvider(impl: StandardThemeProvider): ThemeProvider
}
