package com.vishal.harpy.core.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Utility class for handling device vibration across different SDK levels
 */
object VibrationUtils {
    private const val TAG = "VibrationUtils"

    /**
     * Get Vibrator instance compatible with all SDK versions
     */
    @Suppress("DEPRECATION")
    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Vibrate the device for a specified duration
     * Handles SDK 24-36 with appropriate APIs for each version
     *
     * @param context The application context
     * @param durationMs Duration of vibration in milliseconds
     */
    fun vibrate(context: Context, durationMs: Long) {
        try {
            val vibrator = getVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        // Android 8.0+ (API 26+): Use VibrationEffect
                        vibrateWithEffect(vibrator, durationMs)
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                        // Android 7.0+ (API 24-25): Use deprecated vibrate with duration
                        vibrateDeprecated(vibrator, durationMs)
                    }
                    else -> {
                        // Fallback for older versions
                        vibrateDeprecated(vibrator, durationMs)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating device: ${e.message}")
        }
    }

    /**
     * Vibrate using VibrationEffect (Android 8.0+)
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun vibrateWithEffect(vibrator: Vibrator, durationMs: Long) {
        try {
            val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
            Log.d(TAG, "Vibrated with VibrationEffect for ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error with VibrationEffect: ${e.message}")
        }
    }

    /**
     * Vibrate using deprecated method (Android 7.0-7.1)
     */
    @Suppress("DEPRECATION")
    private fun vibrateDeprecated(vibrator: Vibrator, durationMs: Long) {
        try {
            vibrator.vibrate(durationMs)
            Log.d(TAG, "Vibrated (deprecated method) for ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error with deprecated vibrate: ${e.message}")
        }
    }

    /**
     * Vibrate with a pattern (multiple pulses)
     * Useful for different types of feedback
     *
     * @param context The application context
     * @param pattern Array of durations: [delay, vibrate, delay, vibrate, ...]
     */
    fun vibratePattern(context: Context, pattern: LongArray) {
        try {
            val vibrator = getVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
                        fun vibrateWaveform() {
                             val effect = VibrationEffect.createWaveform(pattern, -1)
                             vibrator.vibrate(effect)
                        }
                        vibrateWaveform()
                        Log.d(TAG, "Vibrated with pattern")
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                        Log.d(TAG, "Vibrated with pattern (deprecated method)")
                    }
                    else -> {
                        // Fallback: vibrate for total duration of pattern
                        val totalDuration = pattern.sum()
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(totalDuration)
                        Log.d(TAG, "Vibrated with pattern (fallback method)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating with pattern: ${e.message}")
        }
    }

    /**
     * Check if device has vibrator
     */
    fun hasVibrator(context: Context): Boolean {
        return try {
            getVibrator(context)?.hasVibrator() == true
        } catch (e: Exception) {
            false
        }
    }
}
