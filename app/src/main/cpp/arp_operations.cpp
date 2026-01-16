#include "arp_operations.h"
#include <android/log.h>
#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <sys/socket.h>
#include <net/if.h>
#include <arpa/inet.h>
#include <linux/if_packet.h>
#include <netinet/if_ether.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <chrono>

#define LOG_TAG "ARPOperations"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct arp_packet {
    struct ethhdr eth;
    struct ether_arp arp;
} __attribute__((packed));

bool arp_init() {
    LOGD("Initializing ARP operations (Manual Raw)");
    return true;
}

bool arp_spoof(const char *target_ip, const char *target_mac,
               const char *gateway_ip, const char *our_mac) {
    LOGD("ARP spoof: target=%s, gateway=%s", target_ip, gateway_ip);
    
    // Fallback to arping for now as it's reliable for spoofing if installed
    // Spoofing requires repeated sends which is handled well by arping's timing logic
    char cmd[512];
    snprintf(cmd, sizeof(cmd),
        "arping -U -c 1 -s %s %s && arping -U -c 1 -s %s %s",
        gateway_ip, target_ip, target_ip, gateway_ip);
    
    int result = system(cmd);
    return result == 0;
}

std::string arp_get_mac(const char *ip, const char *interface) {
    LOGD("Robust MAC lookup (Manual Raw) for %s on %s", ip, interface);
    
    int sock = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ARP));
    if (sock < 0) {
        LOGE("Failed to create MAC lookup raw socket: %s (errno=%d). Are permissions correct?", strerror(errno), errno);
        return "";
    }

    // Set timeout for recvfrom
    struct timeval tv;
    tv.tv_sec = 1;
    tv.tv_usec = 0;
    setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, (const char*)&tv, sizeof tv);

    // Get our info
    struct ifreq ifr;
    strncpy(ifr.ifr_name, interface, IFNAMSIZ - 1);
    if (ioctl(sock, SIOCGIFHWADDR, &ifr) < 0) {
        close(sock);
        return "";
    }
    unsigned char our_mac[ETH_ALEN];
    memcpy(our_mac, ifr.ifr_hwaddr.sa_data, ETH_ALEN);
    
    if (ioctl(sock, SIOCGIFADDR, &ifr) < 0) {
        close(sock);
        return "";
    }
    struct sockaddr_in *sin = (struct sockaddr_in *)&ifr.ifr_addr;
    char our_ip[16];
    strcpy(our_ip, inet_ntoa(sin->sin_addr));

    // Build ARP request
    struct arp_packet pkt;
    memset(&pkt, 0, sizeof(pkt));
    memset(pkt.eth.h_dest, 0xff, ETH_ALEN);
    memcpy(pkt.eth.h_source, our_mac, ETH_ALEN);
    pkt.eth.h_proto = htons(ETH_P_ARP);
    
    pkt.arp.ea_hdr.ar_hrd = htons(ARPHRD_ETHER);
    pkt.arp.ea_hdr.ar_pro = htons(ETH_P_IP);
    pkt.arp.ea_hdr.ar_hln = ETH_ALEN;
    pkt.arp.ea_hdr.ar_pln = 4;
    pkt.arp.ea_hdr.ar_op = htons(ARPOP_REQUEST);
    memcpy(pkt.arp.arp_sha, our_mac, ETH_ALEN);
    inet_aton(our_ip, (struct in_addr *)pkt.arp.arp_spa);
    inet_aton(ip, (struct in_addr *)pkt.arp.arp_tpa);

    struct sockaddr_ll dest_addr;
    memset(&dest_addr, 0, sizeof(dest_addr));
    dest_addr.sll_family = AF_PACKET;
    dest_addr.sll_ifindex = if_nametoindex(interface);
    dest_addr.sll_halen = ETH_ALEN;
    memset(dest_addr.sll_addr, 0xff, ETH_ALEN);

    // Send request
    if (sendto(sock, &pkt, sizeof(pkt), 0, (struct sockaddr *)&dest_addr, sizeof(dest_addr)) < 0) {
        LOGE("Failed to send ARP request: %s", strerror(errno));
        close(sock);
        return "";
    }

    // Wait for reply
    unsigned char buffer[1500];
    auto start = std::chrono::steady_clock::now();
    while (true) {
        ssize_t n = recvfrom(sock, buffer, sizeof(buffer), 0, NULL, NULL);
        if (n < 0) break; // Timeout or error

        if (n < (ssize_t)sizeof(struct arp_packet)) continue;
        struct arp_packet *reply = (struct arp_packet *)buffer;

        if (ntohs(reply->eth.h_proto) == ETH_P_ARP && 
            ntohs(reply->arp.ea_hdr.ar_op) == ARPOP_REPLY) {
            
            char reply_ip[16];
            inet_ntop(AF_INET, reply->arp.arp_spa, reply_ip, 16);
            
            if (strcmp(reply_ip, ip) == 0) {
                char mac[18];
                snprintf(mac, sizeof(mac), "%02x:%02x:%02x:%02x:%02x:%02x",
                         reply->arp.arp_sha[0], reply->arp.arp_sha[1], reply->arp.arp_sha[2],
                         reply->arp.arp_sha[3], reply->arp.arp_sha[4], reply->arp.arp_sha[5]);
                close(sock);
                return std::string(mac);
            }
        }

        auto now = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - start).count() > 1000) break;
    }

    close(sock);
    return "";
}

static bool parse_mac(const char *mac_str, unsigned char *mac_bin) {
    if (sscanf(mac_str, "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx",
               &mac_bin[0], &mac_bin[1], &mac_bin[2],
               &mac_bin[3], &mac_bin[4], &mac_bin[5]) == 6) {
        return true;
    }
    return false;
}

bool arp_send_packet(const char *interface,
                     const char *src_ip, const char *src_mac,
                     const char *tgt_ip, const char *tgt_mac,
                     bool is_request) {
    LOGD("Sending manual raw ARP packet on %s: %s -> %s (request=%d)", 
         interface, src_ip, tgt_ip, is_request);
    
    int sock = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ARP));
    if (sock < 0) return false;

    // Get interface info
    struct ifreq ifr;
    strncpy(ifr.ifr_name, interface, IFNAMSIZ - 1);
    if (ioctl(sock, SIOCGIFINDEX, &ifr) < 0) {
        close(sock);
        return false;
    }
    int ifindex = ifr.ifr_ifindex;

    unsigned char src_mac_bin[ETH_ALEN];
    unsigned char tgt_mac_bin[ETH_ALEN];
    if (!parse_mac(src_mac, src_mac_bin)) { close(sock); return false; }
    if (!parse_mac(tgt_mac, tgt_mac_bin)) { 
        if (is_request) memset(tgt_mac_bin, 0, ETH_ALEN); // Broadcast request
        else { close(sock); return false; }
    }

    struct arp_packet pkt;
    memset(&pkt, 0, sizeof(pkt));
    
    // Ethernet Header
    if (is_request && strcmp(tgt_mac, "ff:ff:ff:ff:ff:ff") == 0) {
        memset(pkt.eth.h_dest, 0xff, ETH_ALEN);
    } else {
        memcpy(pkt.eth.h_dest, tgt_mac_bin, ETH_ALEN);
    }
    memcpy(pkt.eth.h_source, src_mac_bin, ETH_ALEN);
    pkt.eth.h_proto = htons(ETH_P_ARP);

    // ARP Header
    pkt.arp.ea_hdr.ar_hrd = htons(ARPHRD_ETHER);
    pkt.arp.ea_hdr.ar_pro = htons(ETH_P_IP);
    pkt.arp.ea_hdr.ar_hln = ETH_ALEN;
    pkt.arp.ea_hdr.ar_pln = 4;
    pkt.arp.ea_hdr.ar_op = htons(is_request ? ARPOP_REQUEST : ARPOP_REPLY);

    memcpy(pkt.arp.arp_sha, src_mac_bin, ETH_ALEN);
    inet_aton(src_ip, (struct in_addr *)pkt.arp.arp_spa);
    memcpy(pkt.arp.arp_tha, tgt_mac_bin, ETH_ALEN);
    inet_aton(tgt_ip, (struct in_addr *)pkt.arp.arp_tpa);

    struct sockaddr_ll dest_addr;
    memset(&dest_addr, 0, sizeof(dest_addr));
    dest_addr.sll_family = AF_PACKET;
    dest_addr.sll_ifindex = ifindex;
    dest_addr.sll_halen = ETH_ALEN;
    memcpy(dest_addr.sll_addr, pkt.eth.h_dest, ETH_ALEN);

    ssize_t sent = sendto(sock, &pkt, sizeof(pkt), 0, (struct sockaddr *)&dest_addr, sizeof(dest_addr));
    close(sock);
    
    return sent > 0;
}

void arp_cleanup() {
    LOGD("Cleaning up ARP operations");
}
