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
     * @return Array of discovered device strings in "IP|MAC" format
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

    /**
     * Initialize DNS spoofing operations
     */
    fun initializeDNSSpoof(): Boolean {
        // DNS spoofing is handled by root helper, not native library
        return true
    }

    /**
     * Start DNS spoofing on a specific interface
     * @param interfaceName Network interface name (e.g., "wlan0")
     * @param domains Array of domain names to spoof
     * @param spoofedIPs Array of spoofed IP addresses corresponding to domains
     * @return true if successful, false otherwise
     */
    fun startDNSSpoof(
        interfaceName: String,
        domains: Array<String>,
        spoofedIPs: Array<String>
    ): Boolean {
        // DNS spoofing is handled by root helper, not native library
        return false
    }

    /**
     * Stop DNS spoofing
     */
    fun stopDNSSpoof() {
        // DNS spoofing is handled by root helper, not native library
    }

    /**
     * Add a DNS spoofing rule
     * @param domain Domain to spoof
     * @param spoofedIP IP address to return instead
     */
    fun addDNSSpoofRule(domain: String, spoofedIP: String) {
        // DNS spoofing is handled by root helper, not native library
    }

    /**
     * Remove a DNS spoofing rule
     * @param domain Domain to remove from spoofing rules
     */
    fun removeDNSSpoofRule(domain: String) {
        // DNS spoofing is handled by root helper, not native library
    }

    /**
     * Check if DNS spoofing is currently active
     * @return true if active, false otherwise
     */
    fun isDNSSpoofActive(): Boolean {
        // DNS spoofing is handled by root helper, not native library
        return false
    }

    /**
     * Initialize DHCP spoofing operations
     */
    fun initializeDHCPSpoof(): Boolean {
        // DHCP spoofing is handled by root helper, not native library
        return true
    }

    /**
     * Start DHCP spoofing on a specific interface
     * @param interfaceName Network interface name (e.g., "wlan0")
     * @param targetMacs Array of target MAC addresses to spoof
     * @param spoofedIPs Array of spoofed IP addresses corresponding to targets
     * @param gatewayIPs Array of gateway IPs to provide
     * @param subnetMasks Array of subnet masks to provide
     * @param dnsServers Array of DNS servers to provide
     * @return true if successful, false otherwise
     */
    fun startDHCPSpoof(
        interfaceName: String,
        targetMacs: Array<String>,
        spoofedIPs: Array<String>,
        gatewayIPs: Array<String>,
        subnetMasks: Array<String>,
        dnsServers: Array<String>
    ): Boolean {
        // DHCP spoofing is handled by root helper, not native library
        return false
    }

    /**
     * Stop DHCP spoofing
     */
    fun stopDHCPSpoof() {
        // DHCP spoofing is handled by root helper, not native library
    }

    /**
     * Add a DHCP spoofing rule
     * @param targetMac MAC address to target
     * @param spoofedIP IP address to assign to target
     * @param gatewayIP Gateway IP to provide
     * @param subnetMask Subnet mask to provide
     * @param dnsServer DNS server to provide
     */
    fun addDHCPSpoofRule(
        targetMac: String,
        spoofedIP: String,
        gatewayIP: String,
        subnetMask: String,
        dnsServer: String
    ) {
        // DHCP spoofing is handled by root helper, not native library
    }

    /**
     * Remove a DHCP spoofing rule
     * @param targetMac MAC address to remove from spoofing rules
     */
    fun removeDHCPSpoofRule(targetMac: String) {
        // DHCP spoofing is handled by root helper, not native library
    }

    /**
     * Check if DHCP spoofing is currently active
     * @return true if active, false otherwise
     */
    fun isDHCPSpoofActive(): Boolean {
        // DHCP spoofing is handled by root helper, not native library
        return false
    }
}
