package com.vishal.harpy.features.dns.domain.repository

import com.vishal.harpy.core.utils.NetworkResult

interface DnsRepository {
    suspend fun startDNSSpoofing(domain: String, spoofedIP: String, interfaceName: String): NetworkResult<Boolean>
    suspend fun stopDNSSpoofing(domain: String): NetworkResult<Boolean>
    fun isDNSSpoofingActive(domain: String): Boolean
}
