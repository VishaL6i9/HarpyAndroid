package com.vishal.harpy.core.native

/**
 * JNI interface for native network operations using raw sockets
 * Provides low-level ARP manipulation and packet capture capabilities
 */
object NativeNetworkOps {
    
    init {
        try {
            System.loadLibrary("harpy_native")
        } catch (e: UnsatisfiedLinkError) {
            // Native library not available, will fall back to shell commands
        }
    }

    /**
     * Initialize native network operations
     * Must be called before using other native functions
     */
    external fun initializeNativeOps(): Boolean

    /**
     * Scan network using native raw sockets for ARP discovery
     * @param interfaceName Network interface name (e.g., "wlan0")
     * @param subnet Subnet to scan (e.g., "192.168.29.0/24")
     * @param timeoutSeconds Timeout in seconds
     * @return Array of discovered device IPs
     */
    external fun scanNetworkNative(
        interfaceName: String,
        subnet: String,
        timeoutSeconds: Int
    ): Array<String>

    /**
     * Perform ARP spoofing to block a device
     * @param targetIP IP address of the device to block
     * @param targetMAC MAC address of the device to block
     * @param gatewayIP IP address of the gateway
     * @param ourMAC Our device's MAC address
     * @return true if successful, false otherwise
     */
    external fun performARPSpoof(
        targetIP: String,
        targetMAC: String,
        gatewayIP: String,
        ourMAC: String
    ): Boolean

    /**
     * Get MAC address for a given IP using ARP
     * @param ip IP address to resolve
     * @param interfaceName Network interface name
     * @return MAC address or null if not found
     */
    external fun getMACForIP(ip: String, interfaceName: String): String?

    /**
     * Send raw ARP packet
     * @param interfaceName Network interface name
     * @param sourceIP Source IP address
     * @param sourceMAC Source MAC address
     * @param targetIP Target IP address
     * @param targetMAC Target MAC address
     * @param isRequest true for ARP request, false for reply
     * @return true if successful
     */
    external fun sendARPPacket(
        interfaceName: String,
        sourceIP: String,
        sourceMAC: String,
        targetIP: String,
        targetMAC: String,
        isRequest: Boolean
    ): Boolean

    /**
     * Cleanup native resources
     */
    external fun cleanupNativeOps(): Boolean
}
