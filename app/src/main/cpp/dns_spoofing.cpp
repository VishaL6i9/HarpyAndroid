#include "dns_spoofing.h"
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
#include "dns_handler.h"

#define LOG_TAG "DNSSpoofing"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global variables for DNS spoofing
static std::vector<DNSSpoofRule> g_dns_rules;
static std::mutex g_rules_mutex;
static std::atomic<bool> g_dns_spoof_active(false);
static std::thread *g_dns_spoof_thread = nullptr;
static int g_dns_socket = -1;
static std::atomic<bool> g_stop_spoofing(false);

// Main DNS spoofing thread function
void dns_spoof_thread_func(const std::string& interface) {
    LOGD("Starting DNS spoofing on interface: %s", interface.c_str());
    
    // Create a UDP socket to listen on port 53 (DNS)
    g_dns_socket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if(g_dns_socket < 0) {
        LOGE("Failed to create DNS spoofing UDP socket: %s", strerror(errno));
        return;
    }
    
    // Set socket options
    int opt = 1;
    setsockopt(g_dns_socket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    setsockopt(g_dns_socket, SOL_SOCKET, SO_REUSEPORT, &opt, sizeof(opt));
    
    // Configure the socket to bind to DNS port
    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(53);  // DNS port
    server_addr.sin_addr.s_addr = INADDR_ANY; // Listen on all interfaces
    
    // Bind to the DNS port
    if(bind(g_dns_socket, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        LOGE("Failed to bind DNS spoofing socket to port 53: %s", strerror(errno));
        close(g_dns_socket);
        g_dns_socket = -1;
        return;
    }
    
    g_stop_spoofing = false;
    
    // Buffer for incoming packets
    unsigned char packet_buffer[512]; // DNS packets are usually smaller
    
    LOGD("DNS spoofing listening on port 53...");
    
    while(!g_stop_spoofing) {
        struct sockaddr_in client_addr;
        socklen_t addr_len = sizeof(client_addr);
        
        int packet_size = recvfrom(g_dns_socket, packet_buffer, sizeof(packet_buffer), 0,
                                   (struct sockaddr*)&client_addr, &addr_len);
        
        if(packet_size < 0) {
            if(errno == EINTR) continue;
            LOGE("Error receiving DNS packet: %s", strerror(errno));
            break;
        }
        
        // Check if this is a UDP packet containing DNS data (port 53)
        // Apply spoofing rules to the packet
        {
            std::lock_guard<std::mutex> lock(g_rules_mutex);
            for(const auto& rule : g_dns_rules) {
                DNSSpoofRule spoof_rule;
                spoof_rule.domain = rule.domain;
                spoof_rule.spoofed_ip = rule.spoofed_ip;
                
                // Handle the DNS query with spoofing
                bool response_sent = handle_dns_query_with_spoof(
                    (char*)packet_buffer, 
                    packet_size, 
                    &client_addr, 
                    addr_len, 
                    g_dns_socket, 
                    spoof_rule
                );
                
                if(response_sent) {
                    break; // Response sent, no need to check other rules
                }
            }
        }
    }
    
    close(g_dns_socket);
    g_dns_socket = -1;
    LOGD("DNS spoofing thread stopped");
}

bool dns_spoof_init() {
    LOGD("Initializing DNS spoofing operations");
    return true;
}

bool dns_start_spoofing(const char *interface, const std::vector<DNSSpoofRule>& rules) {
    LOGD("Starting DNS spoofing on interface: %s", interface);
    
    if(g_dns_spoof_active.load()) {
        LOGE("DNS spoofing is already active");
        return false;
    }
    
    // Apply the rules
    {
        std::lock_guard<std::mutex> lock(g_rules_mutex);
        g_dns_rules = rules;
    }
    
    // Start the spoofing thread
    try {
        g_dns_spoof_thread = new std::thread(dns_spoof_thread_func, std::string(interface));
        g_dns_spoof_active = true;
        LOGD("DNS spoofing started successfully");
        return true;
    } catch(const std::exception& e) {
        LOGE("Failed to start DNS spoofing thread: %s", e.what());
        return false;
    }
}

void dns_stop_spoofing() {
    LOGD("Stopping DNS spoofing");
    
    if(!g_dns_spoof_active.load()) {
        LOGD("DNS spoofing is not active");
        return;
    }
    
    g_stop_spoofing = true;
    
    if(g_dns_socket >= 0) {
        close(g_dns_socket);
        g_dns_socket = -1;
    }
    
    if(g_dns_spoof_thread && g_dns_spoof_thread->joinable()) {
        g_dns_spoof_thread->join();
        delete g_dns_spoof_thread;
        g_dns_spoof_thread = nullptr;
    }
    
    g_dns_spoof_active = false;
    LOGD("DNS spoofing stopped");
}

void dns_add_rule(const char *domain, const char *spoofed_ip) {
    if(domain && spoofed_ip) {
        std::lock_guard<std::mutex> lock(g_rules_mutex);
        // Check if rule already exists
        for(auto& rule : g_dns_rules) {
            if(rule.domain == domain) {
                rule.spoofed_ip = spoofed_ip;
                LOGD("Updated DNS spoofing rule for %s to %s", domain, spoofed_ip);
                return;
            }
        }
        // Add new rule
        g_dns_rules.push_back({domain, spoofed_ip});
        LOGD("Added DNS spoofing rule: %s -> %s", domain, spoofed_ip);
    }
}

void dns_remove_rule(const char *domain) {
    if(domain) {
        std::lock_guard<std::mutex> lock(g_rules_mutex);
        g_dns_rules.erase(
            std::remove_if(g_dns_rules.begin(), g_dns_rules.end(),
                          [domain](const DNSSpoofRule& rule) {
                              return rule.domain == domain;
                          }),
            g_dns_rules.end()
        );
        LOGD("Removed DNS spoofing rule for %s", domain);
    }
}

void dns_clear_rules() {
    std::lock_guard<std::mutex> lock(g_rules_mutex);
    g_dns_rules.clear();
    LOGD("Cleared all DNS spoofing rules");
}

bool dns_is_active() {
    return g_dns_spoof_active.load();
}

void dns_spoof_cleanup() {
    LOGD("Cleaning up DNS spoofing operations");
    dns_stop_spoofing();
    dns_clear_rules();
}