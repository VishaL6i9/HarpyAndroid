#ifndef ICMP_REDIRECT_H
#define ICMP_REDIRECT_H

#include <string>

/**
 * Structure for ICMP redirect attack parameters
 */
struct ICMPRedirectTarget {
    std::string client_mac;      // MAC of target iOS device
    std::string client_ip;       // IP of target iOS device
    std::string router_ip;       // Legitimate gateway IP (we spoof this as source)
    std::string router_mac;      // Legitimate gateway MAC (we spoof this at Layer 2)
    std::string interface_name;  // Network interface to use
    bool redirect_all;           // If true, redirect 0.0.0.0/0 via client's own IP
    std::string target_dst;      // Specific destination to redirect (if not redirect_all)
};

/**
 * Initialize ICMP redirect operations
 */
bool icmp_redirect_init();

/**
 * Start ICMP redirect attack (SECONDARY VOID)
 * Sends ICMP Type 5 Redirect packets claiming the client is its own gateway
 * @param target The attack parameters
 */
bool icmp_redirect_start(const ICMPRedirectTarget& target);

/**
 * Stop ICMP redirect attack
 */
void icmp_redirect_stop();

/**
 * Check if ICMP redirect is active
 */
bool icmp_redirect_is_active();

/**
 * Cleanup
 */
void icmp_redirect_cleanup();

#endif // ICMP_REDIRECT_H
