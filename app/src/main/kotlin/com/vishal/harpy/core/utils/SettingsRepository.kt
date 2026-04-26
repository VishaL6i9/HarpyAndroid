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
        private const val KEY_CUSTOM_DNS = "custom_dns"
        private const val KEY_FALLBACK_DNS = "fallback_dns"
        private const val KEY_DHCP_LEASE_TIME = "dhcp_lease_time"
        private const val KEY_ENABLE_WHITELIST = "enable_whitelist"
        private const val KEY_BLOCKING_METHOD = "blocking_method"
        private const val KEY_TRAFFIC_CONTROL_RATE = "traffic_control_rate_kbps"
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
            isVerboseLogging = sharedPreferences.getBoolean(KEY_VERBOSE_LOGGING, false),
            customDnsServer = sharedPreferences.getString(KEY_CUSTOM_DNS, "8.8.8.8") ?: "8.8.8.8",
            fallbackDnsServer = sharedPreferences.getString(KEY_FALLBACK_DNS, "8.8.4.4") ?: "8.8.4.4",
            dhcpLeaseTimeSeconds = sharedPreferences.getInt(KEY_DHCP_LEASE_TIME, 3600),
            enableWhitelist = sharedPreferences.getBoolean(KEY_ENABLE_WHITELIST, false),
            blockingMethod = try {
                BlockingMethod.valueOf(sharedPreferences.getString(KEY_BLOCKING_METHOD, BlockingMethod.ARP_SPOOF.name) ?: BlockingMethod.ARP_SPOOF.name)
            } catch (e: Exception) {
                BlockingMethod.ARP_SPOOF
            },
            trafficControlRateKbps = sharedPreferences.getInt(KEY_TRAFFIC_CONTROL_RATE, 0)
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

    suspend fun updateDnsSettings(customDns: String, fallbackDns: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putString(KEY_CUSTOM_DNS, customDns)
            .putString(KEY_FALLBACK_DNS, fallbackDns)
            .apply()
        _settings.value = _settings.value.copy(
            customDnsServer = customDns,
            fallbackDnsServer = fallbackDns
        )
        Log.d(TAG, "DNS settings updated - Primary: $customDns, Fallback: $fallbackDns")
    }

    suspend fun updateDhcpLeaseTime(leaseTimeSeconds: Int) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putInt(KEY_DHCP_LEASE_TIME, leaseTimeSeconds).apply()
        _settings.value = _settings.value.copy(dhcpLeaseTimeSeconds = leaseTimeSeconds)
        Log.d(TAG, "DHCP lease time updated to: $leaseTimeSeconds seconds")
    }

    suspend fun updateWhitelistEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putBoolean(KEY_ENABLE_WHITELIST, enabled).apply()
        _settings.value = _settings.value.copy(enableWhitelist = enabled)
        Log.d(TAG, "Whitelist mode updated to: $enabled")
    }

    suspend fun updateBlockingMethod(method: BlockingMethod) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_BLOCKING_METHOD, method.name).apply()
        _settings.value = _settings.value.copy(blockingMethod = method)
        Log.d(TAG, "Blocking method updated to: ${method.name}")
    }

    suspend fun updateTrafficControlRate(rateKbps: Int) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putInt(KEY_TRAFFIC_CONTROL_RATE, rateKbps).apply()
        _settings.value = _settings.value.copy(trafficControlRateKbps = rateKbps)
        Log.d(TAG, "Traffic control rate updated to: ${rateKbps} kbit/s")
    }

    fun getTrafficControlRate(): Int {
        return sharedPreferences.getInt(KEY_TRAFFIC_CONTROL_RATE, 0)
    }
}
