package com.vishal.harpy.core.native

import android.util.Log

/**
 * Wrapper around native network operations with fallback to shell commands
 * Provides a unified interface for network operations
 */
class NativeNetworkWrapper {
    
    companion object {
        private const val TAG = "NativeNetworkWrapper"
        private var isNativeAvailable = false
        
        init {
            try {
                isNativeAvailable = NativeNetworkOps.initializeNativeOps()
                Log.d(TAG, "Native library initialized: $isNativeAvailable")
            } catch (e: Exception) {
                Log.d(TAG, "Native library not available, will use shell fallback: ${e.message}")
                isNativeAvailable = false
            }
        }
    }

    /**
     * Perform ARP spoofing with native implementation or fallback
     */
    fun performARPSpoof(
        targetIP: String,
        targetMAC: String,
        gatewayIP: String,
        ourMAC: String
    ): Boolean {
        return if (isNativeAvailable) {
            try {
                Log.d(TAG, "Using native ARP spoof for $targetIP")
                NativeNetworkOps.performARPSpoof(targetIP, targetMAC, gatewayIP, ourMAC)
            } catch (e: Exception) {
                Log.e(TAG, "Native ARP spoof failed: ${e.message}, falling back to shell")
                false
            }
        } else {
            Log.d(TAG, "Native not available, ARP spoof requires shell commands")
            false
        }
    }

    /**
     * Scan network using native implementation or fallback
     */
    fun scanNetworkNative(
        interfaceName: String,
        subnet: String,
        timeoutSeconds: Int = 5
    ): Array<String> {
        return if (isNativeAvailable) {
            try {
                Log.d(TAG, "Using native network scan for $subnet on $interfaceName")
                NativeNetworkOps.scanNetworkNative(interfaceName, subnet, timeoutSeconds)
            } catch (e: Exception) {
                Log.e(TAG, "Native network scan failed: ${e.message}")
                emptyArray()
            }
        } else {
            Log.d(TAG, "Native not available, network scan requires shell commands")
            emptyArray()
        }
    }

    /**
     * Get MAC address for IP using native implementation or fallback
     */
    fun getMACForIP(ip: String, interfaceName: String): String? {
        return if (isNativeAvailable) {
            try {
                Log.d(TAG, "Using native MAC lookup for $ip")
                NativeNetworkOps.getMACForIP(ip, interfaceName)
            } catch (e: Exception) {
                Log.e(TAG, "Native MAC lookup failed: ${e.message}")
                null
            }
        } else {
            Log.d(TAG, "Native not available, MAC lookup requires shell commands")
            null
        }
    }

    /**
     * Send raw ARP packet using native implementation
     */
    fun sendARPPacket(
        interfaceName: String,
        sourceIP: String,
        sourceMAC: String,
        targetIP: String,
        targetMAC: String,
        isRequest: Boolean = true
    ): Boolean {
        return if (isNativeAvailable) {
            try {
                Log.d(TAG, "Using native ARP packet send")
                NativeNetworkOps.sendARPPacket(
                    interfaceName,
                    sourceIP,
                    sourceMAC,
                    targetIP,
                    targetMAC,
                    isRequest
                )
            } catch (e: Exception) {
                Log.e(TAG, "Native ARP packet send failed: ${e.message}")
                false
            }
        } else {
            Log.d(TAG, "Native not available, ARP packet send requires shell commands")
            false
        }
    }

    /**
     * Check if native library is available
     */
    fun isNativeAvailable(): Boolean = isNativeAvailable

    /**
     * Cleanup native resources
     */
    fun cleanup() {
        if (isNativeAvailable) {
            try {
                NativeNetworkOps.cleanupNativeOps()
                Log.d(TAG, "Native resources cleaned up")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up native resources: ${e.message}")
            }
        }
    }
}
