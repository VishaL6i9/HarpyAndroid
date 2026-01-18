#include "dns_handler.h"
#include <cstring>
#include <iostream>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <cerrno>

// DNS header structure
struct dns_header {
    uint16_t id;          // identification number
    uint16_t flags;       // flags
    uint16_t q_count;     // number of question entries
    uint16_t ans_count;   // number of answer entries
    uint16_t auth_count;  // number of authority entries
    uint16_t add_count;   // number of resource entries
};

// Function to decode DNS name from packet
std::string decode_dns_name(unsigned char *buffer, int *position, unsigned int max_len) {
    std::string name = "";
    unsigned int len = 0, offset = 0;
    int jumped = 0;
    int original_position = *position;

    while(true) {
        if(*position >= (int)max_len) {
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
                if((*position + (int)i) >= (int)max_len) {
                    return name;
                }
                name += buffer[*position + i];
            }

            name += '.';
            *position += len;
        }

        if(jumped) {
            *position = original_position;
            break;
        }
    }

    if(name.length() > 0 && name.back() == '.') {
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
            int len = (int)(end - start);
            buffer[(*position)++] = len;
            for(int i = 0; i < len; i++) {
                buffer[(*position)++] = start[i];
            }
            start = end + 1;
        }
        end++;
    }

    if(end > start) {
        int len = (int)(end - start);
        buffer[(*position)++] = len;
        for(int i = 0; i < len; i++) {
            buffer[(*position)++] = start[i];
        }
    }

    buffer[(*position)++] = 0; // End of name
}

// Function to craft a DNS response packet
int craft_dns_response(char *query_packet, ssize_t query_size, char *response_packet,
                      const std::string& spoofed_ip) {
    struct dns_header *query_header = (struct dns_header*)query_packet;
    struct dns_header *response_header = (struct dns_header*)response_packet;

    // Copy the header and modify flags to indicate response
    memcpy(response_header, query_header, sizeof(struct dns_header));
    response_header->flags |= htons(0x8000); // Set response flag
    response_header->ans_count = htons(1);    // One answer

    // Copy the query section
    int pos = sizeof(struct dns_header);
    int response_pos = sizeof(struct dns_header);

    // Decode the query name
    std::string query_domain = decode_dns_name((unsigned char*)query_packet, &pos, query_size);

    // Copy the query name to response
    encode_dns_name(query_domain.c_str(), (unsigned char*)response_packet, &response_pos);

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
    inet_pton(AF_INET, spoofed_ip.c_str(), &addr);
    memcpy(response_packet + response_pos, &addr, 4);
    response_pos += 4;

    return response_pos;
}

bool handle_dns_query_with_spoof(
    char* query_buffer,
    ssize_t query_size,
    struct sockaddr_in* client_addr,
    socklen_t client_len,
    int sockfd,
    const DNSSpoofRule& rule
) {
    struct dns_header *header = (struct dns_header*)query_buffer;

    // Check if it's a query (not a response)
    if(ntohs(header->flags) & 0x8000) {
        return false; // This is a response, not a query
    }

    // Check if it has questions
    if(ntohs(header->q_count) == 0) {
        return false; // No questions in this query
    }

    // Extract the domain name from the query
    int pos = sizeof(struct dns_header);
    std::string domain = decode_dns_name((unsigned char*)query_buffer, &pos, query_size);

    // Check if this domain matches our spoofing rule
    if(domain == rule.domain) {
        std::cout << "DNS_SPOOF_MATCH: Query for '" << domain << "' matches rule, sending spoofed response" << std::endl;

        // Craft a response packet with the spoofed IP
        char response_packet[512];
        int response_size = craft_dns_response(query_buffer, query_size, response_packet, rule.spoofed_ip);

        // Send the spoofed response back to the client
        ssize_t sent_bytes = sendto(sockfd, response_packet, response_size, 0,
                                   (struct sockaddr*)client_addr, client_len);

        if(sent_bytes > 0) {
            std::cout << "DNS_SPOOF_RESPONSE_SENT: Sent " << sent_bytes << " bytes to "
                      << inet_ntoa(client_addr->sin_addr) << std::endl;
            return true;
        } else {
            std::cerr << "ERROR: Failed to send DNS response: " << strerror(errno) << std::endl;
            return false;
        }
    }

    return false;
}