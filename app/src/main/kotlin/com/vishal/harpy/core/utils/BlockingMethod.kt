package com.vishal.harpy.core.utils

enum class BlockingMethod {
    ARP_SPOOF,           // ARP spoofing (works on Android)
    BLACKHOLE_ROUTE,     // Blackhole route (drop all packets)
    TRAFFIC_CONTROL      // TC rate limit (0 = block, >0 = rate limit)
}
