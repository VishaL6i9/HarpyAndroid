package com.vishal.harpy.core.utils

import java.time.LocalDateTime

/**
 * Represents a DNS spoofing session
 */
data class DnsSpoofingSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val domain: String,
    val spoofedIP: String,
    val interfaceName: String = "wlan0",
    val isActive: Boolean = false,
    val startTime: LocalDateTime? = null,
    val processId: Long? = null,
    val errorMessage: String? = null
)

/**
 * Represents a DHCP spoofing session
 */
data class DhcpSpoofingSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val interfaceName: String = "wlan0",
    val targetMacs: List<String>,
    val spoofedIPs: List<String>,
    val gatewayIPs: List<String>,
    val subnetMasks: List<String>,
    val dnsServers: List<String>,
    val isActive: Boolean = false,
    val startTime: LocalDateTime? = null,
    val processId: Long? = null,
    val errorMessage: String? = null
)

/**
 * Represents a single DHCP spoofing rule
 */
data class DhcpSpoofingRule(
    val targetMac: String,
    val spoofedIP: String,
    val gatewayIP: String,
    val subnetMask: String = "255.255.255.0",
    val dnsServer: String = "8.8.8.8"
)

/**
 * Unified spoofing session for management
 */
sealed class SpoofingSession {
    abstract val id: String
    abstract val isActive: Boolean
    abstract val startTime: LocalDateTime?
    abstract val errorMessage: String?

    data class Dns(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val domain: String,
        val spoofedIP: String,
        val interfaceName: String = "wlan0",
        override val isActive: Boolean = false,
        override val startTime: LocalDateTime? = null,
        val processId: Long? = null,
        override val errorMessage: String? = null
    ) : SpoofingSession()

    data class Dhcp(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val interfaceName: String = "wlan0",
        val rules: List<DhcpSpoofingRule>,
        override val isActive: Boolean = false,
        override val startTime: LocalDateTime? = null,
        val processId: Long? = null,
        override val errorMessage: String? = null
    ) : SpoofingSession()

    fun getDisplayName(): String = when (this) {
        is Dns -> "DNS: $domain → $spoofedIP"
        is Dhcp -> "DHCP: ${rules.size} rule(s)"
    }

    fun getType(): String = when (this) {
        is Dns -> "DNS"
        is Dhcp -> "DHCP"
    }
}
