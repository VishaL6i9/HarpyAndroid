package com.vishal.harpy.core.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SettingsRepository"
        private const val PREFS_NAME = "harpy_settings"
        private const val KEY_SCAN_TIMEOUT = "scan_timeout"
        private const val KEY_NETWORK_INTERFACE = "network_interface"
        private const val KEY_DEBUG_MODE = "debug_mode"
        private const val KEY_VERBOSE_LOGGING = "verbose_logging"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun loadSettings(): AppSettings {
        return AppSettings(
            scanTimeoutSeconds = sharedPreferences.getInt(KEY_SCAN_TIMEOUT, 10),
            networkInterface = sharedPreferences.getString(KEY_NETWORK_INTERFACE, "wlan0") ?: "wlan0",
            isDebugMode = sharedPreferences.getBoolean(KEY_DEBUG_MODE, false),
            isVerboseLogging = sharedPreferences.getBoolean(KEY_VERBOSE_LOGGING, false)
        )
    }

    suspend fun updateScanTimeout(timeout: Int) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putInt(KEY_SCAN_TIMEOUT, timeout).apply()
        _settings.value = _settings.value.copy(scanTimeoutSeconds = timeout)
        Log.d(TAG, "Scan timeout updated to: $timeout")
    }

    suspend fun updateNetworkInterface(interfaceName: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_NETWORK_INTERFACE, interfaceName).apply()
        _settings.value = _settings.value.copy(networkInterface = interfaceName)
        Log.d(TAG, "Network interface updated to: $interfaceName")
    }

    suspend fun updateDebugMode(enabled: Boolean) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
        _settings.value = _settings.value.copy(isDebugMode = enabled)
        
        // Also update LogUtils configuration
        LogUtils.setLogRotationEnabled(context, enabled)
        Log.d(TAG, "Debug mode updated to: $enabled")
    }

    suspend fun updateVerboseLogging(enabled: Boolean) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putBoolean(KEY_VERBOSE_LOGGING, enabled).apply()
        _settings.value = _settings.value.copy(isVerboseLogging = enabled)
        Log.d(TAG, "Verbose logging updated to: $enabled")
    }
}
