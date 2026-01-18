#ifndef DNS_SPOOFING_H
#define DNS_SPOOFING_H

#include <string>
#include <vector>

/**
 * Structure to represent a DNS spoofing rule
 */
struct DNSSpoofRule {
    std::string domain;  // Domain to spoof (e.g., "example.com")
    std::string spoofed_ip;  // IP address to return instead (e.g., "192.168.1.100")
};

/**
 * Initialize DNS spoofing operations
 */
bool dns_spoof_init();

/**
 * Start DNS spoofing on a specific interface
 * @param interface The network interface to listen on (e.g., "wlan0")
 * @param rules Vector of DNS spoofing rules to apply
 * @return true if successful, false otherwise
 */
bool dns_start_spoofing(const char *interface, const std::vector<DNSSpoofRule>& rules);

/**
 * Stop DNS spoofing
 */
void dns_stop_spoofing();

/**
 * Add a DNS spoofing rule
 */
void dns_add_rule(const char *domain, const char *spoofed_ip);

/**
 * Remove a DNS spoofing rule
 */
void dns_remove_rule(const char *domain);

/**
 * Clear all DNS spoofing rules
 */
void dns_clear_rules();

/**
 * Check if DNS spoofing is currently active
 */
bool dns_is_active();

/**
 * Cleanup DNS spoofing operations
 */
void dns_spoof_cleanup();

#endif // DNS_SPOOFING_H