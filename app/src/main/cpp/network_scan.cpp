#include "network_scan.h"
#include <android/log.h>
#include <iostream>
#include <cstring>
#include <vector>
#include <chrono>
#include <thread>
#include <mutex>
#include <set>
#include <map>
#include <sys/socket.h>
#include <netinet/in.h>
#include <linux/if_packet.h>
#include <net/if.h>
#include <netinet/if_ether.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>

#define LOG_TAG "NetworkScan"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// ARP packet structure for sending and receiving
struct arp_packet {
    struct ethhdr eth;
    struct ether_arp arp;
} __attribute__((packed));

static std::mutex g_devices_mutex;
static std::vector<std::string> g_discovered_devices;
static std::map<std::string, int> g_ip_response_count; // Track response reliability
static bool g_stop_capture = false;

bool network_scan_init() {
    LOGD("Initializing network scan operations with manual raw sockets");
    return true;
}

// Thread function to capture ARP replies with improved filtering
void capture_responses(int sock, const char* interface) {
    LOGD("Started ARP capture thread on %s", interface);
    
    unsigned char buffer[1500];
    struct sockaddr_ll sll;
    socklen_t sll_len = sizeof(sll);
    
    // Use poll for better timeout handling
    struct pollfd pfd;
    pfd.fd = sock;
    pfd.events = POLLIN;

    while (!g_stop_capture) {
        int ret = poll(&pfd, 1, 100); // 100ms timeout
        if (ret < 0) {
            if (errno == EINTR) continue;
            LOGE("poll error: %s", strerror(errno));
            break;
        }
        if (ret == 0) continue; // Timeout, check stop flag
        
        ssize_t n = recvfrom(sock, buffer, sizeof(buffer), 0, (struct sockaddr*)&sll, &sll_len);
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) continue;
            LOGE("recvfrom error: %s", strerror(errno));
            break;
        }

        if (n < (ssize_t)sizeof(struct arp_packet)) continue;

        struct arp_packet *pkt = (struct arp_packet *)buffer;
        
        // Validate Ethernet frame
        if (ntohs(pkt->eth.h_proto) != ETH_P_ARP) continue;
        
        // Check if it's an ARP REPLY
        if (ntohs(pkt->arp.ea_hdr.ar_op) != ARPOP_REPLY) continue;
        
        // Validate ARP header fields
        if (ntohs(pkt->arp.ea_hdr.ar_hrd) != ARPHRD_ETHER) continue;
        if (ntohs(pkt->arp.ea_hdr.ar_pro) != ETH_P_IP) continue;
        if (pkt->arp.ea_hdr.ar_hln != ETH_ALEN) continue;
        if (pkt->arp.ea_hdr.ar_pln != 4) continue;
        
        char ip[INET_ADDRSTRLEN];
        inet_ntop(AF_INET, pkt->arp.arp_spa, ip, INET_ADDRSTRLEN);
        
        // Validate IP address (not 0.0.0.0 or broadcast)
        uint32_t ip_val;
        memcpy(&ip_val, pkt->arp.arp_spa, 4);
        if (ip_val == 0 || ip_val == 0xFFFFFFFF) continue;
        
        char mac[18];
        snprintf(mac, sizeof(mac), "%02x:%02x:%02x:%02x:%02x:%02x",
                 pkt->arp.arp_sha[0], pkt->arp.arp_sha[1], pkt->arp.arp_sha[2],
                 pkt->arp.arp_sha[3], pkt->arp.arp_sha[4], pkt->arp.arp_sha[5]);
        
        // Validate MAC address (not all zeros or broadcast)
        bool valid_mac = false;
        for (int i = 0; i < ETH_ALEN; i++) {
            if (pkt->arp.arp_sha[i] != 0x00 && pkt->arp.arp_sha[i] != 0xFF) {
                valid_mac = true;
                break;
            }
        }
        if (!valid_mac) continue;
        
        std::lock_guard<std::mutex> lock(g_devices_mutex);
        std::string ip_str(ip);
        
        // Track response count for reliability
        g_ip_response_count[ip_str]++;
        
        // Only add device if we haven't seen it yet
        bool already_added = false;
        for (const auto& dev : g_discovered_devices) {
            if (dev.find(ip_str) == 0) {
                already_added = true;
                break;
            }
        }
        
        if (!already_added) {
            char result[64];
            snprintf(result, sizeof(result), "%s|%s", ip, mac);
            g_discovered_devices.push_back(std::string(result));
            LOGI("Found device: %s (%s)", ip, mac);
        }
    }
    LOGD("ARP capture thread stopped");
}

static bool get_interface_info(const char *interface, unsigned char *mac, char *ip) {
    struct ifreq ifr;
    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) return false;

    strncpy(ifr.ifr_name, interface, IFNAMSIZ - 1);
    if (ioctl(sock, SIOCGIFHWADDR, &ifr) < 0) {
        close(sock);
        return false;
    }
    memcpy(mac, ifr.ifr_hwaddr.sa_data, ETH_ALEN);

    if (ioctl(sock, SIOCGIFADDR, &ifr) < 0) {
        close(sock);
        return false;
    }
    struct sockaddr_in *addr = (struct sockaddr_in *)&ifr.ifr_addr;
    strcpy(ip, inet_ntoa(addr->sin_addr));

    close(sock);
    return true;
}

std::vector<std::string> network_scan(const char *interface,
                                      const char *subnet,
                                      int timeout_seconds) {
    LOGI("Starting network scan: interface=%s, subnet=%s, timeout=%ds", 
         interface, subnet, timeout_seconds);
    
    {
        std::lock_guard<std::mutex> lock(g_devices_mutex);
        g_discovered_devices.clear();
        g_ip_response_count.clear();
        g_stop_capture = false;
    }

    // Validate timeout
    if (timeout_seconds < 2) timeout_seconds = 2;
    if (timeout_seconds > 60) timeout_seconds = 60;

    // Open raw socket for both sending and receiving
    int sock = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ARP));
    if (sock < 0) {
        LOGE("Failed to create raw socket: %s (errno=%d). Root/CAP_NET_RAW required.", 
             strerror(errno), errno);
        return {};
    }

    // Set socket buffer sizes for better performance
    int bufsize = 262144; // 256KB
    setsockopt(sock, SOL_SOCKET, SO_RCVBUF, &bufsize, sizeof(bufsize));
    setsockopt(sock, SOL_SOCKET, SO_SNDBUF, &bufsize, sizeof(bufsize));

    // Set non-blocking for poll-based receiving
    int flags = fcntl(sock, F_GETFL, 0);
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);

    // Bind to interface
    struct sockaddr_ll sll;
    memset(&sll, 0, sizeof(sll));
    sll.sll_family = AF_PACKET;
    sll.sll_ifindex = if_nametoindex(interface);
    sll.sll_protocol = htons(ETH_P_ARP);
    
    if (sll.sll_ifindex == 0) {
        LOGE("Interface %s not found", interface);
        close(sock);
        return {};
    }
    
    if (bind(sock, (struct sockaddr*)&sll, sizeof(sll)) < 0) {
        LOGE("Failed to bind raw socket: %s", strerror(errno));
        close(sock);
        return {};
    }
    LOGD("Raw socket bound to %s (ifindex=%d)", interface, sll.sll_ifindex);

    // Start capture thread
    std::thread capture_thread(capture_responses, sock, interface);

    // Get our interface info
    unsigned char our_mac[ETH_ALEN];
    char our_ip[16];
    if (!get_interface_info(interface, our_mac, our_ip)) {
        LOGE("Failed to get interface info");
        g_stop_capture = true;
        capture_thread.join();
        close(sock);
        return {};
    }
    LOGD("Interface info: IP=%s, MAC=%02x:%02x:%02x:%02x:%02x:%02x",
         our_ip, our_mac[0], our_mac[1], our_mac[2], 
         our_mac[3], our_mac[4], our_mac[5]);

    // Parse subnet prefix
    char subnet_prefix[16];
    strncpy(subnet_prefix, subnet, sizeof(subnet_prefix)-1);
    subnet_prefix[sizeof(subnet_prefix)-1] = '\0';
    
    char *last_dot = strrchr(subnet_prefix, '.');
    if (last_dot && strcmp(last_dot, ".0") == 0) {
        *last_dot = '\0';
    }

    LOGI("Scanning subnet: %s.1-254", subnet_prefix);
    
    // Build ARP request template
    struct arp_packet sweep_pkt;
    memset(&sweep_pkt, 0, sizeof(sweep_pkt));
    memset(sweep_pkt.eth.h_dest, 0xff, ETH_ALEN);
    memcpy(sweep_pkt.eth.h_source, our_mac, ETH_ALEN);
    sweep_pkt.eth.h_proto = htons(ETH_P_ARP);
    
    sweep_pkt.arp.ea_hdr.ar_hrd = htons(ARPHRD_ETHER);
    sweep_pkt.arp.ea_hdr.ar_pro = htons(ETH_P_IP);
    sweep_pkt.arp.ea_hdr.ar_hln = ETH_ALEN;
    sweep_pkt.arp.ea_hdr.ar_pln = 4;
    sweep_pkt.arp.ea_hdr.ar_op = htons(ARPOP_REQUEST);
    memcpy(sweep_pkt.arp.arp_sha, our_mac, ETH_ALEN);
    inet_aton(our_ip, (struct in_addr *)sweep_pkt.arp.arp_spa);
    memset(sweep_pkt.arp.arp_tha, 0, ETH_ALEN);

    struct sockaddr_ll dest_addr;
    memset(&dest_addr, 0, sizeof(dest_addr));
    dest_addr.sll_family = AF_PACKET;
    dest_addr.sll_ifindex = sll.sll_ifindex;
    dest_addr.sll_halen = ETH_ALEN;
    memset(dest_addr.sll_addr, 0xff, ETH_ALEN);

    // Adaptive sweep function with error handling
    auto run_sweep = [&](int pass) -> int {
        LOGD("Sweep pass %d starting", pass);
        int sent_count = 0;
        int error_count = 0;
        
        for (int i = 1; i < 255; i++) {
            char target_ip[16];
            snprintf(target_ip, sizeof(target_ip), "%s.%d", subnet_prefix, i);
            inet_aton(target_ip, (struct in_addr *)sweep_pkt.arp.arp_tpa);
            
            ssize_t sent = sendto(sock, &sweep_pkt, sizeof(sweep_pkt), 0, 
                                 (struct sockaddr *)&dest_addr, sizeof(dest_addr));
            if (sent < 0) {
                error_count++;
                if (error_count > 10) {
                    LOGE("Too many send errors, aborting sweep");
                    return sent_count;
                }
                usleep(5000); // Back off on errors
            } else {
                sent_count++;
            }
            
            // Adaptive pacing based on pass
            if (pass == 1) {
                // First pass: faster sweep
                if (i % 50 == 0) usleep(10000); // 10ms every 50 packets
                else usleep(500); // 0.5ms between packets
            } else {
                // Second pass: slower, more reliable
                if (i % 32 == 0) usleep(20000); // 20ms every 32 packets
                else usleep(1500); // 1.5ms between packets
            }
        }
        
        LOGD("Sweep pass %d complete: sent %d packets, %d errors", 
             pass, sent_count, error_count);
        return sent_count;
    };

    // Execute multi-pass scan
    int total_timeout = timeout_seconds;
    int pass1_wait = total_timeout / 3;
    int pass2_wait = total_timeout / 3;
    int final_wait = total_timeout - pass1_wait - pass2_wait;
    
    if (pass1_wait < 1) pass1_wait = 1;
    if (pass2_wait < 1) pass2_wait = 1;
    if (final_wait < 1) final_wait = 1;

    // Pass 1: Fast sweep
    run_sweep(1);
    LOGD("Waiting %ds after pass 1", pass1_wait);
    sleep(pass1_wait);

    // Pass 2: Thorough sweep
    run_sweep(2);
    LOGD("Waiting %ds after pass 2", pass2_wait);
    sleep(pass2_wait);

    // Optional Pass 3 for longer timeouts: Target non-responders
    if (timeout_seconds >= 10) {
        LOGD("Running targeted pass 3 for non-responders");
        run_sweep(3);
        LOGD("Final wait %ds", final_wait);
        sleep(final_wait);
    } else {
        sleep(final_wait);
    }

    // Stop capture and cleanup
    g_stop_capture = true;
    if (capture_thread.joinable()) capture_thread.join();
    close(sock);

    std::vector<std::string> results;
    {
        std::lock_guard<std::mutex> lock(g_devices_mutex);
        results = g_discovered_devices;
        
        // Log response statistics
        LOGI("Scan complete: %zu devices found", results.size());
        for (const auto& entry : g_ip_response_count) {
            LOGD("  %s: %d responses", entry.first.c_str(), entry.second);
        }
    }

    return results;
}

void network_scan_cleanup() {
    g_stop_capture = true;
}
