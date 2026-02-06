package com.vishal.harpy.core.utils

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

/**
 * Utility class for managing app logging configuration and operations
 */
object LogUtils {
    private const val TAG = "LogUtils"

    // Preference keys
    private const val PREF_NAME = "logging_prefs"
    private const val KEY_LOG_ROTATION_ENABLED = "log_rotation_enabled"
    private const val KEY_APP_START_MARKER = "app_start_marker"
    
    // Log rotation settings
    private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024 // 5MB per file
    private var isLogRotationEnabled = true
    private var appStartMarker: String = ""
    
    // Real-time file logging
    private var logFile: File? = null
    private var logWriter: PrintWriter? = null
    private val logQueue = ConcurrentLinkedQueue<String>()
    private var isLoggingActive = false
    private val logLock = Any()

    /**
     * Initialize logging configuration from shared preferences and start file logging
     */
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Invert: stored value is rotation enabled, but we track debug mode (inverse)
        isLogRotationEnabled = !prefs.getBoolean(KEY_LOG_ROTATION_ENABLED, false)

        // Load the app start marker if it exists
        appStartMarker = prefs.getString(KEY_APP_START_MARKER, "") ?: ""
        
        // Start real-time file logging
        startFileLogging(context)
    }

    /**
     * Start real-time file logging in background thread
     */
    private fun startFileLogging(context: Context) {
        synchronized(logLock) {
            if (isLoggingActive) return
            
            try {
                // Create logs directory
                val logDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        File(Environment.getExternalStorageDirectory(), "HarpyAndroid/logs")
                    } else {
                        File(context.getExternalFilesDir(null), "HarpyAndroid/logs")
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        File(Environment.getExternalStorageDirectory(), "HarpyAndroid/logs")
                    } else {
                        File(context.getExternalFilesDir(null), "HarpyAndroid/logs")
                    }
                }

                if (!logDir.exists()) {
                    logDir.mkdirs()
                }

                // Create log file with timestamp
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                logFile = File(logDir, "logs_$timestamp.txt")
                logWriter = PrintWriter(FileWriter(logFile, true), true)
                
                isLoggingActive = true
                Log.d(TAG, "File logging started at ${logFile?.absolutePath}")
                
                // Start background thread to flush logs
                thread(isDaemon = true, name = "LogWriter") {
                    while (isLoggingActive) {
                        try {
                            val log = logQueue.poll()
                            if (log != null) {
                                synchronized(logLock) {
                                    logWriter?.println(log)
                                    logWriter?.flush()
                                }
                            } else {
                                Thread.sleep(100)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error writing log", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start file logging", e)
            }
        }
    }

    /**
     * Clear the log buffer when the app starts
     */
    fun clearLogBufferAtStart() {
        try {
            Runtime.getRuntime().exec("logcat -c")
            Log.i(TAG, "Log buffer cleared at app start")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing log buffer at app start", e)
        }
    }

    /**
     * Enable or disable log file rotation
     * When Debug Mode is ON, rotation is DISABLED (single continuous file)
     * When Debug Mode is OFF, rotation is ENABLED (manage disk space)
     */
    fun setLogRotationEnabled(context: Context, enabled: Boolean) {
        // Invert: if Debug Mode is enabled, disable rotation
        isLogRotationEnabled = !enabled
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOG_ROTATION_ENABLED, !enabled).apply()
    }

    /**
     * Check if log file rotation is enabled
     */
    fun isLogRotationEnabled(): Boolean {
        return isLogRotationEnabled
    }

    /**
     * Log a debug message if verbose logging is enabled
     */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeToFile("D/$tag: $message")
    }

    /**
     * Log an info message if verbose logging is enabled
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeToFile("I/$tag: $message")
    }

    /**
     * Log a warning message (always logged regardless of verbose setting)
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeToFile("W/$tag: $message")
    }

    /**
     * Log an error message (always logged regardless of verbose setting)
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            writeToFile("E/$tag: $message\n${throwable.stackTraceToString()}")
        } else {
            Log.e(tag, message)
            writeToFile("E/$tag: $message")
        }
    }
    
    /**
     * Write log to file queue for async writing
     */
    private fun writeToFile(message: String) {
        if (isLoggingActive) {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logQueue.offer("[$timestamp] $message")
            
            // Check if rotation is needed
            if (isLogRotationEnabled && logFile != null && logFile!!.length() > MAX_LOG_FILE_SIZE) {
                rotateLogFile()
            }
        }
    }
    
    /**
     * Rotate to a new log file when current one exceeds size limit
     */
    private fun rotateLogFile() {
        synchronized(logLock) {
            try {
                // Close current writer
                logWriter?.flush()
                logWriter?.close()
                
                // Get the log directory
                val logDir = logFile?.parentFile ?: return
                
                // Create new log file with timestamp
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                logFile = File(logDir, "logs_$timestamp.txt")
                logWriter = PrintWriter(FileWriter(logFile, true), true)
                
                Log.d(TAG, "Log file rotated to ${logFile?.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error rotating log file", e)
            }
        }
    }

    /**
     * Start capturing logcat output in real-time (for native logs and system logs)
     */
    fun startLogcatCapture(context: Context) {
        thread(isDaemon = true, name = "LogcatCapture") {
            try {
                // Start logcat with our app's tags and native tags
                val process = Runtime.getRuntime().exec(arrayOf(
                    "logcat", "-v", "brief",
                    "HarpyNative:D",
                    "NetworkScan:D", 
                    "ARPOperations:D",
                    "NetworkMonitorRepoImpl:D",
                    "VendorLookup:D",
                    "NativeNetworkWrapper:D",
                    "LogUtils:D",
                    "RootErrorMapper:D",
                    "DevicePreferenceRepository:D",
                    "NetworkMonitorViewModel:D",
                    "SettingsFragment:D",
                    "NetworkMonitorFragment:D",
                    "DeviceActionsBottomSheet:D",
                    "*:W"  // Also capture all warnings and errors
                ))
                
                val reader = process.inputStream.bufferedReader()
                reader.forEachLine { line ->
                    if (isLoggingActive) {
                        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                        logQueue.offer("[$timestamp] [LOGCAT] $line")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing logcat", e)
            }
        }
    }

    /**
     * Dump logs to a file (or return the current log file path)
     * If rotation is enabled, returns the directory containing all log files
     */
    fun dumpLogsToFile(context: Context): String? {
        return try {
            synchronized(logLock) {
                logWriter?.flush()
            }
            
            if (logFile?.exists() == true && logFile?.length() ?: 0 > 0) {
                Log.d(TAG, "Returning log file: ${logFile?.absolutePath}")
                logFile?.absolutePath
            } else {
                Log.w(TAG, "No logs available to export")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dumping logs to file", e)
            null
        }
    }
    
    /**
     * Get the logs directory path
     */
    fun getLogsDirectory(context: Context): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                File(Environment.getExternalStorageDirectory(), "HarpyAndroid/logs")
            } else {
                File(context.getExternalFilesDir(null), "HarpyAndroid/logs")
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                File(Environment.getExternalStorageDirectory(), "HarpyAndroid/logs")
            } else {
                File(context.getExternalFilesDir(null), "HarpyAndroid/logs")
            }
        }
    }

    /**
     * Stop file logging and cleanup resources
     */
    fun stopFileLogging() {
        synchronized(logLock) {
            isLoggingActive = false
            logWriter?.flush()
            logWriter?.close()
            logWriter = null
        }
    }

    /**
     * Get the count of log files in the logs directory
     */
    fun getLogCount(context: Context): Int {
        val logDir = getLogsDirectory(context) ?: return 0
        return if (logDir.exists() && logDir.isDirectory) {
            logDir.listFiles { _, name -> name.startsWith("logs_") && name.endsWith(".txt") }?.size ?: 0
        } else {
            0
        }
    }

    /**
     * Delete all log files in the logs directory
     */
    fun cleanAllLogs(context: Context): Boolean {
        val logDir = getLogsDirectory(context) ?: return false
        if (!logDir.exists() || !logDir.isDirectory) return false
        
        var allDeleted = true
        synchronized(logLock) {
            // Close current writer first
            val wasLoggingActive = isLoggingActive
            stopFileLogging()
            
            logDir.listFiles { _, name -> name.startsWith("logs_") && name.endsWith(".txt") }?.forEach { file ->
                if (!file.delete()) {
                    allDeleted = false
                    Log.e(TAG, "Failed to delete log file: ${file.absolutePath}")
                }
            }
            
            // Restart logging if it was active
            if (wasLoggingActive) {
                startFileLogging(context)
            }
        }
        return allDeleted
    }

    /**
     * Clear the content of the current log file
     */
    fun clearCurrentLog(context: Context): Boolean {
        synchronized(logLock) {
            try {
                // If not active, there's nothing to clear (or we just delete files)
                if (!isLoggingActive || logFile == null) return false
                
                // Close current writer
                logWriter?.flush()
                logWriter?.close()
                
                // Overwrite with empty content
                logWriter = PrintWriter(FileWriter(logFile!!, false), true)
                Log.i(TAG, "Current log file cleared: ${logFile?.absolutePath}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing current log", e)
                return false
            }
        }
    }
}
