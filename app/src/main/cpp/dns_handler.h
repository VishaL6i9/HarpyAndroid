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
 * @param query_buffer Buffer containing the DNS query
 * @param query_size Size of the DNS query
 * @param client_addr Address of the client that sent the query
 * @param client_len Length of client address structure
 * @param sockfd Socket file descriptor to send response
 * @param rule The DNS spoofing rule to apply
 * @return true if a spoofed response was sent, false otherwise
 */
bool handle_dns_query_with_spoof(
    char* query_buffer, 
    ssize_t query_size, 
    struct sockaddr_in* client_addr, 
    socklen_t client_len, 
    int sockfd, 
    const DNSSpoofRule& rule
);

#endif // DNS_HANDLER_H