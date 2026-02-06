package com.vishal.harpy.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vishal.harpy.ui.screens.dhcp.DHCPSpoofingScreen
import com.vishal.harpy.ui.screens.dns.DNSSpoofingScreen
import com.vishal.harpy.ui.screens.network.NetworkMonitorScreen
import com.vishal.harpy.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object NetworkMonitor : Screen("network_monitor", "Network Monitor", Icons.Default.NetworkCheck)
    object DNSSpoofing : Screen("dns_spoofing", "DNS Spoofing", Icons.Default.Dns)
    object DHCPSpoofing : Screen("dhcp_spoofing", "DHCP Spoofing", Icons.Default.Router)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarpyApp() {
    val navController = rememberNavController()
    var showSettingsScreen by remember { mutableStateOf(false) }
    
    val items = listOf(
        Screen.NetworkMonitor,
        Screen.DNSSpoofing,
        Screen.DHCPSpoofing
    )

    if (showSettingsScreen) {
        SettingsScreen(
            onNavigateBack = { showSettingsScreen = false }
        )
    } else {
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
                    NetworkMonitorScreen(onSettingsClick = { showSettingsScreen = true })
                }
                composable(Screen.DNSSpoofing.route) {
                    DNSSpoofingScreen(onSettingsClick = { showSettingsScreen = true })
                }
                composable(Screen.DHCPSpoofing.route) {
                    DHCPSpoofingScreen(onSettingsClick = { showSettingsScreen = true })
                }
            }
        }
    }
}
