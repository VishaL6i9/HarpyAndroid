#ifndef DNS_HANDLER_H
#define DNS_HANDLER_H

#include <string>

/**
 * Structure to represent a DNS spoofing rule
 */
struct DNSSpoofRule {
    std::string domain;  // Domain to spoof (e.g., "example.com")
    std::string spoofed_ip;  // IP address to return instead (e.g., "8.8.8.8")
};

/**
 * Handle incoming DNS query and send spoofed response if it matches our rules
 * For non-matching queries, forward to upstream DNS server
 * @param query_buffer Buffer containing the DNS query
 * @param query_size Size of the DNS query
 * @param client_addr Address of the client that sent the query
 * @param client_len Length of client address structure
 * @param sockfd Socket file descriptor to send response
 * @param rule The DNS spoofing rule to apply
 * @param upstream_dns Upstream DNS server IP (e.g., "8.8.8.8")
 * @return true if a response was sent (spoofed or forwarded), false otherwise
 */
bool handle_dns_query_with_spoof(
    char* query_buffer, 
    ssize_t query_size, 
    struct sockaddr_in* client_addr, 
    socklen_t client_len, 
    int sockfd, 
    const DNSSpoofRule& rule,
    const std::string& upstream_dns = "8.8.8.8"
);

#endif // DNS_HANDLER_H