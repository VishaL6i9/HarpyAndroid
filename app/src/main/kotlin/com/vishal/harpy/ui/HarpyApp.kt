package com.vishal.harpy.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vishal.harpy.core.di.ServiceEntryPoint
import com.vishal.harpy.core.utils.ThemeManager
import com.vishal.harpy.ui.screens.dhcp.DHCPSpoofingScreen
import com.vishal.harpy.ui.screens.dns.DNSSpoofingScreen
import com.vishal.harpy.ui.screens.network.NetworkMonitorScreen
import com.vishal.harpy.ui.screens.settings.SettingsScreen
import com.vishal.harpy.ui.theme.HarpyTheme
import dagger.hilt.android.EntryPointAccessors

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object NetworkMonitor : Screen("network_monitor", "Network Monitor", Icons.Default.NetworkCheck)
    object DNSSpoofing : Screen("dns_spoofing", "DNS Spoofing", Icons.Default.Dns)
    object DHCPSpoofing : Screen("dhcp_spoofing", "DHCP Spoofing", Icons.Default.Router)
}

/**
 * Sealed class representing all app screens for navigation stack management
 */
sealed class AppScreen {
    object NetworkMonitor : AppScreen()
    object DnsSpoofing : AppScreen()
    object DhcpSpoofing : AppScreen()
    object Settings : AppScreen()
    object DeviceManagement : AppScreen()
    object DetailedStatus : AppScreen()
    object PerformanceMonitor : AppScreen()
    object ProcessList : AppScreen()
    object About : AppScreen()
}

/**
 * Holds scroll state for a screen to restore on navigation back
 */
data class ScrollStateHolder(
    val scrollOffset: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarpyApp() {
    val context = LocalContext.current
    val themeManager = remember {
        EntryPointAccessors.fromApplication(context, ServiceEntryPoint::class.java).getThemeManager()
    }
    val navController = rememberNavController()
    
    // Navigation stack for proper back navigation with scroll state
    data class NavigationEntry(
        val screen: AppScreen,
        val scrollOffset: Int = 0
    )
    
    val navigationStack = remember { mutableStateListOf<NavigationEntry>() }
    val currentScreen = remember { mutableStateOf<NavigationEntry?>(null) }
    val scrollStateMap = remember { mutableStateMapOf<AppScreen, Int>() }
    
    // Helper functions for stack-based navigation with scroll state preservation
    fun navigateTo(screen: AppScreen) {
        // Save current scroll state before navigating
        currentScreen.value?.let { entry ->
            scrollStateMap[entry.screen] = entry.scrollOffset
        }
        navigationStack.add(currentScreen.value ?: NavigationEntry(AppScreen.NetworkMonitor))
        currentScreen.value = NavigationEntry(screen, scrollStateMap[screen] ?: 0)
    }
    
    fun navigateBack() {
        // Save current scroll state
        currentScreen.value?.let { entry ->
            scrollStateMap[entry.screen] = entry.scrollOffset
        }
        
        currentScreen.value = if (navigationStack.isNotEmpty()) {
            navigationStack.removeAt(navigationStack.lastIndex)
        } else {
            null
        }
    }
    
    fun updateScrollOffset(offset: Int) {
        currentScreen.value = currentScreen.value?.copy(scrollOffset = offset)
    }

    val themeMode by themeManager.themeMode.collectAsStateWithLifecycle()
    val systemDarkMode = isSystemInDarkTheme()
    val isDarkMode = themeManager.isDarkMode(systemDarkMode)

    val items = listOf(
        Screen.NetworkMonitor,
        Screen.DNSSpoofing,
        Screen.DHCPSpoofing
    )

    HarpyTheme(darkTheme = isDarkMode) {
        // Determine which screen to show based on current navigation state
        val screenToShow = currentScreen.value

        when (screenToShow?.screen) {
            AppScreen.Settings -> {
                SettingsScreen(
                    onNavigateBack = { navigateBack() },
                    onNavigateToDeviceManagement = { navigateTo(AppScreen.DeviceManagement) },
                    onNavigateToPerformanceMonitor = { navigateTo(AppScreen.PerformanceMonitor) },
                    savedScrollOffset = screenToShow.scrollOffset,
                    onScrollOffsetChanged = { updateScrollOffset(it) }
                )
            }
            AppScreen.DeviceManagement -> {
                com.vishal.harpy.ui.screens.device_management.DeviceManagementScreen(
                    onNavigateBack = { navigateBack() },
                    savedScrollOffset = screenToShow.scrollOffset,
                    onScrollOffsetChanged = { updateScrollOffset(it) }
                )
            }
            AppScreen.DetailedStatus -> {
                com.vishal.harpy.ui.screens.status.DetailedStatusScreen(
                    onNavigateBack = { navigateBack() },
                    savedScrollOffset = screenToShow.scrollOffset,
                    onScrollOffsetChanged = { updateScrollOffset(it) }
                )
            }
            AppScreen.PerformanceMonitor -> {
                com.vishal.harpy.ui.screens.performance.PerformanceMonitorScreen(
                    onNavigateBack = { navigateBack() },
                    onNavigateToProcessList = { navigateTo(AppScreen.ProcessList) },
                    savedScrollOffset = screenToShow.scrollOffset,
                    onScrollOffsetChanged = { updateScrollOffset(it) }
                )
            }
            AppScreen.ProcessList -> {
                com.vishal.harpy.ui.screens.performance.ProcessListScreen(
                    onNavigateBack = { navigateBack() },
                    savedScrollOffset = screenToShow.scrollOffset,
                    onScrollOffsetChanged = { updateScrollOffset(it) }
                )
            }
            AppScreen.About -> {
                com.vishal.harpy.ui.screens.settings.AboutScreen(
                    onNavigateBack = { navigateBack() },
                    savedScrollOffset = screenToShow.scrollOffset,
                    onScrollOffsetChanged = { updateScrollOffset(it) }
                )
            }
            null,
            AppScreen.NetworkMonitor, AppScreen.DnsSpoofing, AppScreen.DhcpSpoofing -> {
                // Show main app with bottom navigation
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination

                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) },
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.NetworkMonitor.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.NetworkMonitor.route) {
                            NetworkMonitorScreen(
                                onSettingsClick = { navigateTo(AppScreen.Settings) },
                                onStatusClick = { navigateTo(AppScreen.DetailedStatus) }
                            )
                        }
                        composable(Screen.DNSSpoofing.route) {
                            DNSSpoofingScreen(onSettingsClick = { navigateTo(AppScreen.Settings) })
                        }
                        composable(Screen.DHCPSpoofing.route) {
                            DHCPSpoofingScreen(onSettingsClick = { navigateTo(AppScreen.Settings) })
                        }
                    }
                }
            }
        }
    }
}
