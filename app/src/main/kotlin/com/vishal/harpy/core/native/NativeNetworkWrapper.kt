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

        fun getRootHelperPath(context: android.content.Context): String? {
            val libDir = context.applicationInfo.nativeLibraryDir
            val binaryName = "libharpy_root_helper.so"
            
            // Try direct path first
            var helperPath = "$libDir/$binaryName"
            var file = java.io.File(helperPath)
            if (file.exists()) return helperPath
            
            // Try common ABI subdirectories just in case
            val abis = listOf("arm64-v8a", "arm64", "base.apk!/lib/arm64-v8a", "base.apk!/lib/arm64")
            for (abi in abis) {
                helperPath = "$libDir/$abi/$binaryName"
                file = java.io.File(helperPath)
                if (file.exists()) return helperPath
            }

            Log.e(TAG, "Root helper not found in $libDir or its subdirectories. Checked: $abis")
            return null
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
     * Initialize DNS spoofing
     */
    fun initializeDNSSpoof(): Boolean {
        Log.d(TAG, "DNS spoofing initialization is handled by root helper")
        return true
    }

    /**
     * Start DNS spoofing
     */
    fun startDNSSpoof(
        interfaceName: String,
        domains: Array<String>,
        spoofedIPs: Array<String>
    ): Boolean {
        Log.d(TAG, "DNS spoofing is handled by root helper, not native library")
        return false
    }

    /**
     * Stop DNS spoofing
     */
    fun stopDNSSpoof() {
        Log.d(TAG, "DNS spoofing stop is handled by root helper")
    }

    /**
     * Add DNS spoofing rule
     */
    fun addDNSSpoofRule(domain: String, spoofedIP: String) {
        Log.d(TAG, "DNS spoofing rules are handled by root helper: $domain -> $spoofedIP")
    }

    /**
     * Remove DNS spoofing rule
     */
    fun removeDNSSpoofRule(domain: String) {
        Log.d(TAG, "DNS spoofing rule removal is handled by root helper: $domain")
    }

    /**
     * Check if DNS spoofing is active
     */
    fun isDNSSpoofActive(): Boolean {
        Log.d(TAG, "DNS spoofing status check is handled by root helper")
        return false
    }

    /**
     * Initialize DHCP spoofing
     */
    fun initializeDHCPSpoof(): Boolean {
        Log.d(TAG, "DHCP spoofing initialization is handled by root helper")
        return true
    }

    /**
     * Start DHCP spoofing
     */
    fun startDHCPSpoof(
        interfaceName: String,
        targetMacs: Array<String>,
        spoofedIPs: Array<String>,
        gatewayIPs: Array<String>,
        subnetMasks: Array<String>,
        dnsServers: Array<String>
    ): Boolean {
        Log.d(TAG, "DHCP spoofing is handled by root helper, not native library")
        return false
    }

    /**
     * Stop DHCP spoofing
     */
    fun stopDHCPSpoof() {
        Log.d(TAG, "DHCP spoofing stop is handled by root helper")
    }

    /**
     * Add DHCP spoofing rule
     */
    fun addDHCPSpoofRule(
        targetMac: String,
        spoofedIP: String,
        gatewayIP: String,
        subnetMask: String,
        dnsServer: String
    ) {
        Log.d(TAG, "DHCP spoofing rules are handled by root helper: $targetMac -> $spoofedIP")
    }

    /**
     * Remove DHCP spoofing rule
     */
    fun removeDHCPSpoofRule(targetMac: String) {
        Log.d(TAG, "DHCP spoofing rule removal is handled by root helper: $targetMac")
    }

    /**
     * Check if DHCP spoofing is active
     */
    fun isDHCPSpoofActive(): Boolean {
        Log.d(TAG, "DHCP spoofing status check is handled by root helper")
        return false
    }

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
