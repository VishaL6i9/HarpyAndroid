#ifndef NETWORK_SCAN_H
#define NETWORK_SCAN_H

#include <string>
#include <vector>

/**
 * Initialize network scan operations
 */
bool network_scan_init();

/**
 * Scan network for devices
 */
std::vector<std::string> network_scan(const char *interface,
                                      const char *subnet,
                                      int timeout_seconds);

/**
 * Cleanup network scan operations
 */
void network_scan_cleanup();

#endif // NETWORK_SCAN_H
