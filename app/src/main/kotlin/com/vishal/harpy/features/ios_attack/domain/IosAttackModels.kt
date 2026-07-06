package com.vishal.harpy.features.ios_attack.domain

/**
 * Type of iOS void attack
 */
enum class IosAttackType {
    DHCP_SELF_GATEWAY,  // PRIMARY VOID: Router = client's own IP, /32 mask
    DNS_NULLIFY,        // TERTIARY VOID: DNS server = 0.0.0.0
    ICMP_REDIRECT,      // SECONDARY VOID: ICMP Type 5 redirect
    TCP_RST             // FAILSAFE: TCP RST asymmetry
}

/**
 * Configuration for an iOS void attack
 */
data class IosAttackConfig(
    val attackType: IosAttackType,
    val targetMac: String,
    val targetIp: String = "",
    val routerIp: String,
    val routerMac: String,
    val interfaceName: String = "wlan0",
    val redirectAll: Boolean = true,
    val specificDestination: String = ""
)

/**
 * Represents an active iOS attack session
 */
data class IosAttackSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val config: IosAttackConfig,
    val isActive: Boolean = false,
    val startTime: Long? = null
)
