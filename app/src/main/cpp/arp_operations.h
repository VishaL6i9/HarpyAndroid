#ifndef ARP_OPERATIONS_H
#define ARP_OPERATIONS_H

#include <string>

/**
 * Initialize ARP operations
 */
bool arp_init();

/**
 * Perform ARP spoofing
 */
bool arp_spoof(const char *target_ip, const char *target_mac,
               const char *gateway_ip, const char *our_mac);

/**
 * Get MAC address for IP using ARP
 */
std::string arp_get_mac(const char *ip, const char *interface);

/**
 * Send raw ARP packet
 */
bool arp_send_packet(const char *interface,
                     const char *src_ip, const char *src_mac,
                     const char *tgt_ip, const char *tgt_mac,
                     bool is_request);

/**
 * Cleanup ARP operations
 */
void arp_cleanup();

#endif // ARP_OPERATIONS_H
