#ifndef DHCP_SPOOFING_H
#define DHCP_SPOOFING_H

#include <string>
#include <vector>

/**
 * Structure to represent a DHCP spoofing rule
 */
struct DHCPSpoofRule {
    std::string target_mac;      // MAC address of target device
    std::string spoofed_ip;      // IP address to assign to target
    std::string gateway_ip;      // Gateway IP to provide
    std::string subnet_mask;     // Subnet mask to provide
    std::string dns_server;      // DNS server to provide
};

/**
 * Initialize DHCP spoofing operations
 */
bool dhcp_spoof_init();

/**
 * Start DHCP spoofing on a specific interface
 * @param interface The network interface to listen on (e.g., "wlan0")
 * @param rules Vector of DHCP spoofing rules to apply
 * @return true if successful, false otherwise
 */
bool dhcp_start_spoofing(const char *interface, const std::vector<DHCPSpoofRule>& rules);

/**
 * Stop DHCP spoofing
 */
void dhcp_stop_spoofing();

/**
 * Add a DHCP spoofing rule
 */
void dhcp_add_rule(const char *target_mac, const char *spoofed_ip, 
                  const char *gateway_ip, const char *subnet_mask, 
                  const char *dns_server);

/**
 * Remove a DHCP spoofing rule
 */
void dhcp_remove_rule(const char *target_mac);

/**
 * Clear all DHCP spoofing rules
 */
void dhcp_clear_rules();

/**
 * Check if DHCP spoofing is currently active
 */
bool dhcp_is_active();

/**
 * Cleanup DHCP spoofing operations
 */
void dhcp_spoof_cleanup();

#endif // DHCP_SPOOFING_H