package com.vishal.harpy.core.utils

enum class BlockingMethod {
    ARP_SPOOF,           // Current: ARP spoofing (works on Android)
    IPTABLES_DROP,       // Drop all packets from device
    IPTABLES_REDIRECT,   // Redirect to null route (127.0.0.1)
    TRAFFIC_CONTROL      // Use tc (traffic control) to rate limit to 0
}
