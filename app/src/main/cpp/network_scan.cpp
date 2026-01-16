#include "network_scan.h"
#include <android/log.h>
#include <iostream>
#include <cstring>
#include <vector>
#include <chrono>
#include <thread>
#include <mutex>
#include <set>
#include <sys/socket.h>
#include <netinet/in.h>
#include <linux/if_packet.h>
#include <net/if.h>
#include <netinet/if_ether.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <errno.h>

#define LOG_TAG "NetworkScan"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ARP packet structure for sending and receiving
struct arp_packet {
    struct ethhdr eth;
    struct ether_arp arp;
} __attribute__((packed));

static std::mutex g_devices_mutex;
static std::vector<std::string> g_discovered_devices;
static std::set<std::string> g_seen_ips;
static bool g_stop_capture = false;

bool network_scan_init() {
    LOGD("Initializing network scan operations with manual raw sockets");
    return true;
}

// Thread function to capture ARP replies
void capture_responses(int sock, const char* interface) {
    std::cout << "DEBUG: Started ARP capture thread on " << interface << std::endl;
    LOGD("Started ARP capture thread on %s", interface);
    
    unsigned char buffer[1500];
    struct sockaddr_ll sll;
    socklen_t sll_len = sizeof(sll);

    while (!g_stop_capture) {
        ssize_t n = recvfrom(sock, buffer, sizeof(buffer), 0, (struct sockaddr*)&sll, &sll_len);
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                usleep(10000);
                continue;
            }
            LOGE("recvfrom error: %s", strerror(errno));
            break;
        }

        if (n < (ssize_t)sizeof(struct arp_packet)) continue;

        struct arp_packet *pkt = (struct arp_packet *)buffer;
        
        // Check if it's an ARP packet and it's a REPLY
        if (ntohs(pkt->eth.h_proto) == ETH_P_ARP && 
            ntohs(pkt->arp.ea_hdr.ar_op) == ARPOP_REPLY) {
            
            char ip[INET_ADDRSTRLEN];
            inet_ntop(AF_INET, pkt->arp.arp_spa, ip, INET_ADDRSTRLEN);
            
            char mac[18];
            snprintf(mac, sizeof(mac), "%02x:%02x:%02x:%02x:%02x:%02x",
                     pkt->arp.arp_sha[0], pkt->arp.arp_sha[1], pkt->arp.arp_sha[2],
                     pkt->arp.arp_sha[3], pkt->arp.arp_sha[4], pkt->arp.arp_sha[5]);
            
            std::lock_guard<std::mutex> lock(g_devices_mutex);
            if (g_seen_ips.find(ip) == g_seen_ips.end()) {
                char result[64];
                snprintf(result, sizeof(result), "%s|%s", ip, mac);
                g_discovered_devices.push_back(std::string(result));
                g_seen_ips.insert(std::string(ip));
                LOGD("Manual raw socket found device: %s (%s)", ip, mac);
                std::cout << "INFO: Found " << ip << " with " << mac << std::endl;
            }
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
    LOGD("Starting robust network scan (Manual Raw): interface=%s, subnet=%s", interface, subnet);
    
    {
        std::lock_guard<std::mutex> lock(g_devices_mutex);
        g_discovered_devices.clear();
        g_seen_ips.clear();
        g_stop_capture = false;
    }

    // Open raw socket for both sending and receiving
    int sock = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ARP));
    if (sock < 0) {
        LOGE("Failed to create scan raw socket: %s (errno=%d). Raw sockets require root/CAP_NET_RAW.", strerror(errno), errno);
        return {};
    }

    // Set non-blocking for the receiver thread to exit gracefully
    int flags = fcntl(sock, F_GETFL, 0);
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);

    // Bind to interface
    struct sockaddr_ll sll;
    memset(&sll, 0, sizeof(sll));
    sll.sll_family = AF_PACKET;
    sll.sll_ifindex = if_nametoindex(interface);
    sll.sll_protocol = htons(ETH_P_ARP);
    if (bind(sock, (struct sockaddr*)&sll, sizeof(sll)) < 0) {
        std::cout << "DEBUG: Failed to bind raw socket: " << strerror(errno) << std::endl;
        LOGE("Failed to bind raw socket: %s", strerror(errno));
        close(sock);
        return {};
    }
    std::cout << "DEBUG: Raw socket bound to " << interface << " (ifindex=" << sll.sll_ifindex << ")" << std::endl;

    // Start capture thread
    std::thread capture_thread(capture_responses, sock, interface);

    // Get our info for sweep
    unsigned char our_mac[ETH_ALEN];
    char our_ip[16];
    if (!get_interface_info(interface, our_mac, our_ip)) {
        LOGE("Failed to get interface info");
        g_stop_capture = true;
        capture_thread.join();
        close(sock);
        return {};
    }

    // Use subnet as provided if it already looks like a prefix (e.g. "192.168.29")
    // or strip .0 if it's "192.168.29.0"
    char subnet_prefix[16];
    strncpy(subnet_prefix, subnet, sizeof(subnet_prefix)-1);
    subnet_prefix[sizeof(subnet_prefix)-1] = '\0';
    
    char *last_dot = strrchr(subnet_prefix, '.');
    if (last_dot && strcmp(last_dot, ".0") == 0) {
        *last_dot = '\0';
    }

    std::cout << "DEBUG: Starting 2-pass sweep on subnet: " << subnet_prefix << std::endl;
    LOGD("Starting 2-pass sweep on %s.1-254", subnet_prefix);
    
    // Structure for sending
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

    auto run_sweep = [&](int pass) {
        std::cout << "DEBUG: Sweep Pass " << pass << " starting..." << std::endl;
        for (int i = 1; i < 255; i++) {
            char target_ip[16];
            snprintf(target_ip, sizeof(target_ip), "%s.%d", subnet_prefix, i);
            inet_aton(target_ip, (struct in_addr *)sweep_pkt.arp.arp_tpa);
            
            sendto(sock, &sweep_pkt, sizeof(sweep_pkt), 0, (struct sockaddr *)&dest_addr, sizeof(dest_addr));
            
            // Pacing: Wait 1ms between packets to avoid buffer overflows
            // and 20ms every 32 packets to let the network breathe
            if (i % 32 == 0) usleep(20000);
            else usleep(1000); 
        }
    };

    // Pass 1
    run_sweep(1);
    
    // Wait for half the total timeout after pass 1
    int wait_time = timeout_seconds / 2;
    if (wait_time < 2) wait_time = 2; // Min wait 2s
    
    std::cout << "DEBUG: Pass 1 finished, waiting " << wait_time << "s..." << std::endl;
    sleep(wait_time);

    // Pass 2
    run_sweep(2);

    // Wait for the remaining timeout
    std::cout << "DEBUG: Pass 2 finished, final wait " << wait_time << "s..." << std::endl;
    sleep(wait_time);

    // Stop and cleanup
    g_stop_capture = true;
    if (capture_thread.joinable()) capture_thread.join();
    close(sock);

    std::vector<std::string> results;
    {
        std::lock_guard<std::mutex> lock(g_devices_mutex);
        results = g_discovered_devices;
    }

    LOGD("Scan complete. Found %zu devices", results.size());
    return results;
}

void network_scan_cleanup() {
    g_stop_capture = true;
}
