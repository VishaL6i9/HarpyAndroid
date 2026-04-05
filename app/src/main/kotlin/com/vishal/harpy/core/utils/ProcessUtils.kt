package com.vishal.harpy.core.utils

import android.os.Build
import java.util.concurrent.TimeUnit

/**
 * Utility class to provide backward-compatible Process methods for API < 26.
 */
object ProcessUtils {

    /**
     * Backward-compatible isAlive() check for API < 26.
     */
    fun isAlive(process: Process): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.isAlive
        } else {
            try {
                process.exitValue()
                false
            } catch (e: IllegalThreadStateException) {
                true
            }
        }
    }

    /**
     * Backward-compatible waitFor(timeout) for API < 26.
     */
    fun waitFor(process: Process, timeout: Long, unit: TimeUnit): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return process.waitFor(timeout, unit)
        }
        
        val startTime = System.currentTimeMillis()
        val timeoutMillis = unit.toMillis(timeout)
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (!isAlive(process)) {
                return true
            }
            try {
                Thread.sleep(100L) // Poll every 100ms
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return !isAlive(process)
    }

    /**
     * Backward-compatible destroyForcibly() for API < 26.
     */
    fun destroyForcibly(process: Process): Process {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.destroyForcibly()
        } else {
            process.destroy()
            process
        }
    }
}
