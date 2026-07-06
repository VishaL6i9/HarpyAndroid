#ifndef TCP_RST_SPOOF_H
#define TCP_RST_SPOOF_H

#include <string>

/**
 * Structure for TCP RST spoofing parameters
 */
struct TCPRSTTarget {
    std::string client_mac;      // MAC of target iOS device
    std::string client_ip;       // IP of target iOS device
    std::string interface_name;  // Network interface to use
};

/**
 * Initialize TCP RST spoofing operations
 */
bool tcp_rst_init();

/**
 * Start TCP RST asymmetry attack (FAILSAFE)
 * Sniffs TCP SYN packets from target, responds with forged RST
 * @param target The attack parameters
 */
bool tcp_rst_start(const TCPRSTTarget& target);

/**
 * Stop TCP RST attack
 */
void tcp_rst_stop();

/**
 * Check if TCP RST is active
 */
bool tcp_rst_is_active();

/**
 * Cleanup
 */
void tcp_rst_cleanup();

#endif // TCP_RST_SPOOF_H
