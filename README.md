# Harpy Android

A network monitoring and control application for Android, inspired by the iOS jailbreak tweak Harpy. This app allows users to discover devices on their local network and manage network access.

## Features

### Root-Based Functionality (Current Implementation)
- Network device discovery using ARP scanning
- Detailed device information (IP address, MAC address, hostname)
- Ability to block/disconnect specific devices from the network using ARP spoofing
- Device management interface for controlling network access
- Real-time network monitoring

### Non-Root Approach (Future Plan)
- Network monitoring using Android's VpnService
- Traffic analysis for the device's own network usage
- Limited functionality compared to root-based approach
- Compatible with non-rooted devices

## Technical Implementation

The root-based implementation is built using native Kotlin for the Android application layer, with plans for native C/C++ code using libpcap/libnet libraries accessed through JNI for low-level network operations when root access is available. The app leverages root access to execute ARP spoofing techniques similar to the original iOS Harpy tweak.

## Requirements

- Android device with root access
- Android 7.0 (API level 24) or higher recommended
- Android SDK 36 (Android 16 "Baklava")
- AGP 8.13.2
- Gradle 8.13

## Legal Notice

This tool should only be used on networks you own or have explicit permission to manage. Unauthorized network interference may violate local laws.

## Development

The project follows a modular structure:
- `app/` - Main Android application module
- `app/src/main/kotlin/com/vishal/harpy/` - Kotlin source code
- `app/src/main/res/` - Resource files
- `app/src/main/AndroidManifest.xml` - Application manifest

To build the project, ensure you have the Android SDK properly configured with the required API level and build tools.

## Project Roadmap

### Phase 1: Foundation & UI (Completed ✅)
- [x] Project initialization with Android SDK 36 support
- [x] Root detection functionality
- [x] Basic UI with device listing
- [x] Network device discovery placeholder
- [x] Device blocking/unblocking UI elements

### Phase 2: Core Network Functionality (In Progress 🔄)
- [x] Root access validation improvements
- [ ] ARP scanning implementation
- [ ] Device identification algorithms
- [ ] Network topology mapping
- [ ] Real-time device monitoring

### Phase 3: Advanced Features (Planned 📋)
- [ ] ARP spoofing implementation for device blocking
- [ ] Network traffic analysis
- [ ] Blacklist/whitelist management
- [ ] Scheduled blocking functionality
- [ ] Network usage statistics

### Phase 4: Optimization & Polish (Planned 📋)
- [ ] Performance optimizations
- [ ] UI/UX enhancements
- [ ] Error handling improvements
- [ ] Localization support
- [ ] Accessibility features

### Phase 5: Native Integration (Planned 📋)
- [ ] Native C/C++ libraries for low-level network operations
- [ ] JNI integration for ARP manipulation
- [ ] libpcap/libnet integration
- [ ] Enhanced root command execution
- [ ] Performance benchmarking

### Phase 6: Testing & Deployment (Planned 📋)
- [ ] Comprehensive unit testing
- [ ] Integration testing on various devices
- [ ] Security audit
- [ ] Beta testing program
- [ ] Production release preparation

### Phase 7: Non-Root Implementation (Future Plan 📋)
- [ ] VpnService-based network monitoring
- [ ] Limited functionality for non-rooted devices
- [ ] Feature parity assessment
- [ ] Alternative network scanning methods