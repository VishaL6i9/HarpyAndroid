package com.vishal.harpy.features.ios_attack.domain

import com.vishal.harpy.core.utils.NetworkResult

/**
 * Repository for iOS-specific void attacks
 * Wraps the root helper binary commands for the 4 attack vectors
 */
interface IosAttackRepository {
    /**
     * Start a DHCP self-implosion attack (PRIMARY VOID)
     * Forces iOS device to delete its default gateway via forged unicast DHCPACK
     */
    suspend fun startDhcpSelfGateway(config: IosAttackConfig): NetworkResult<Boolean>

    /**
     * Start DNS nullification attack (TERTIARY VOID)
     * Sets DNS server to 0.0.0.0, breaking name resolution
     */
    suspend fun startDnsNullification(config: IosAttackConfig): NetworkResult<Boolean>

    /**
     * Start ICMP redirect attack (SECONDARY VOID)
     * Poisons routing table via forged ICMP Type 5 redirects
     */
    suspend fun startIcmpRedirect(config: IosAttackConfig): NetworkResult<Boolean>

    /**
     * Start TCP RST asymmetry attack (FAILSAFE)
     * Sniffs SYN packets and sends forged RSTs
     */
    suspend fun startTcpRst(config: IosAttackConfig): NetworkResult<Boolean>

    /**
     * Stop an active iOS attack by type
     */
    suspend fun stopAttack(attackType: IosAttackType): NetworkResult<Boolean>

    /**
     * Check if an attack type is currently active
     */
    fun isAttackActive(attackType: IosAttackType): Boolean

    /**
     * Get all active attack sessions
     */
    fun getActiveSessions(): List<IosAttackType>
}
