package com.vishal.harpy.ui.di

import androidx.compose.runtime.Composable
import com.vishal.harpy.ui.theme.CtosTheme
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * CTOS flavor theme provider implementation.
 * Provides the custom CTOS dark theme for the ctos flavor.
 */
class CtosThemeProvider : ThemeProvider {
    @Composable
    override fun applyTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
        // CTOS theme is always dark, ignoring the darkTheme parameter
        CtosTheme(content = content)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CtosThemeModule {
    @Binds
    @Singleton
    abstract fun bindThemeProvider(impl: CtosThemeProvider): ThemeProvider
}
