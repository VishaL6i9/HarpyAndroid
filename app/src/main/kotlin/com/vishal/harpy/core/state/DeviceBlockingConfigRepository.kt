package com.vishal.harpy.core.state

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceBlockingConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DeviceBlockingConfig"
        private const val PREFS_NAME = "device_blocking_config"
        private const val KEY_RATE_LIMIT_PREFIX = "rate_limit_"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get rate limit for device (0 = use global setting)
     */
    fun getDeviceRateLimit(macAddress: String): Int {
        return sharedPreferences.getInt(KEY_RATE_LIMIT_PREFIX + macAddress, 0)
    }

    /**
     * Set rate limit for device
     */
    suspend fun setDeviceRateLimit(macAddress: String, rateKbps: Int) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putInt(KEY_RATE_LIMIT_PREFIX + macAddress, rateKbps).apply()
        Log.d(TAG, "Set rate limit for $macAddress to $rateKbps kbit/s")
    }

    /**
     * Clear rate limit for device (revert to global)
     */
    suspend fun clearDeviceRateLimit(macAddress: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().remove(KEY_RATE_LIMIT_PREFIX + macAddress).apply()
        Log.d(TAG, "Cleared rate limit for $macAddress")
    }

    /**
     * Get effective rate limit (device-specific or global fallback)
     */
    fun getEffectiveRateLimit(macAddress: String, globalRateKbps: Int): Int {
        val deviceRate = getDeviceRateLimit(macAddress)
        return if (deviceRate > 0) deviceRate else globalRateKbps
    }
}
