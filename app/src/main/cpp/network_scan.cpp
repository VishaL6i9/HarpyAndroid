#include "network_scan.h"
#include <android/log.h>
#include <cstring>
#include <vector>
#include <chrono>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/if_ether.h>
#include <netinet/ether.h>
#include <arpa/inet.h>
#include <net/if.h>
#include <ifaddrs.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdlib.h>

#define LOG_TAG "NetworkScan"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ARP packet structure
struct arp_packet {
    struct ether_header eth;
    struct ether_arp arp;
};

bool network_scan_init() {
    LOGD("Initializing network scan operations with native sockets");
    return true;
}

// Send ARP request to trigger responses
static bool send_arp_request(const char *interface, const char *target_ip, 
                            const unsigned char *our_mac, const char *our_ip) {
    int sock = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ARP));
    if (sock < 0) {
        LOGE("Failed to create ARP socket: %s", strerror(errno));
        return false;
    }

    struct ifreq ifr;
    strncpy(ifr.ifr_name, interface, IFNAMSIZ - 1);
    
    if (setsockopt(sock, SOL_SOCKET, SO_BINDTODEVICE, &ifr, sizeof(ifr)) < 0) {
        LOGE("Failed to bind to interface: %s", strerror(errno));
        close(sock);
        return false;
    }

    struct arp_packet pkt;
    memset(&pkt, 0, sizeof(pkt));

    // Ethernet header
    memset(pkt.eth.ether_dhost, 0xff, ETH_ALEN);  // Broadcast
    memcpy(pkt.eth.ether_shost, our_mac, ETH_ALEN);
    pkt.eth.ether_type = htons(ETHERTYPE_ARP);

    // ARP header
    pkt.arp.ea_hdr.ar_hrd = htons(ARPHRD_ETHER);
    pkt.arp.ea_hdr.ar_pro = htons(ETHERTYPE_IP);
    pkt.arp.ea_hdr.ar_hln = ETH_ALEN;
    pkt.arp.ea_hdr.ar_pln = 4;
    pkt.arp.ea_hdr.ar_op = htons(ARPOP_REQUEST);

    memcpy(pkt.arp.arp_sha, our_mac, ETH_ALEN);
    inet_aton(our_ip, (struct in_addr *)pkt.arp.arp_spa);
    memset(pkt.arp.arp_tha, 0, ETH_ALEN);
    inet_aton(target_ip, (struct in_addr *)pkt.arp.arp_tpa);

    struct sockaddr_ll addr;
    memset(&addr, 0, sizeof(addr));
    addr.sll_family = AF_PACKET;
    addr.sll_ifindex = if_nametoindex(interface);
    addr.sll_halen = ETH_ALEN;
    memset(addr.sll_addr, 0xff, ETH_ALEN);

    if (sendto(sock, &pkt, sizeof(pkt), 0, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        LOGE("Failed to send ARP request: %s", strerror(errno));
        close(sock);
        return false;
    }

    close(sock);
    return true;
}

// Read ARP table using /proc/net/arp
static std::vector<std::string> read_arp_table() {
    std::vector<std::string> devices;
    FILE *fp = fopen("/proc/net/arp", "r");
    
    if (!fp) {
        LOGE("Failed to open /proc/net/arp: %s", strerror(errno));
        return devices;
    }

    char line[256];
    char ip[16], mac[18], device[16];
    int hw_type, flags;

    // Skip header
    fgets(line, sizeof(line), fp);

    while (fgets(line, sizeof(line), fp)) {
        if (sscanf(line, "%15s %x %x %17s %*s %15s", ip, &hw_type, &flags, mac, device) == 5) {
            // Only accept valid IPv4 addresses and MAC addresses
            if (strchr(ip, '.') && strchr(mac, ':')) {
                devices.push_back(std::string(ip));
                LOGD("Found device in ARP table: %s (%s)", ip, mac);
            }
        }
    }

    fclose(fp);
    return devices;
}

// Get our MAC address for the interface
static bool get_interface_mac(const char *interface, unsigned char *mac) {
    struct ifreq ifr;
    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    
    if (sock < 0) {
        LOGE("Failed to create socket: %s", strerror(errno));
        return false;
    }

    strncpy(ifr.ifr_name, interface, IFNAMSIZ - 1);
    
    if (ioctl(sock, SIOCGIFHWADDR, &ifr) < 0) {
        LOGE("Failed to get MAC address: %s", strerror(errno));
        close(sock);
        return false;
    }

    memcpy(mac, ifr.ifr_hwaddr.sa_data, ETH_ALEN);
    close(sock);
    return true;
}

// Get our IP address for the interface
static bool get_interface_ip(const char *interface, char *ip) {
    struct ifaddrs *ifaddr, *ifa;
    bool found = false;

    if (getifaddrs(&ifaddr) == -1) {
        LOGE("Failed to get interface addresses: %s", strerror(errno));
        return false;
    }

    for (ifa = ifaddr; ifa != NULL; ifa = ifa->ifa_next) {
        if (ifa->ifa_addr == NULL) continue;

        if (strcmp(ifa->ifa_name, interface) == 0 && 
            ifa->ifa_addr->sa_family == AF_INET) {
            
            struct sockaddr_in *addr = (struct sockaddr_in *)ifa->ifa_addr;
            strcpy(ip, inet_ntoa(addr->sin_addr));
            found = true;
            break;
        }
    }

    freeifaddrs(ifaddr);
    return found;
}

std::vector<std::string> network_scan(const char *interface,
                                      const char *subnet,
                                      int timeout_seconds) {
    LOGD("Network scan: interface=%s, subnet=%s, timeout=%d", 
         interface, subnet, timeout_seconds);
    
    std::vector<std::string> devices;
    
    // Get our MAC and IP
    unsigned char our_mac[ETH_ALEN];
    char our_ip[16];
    
    if (!get_interface_mac(interface, our_mac)) {
        LOGE("Failed to get interface MAC");
        return devices;
    }
    
    if (!get_interface_ip(interface, our_ip)) {
        LOGE("Failed to get interface IP");
        return devices;
    }

    LOGD("Interface: %s, MAC: %02x:%02x:%02x:%02x:%02x:%02x, IP: %s",
         interface, our_mac[0], our_mac[1], our_mac[2], 
         our_mac[3], our_mac[4], our_mac[5], our_ip);

    // Parse subnet to get base IP
    char subnet_base[16];
    strncpy(subnet_base, subnet, sizeof(subnet_base) - 1);
    char *slash = strchr(subnet_base, '/');
    if (slash) *slash = '\0';
    
    // Extract first 3 octets
    char *last_dot = strrchr(subnet_base, '.');
    if (last_dot) *last_dot = '\0';

    LOGD("Sending ARP requests to subnet: %s.0/24", subnet_base);

    auto start_time = std::chrono::steady_clock::now();

    // Send ARP requests to all IPs in the subnet
    for (int i = 1; i < 255; i++) {
        auto elapsed = std::chrono::steady_clock::now() - start_time;
        if (std::chrono::duration_cast<std::chrono::seconds>(elapsed).count() > timeout_seconds) {
            LOGD("Timeout reached during ARP sweep");
            break;
        }

        char target_ip[16];
        snprintf(target_ip, sizeof(target_ip), "%s.%d", subnet_base, i);
        
        send_arp_request(interface, target_ip, our_mac, our_ip);
    }

    // Wait a bit for ARP responses to populate the table
    sleep(1);

    // Read ARP table
    devices = read_arp_table();
    
    LOGD("Network scan complete. Found %zu devices", devices.size());
    return devices;
}

void network_scan_cleanup() {
    LOGD("Cleaning up network scan operations");
}
