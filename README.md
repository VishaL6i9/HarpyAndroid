# Harpy Android

A network monitoring and control application for Android, inspired by the iOS jailbreak tweak Harpy. This app allows users to discover devices on their local network and manage network access.

## Features

### Multi-Feature Navigation
The app now features a bottom navigation bar providing easy access to three main features:
- **Network Monitor** - Device discovery and management
- **DNS Spoofing** - DNS query interception and redirection
- **DHCP Spoofing** - DHCP request interception and IP assignment

### Root-Based Functionality (Current Implementation)
- Network device discovery using ARP scanning with instant results
- Detailed device information (IP address, MAC address, hostname, vendor)
- Ability to block/disconnect specific devices from the network using ARP spoofing
- Gateway detection and identification with visual indicators
- Nuclear option: Block all devices on the network by spoofing the gateway
- Device management interface for controlling network access
- Real-time network monitoring
- Custom device naming for easy identification with persistent storage
  - Device names saved to SharedPreferences and persist across app restarts
  - Named devices automatically appear at the top of the device listing
- Device pinning to keep important devices at the top
  - Pinned devices appear below named devices in the listing
- Persistent blocked device state tracking
  - Blocked state saved to SharedPreferences and persists across app restarts
  - Automatic verification and restoration of blocks after app restart
  - Stale blocked states cleaned up for devices no longer on network
  - "Unblock All" functionality to remove all device blocks at once
  - Prominent "Unblock All" button appears when blocked devices exist
- Vendor lookup using OUI database for accurate manufacturer identification
- Immersive fullscreen mode with auto-hiding status bar
- OLED-optimized dark theme with true blacks
- Device ping testing to verify connectivity
- Root helper binary for privileged network operations
- Bottom sheet UI for device actions with confirmation dialogs
- Blocking indicator overlay with progress feedback
  - Semi-transparent overlay during device blocking operations
  - Centered progress indicator and status text
  - Smooth fade animations for visibility transitions
- Granular loading state management
  - Distinct states for Scanning, Blocking, Unblocking, MappingTopology, TestingPing, DNSSpoofing, DHCPSpoofing
  - Precise UI feedback for each operation type
- Animated visibility transitions
  - Smooth fade-in/fade-out animations for device list items
  - AccelerateInterpolator for show animations
  - DecelerateInterpolator for hide animations
- DNS spoofing and redirection via root helper
  - Domain-to-IP redirection
  - Dedicated DNS Spoofing feature fragment
  - Root helper command support for DNS interception
- DHCP spoofing and response interception
  - DHCP request interception on port 67
  - Spoofed IP assignment to target devices
  - Custom gateway and DNS server injection
  - Rule-based targeting by MAC address
  - Dedicated DHCP Spoofing feature fragment

### Logging & Debugging
- Real-time file logging with automatic log rotation (5MB per file)
- Debug mode toggle for continuous logging without rotation
- Log export functionality with file sharing support
- Log buffer clearing with 3-second hold gesture and haptic feedback
- Logcat capture for native and system logs
- Centralized logging utility (LogUtils) for consistent app-wide logging
- Logging settings accessible from the settings menu

### Permissions & Security
- Runtime permission management with dedicated permissions activity
- Comprehensive permission checking on app startup
- Support for Android 11+ storage permissions (MANAGE_EXTERNAL_STORAGE)
- Graceful fallback for older Android versions (WRITE_EXTERNAL_STORAGE)
- Visual permission status display with grant/deny indicators
- File provider configuration for secure log file sharing
- SDK-aware DNS property access warnings
  - Android 7-8: DNS access available with standard permissions
  - Android 9: Moderate restrictions on DNS properties
  - Android 10+: Strict restrictions with fallback to alternative detection methods
  - Warning-based notifications that don't block app access
- SDK-aware hostname resolution
  - Android 7-9: Uses nslookup for hostname resolution
  - Android 10+: Uses getent hosts to avoid DNS property access warnings
  - Eliminates "Access denied finding property net.dns*" warnings on modern Android

### Non-Root Approach (Future Plan)
- Network monitoring using Android's VpnService
- Traffic analysis for the device's own network usage
- Limited functionality compared to root-based approach
- Compatible with non-rooted devices

## Technical Implementation

The root-based implementation is built using native Kotlin for the Android application layer, with native C/C++ code using raw socket implementations for low-level network operations when root access is available. The app leverages root access to execute ARP spoofing techniques similar to the original iOS Harpy tweak. Future plans include migrating to libpcap/libnet libraries for enhanced packet capture and crafting capabilities.

### SDK-Aware Network Operations
The app implements different strategies based on Android SDK level to handle platform restrictions:

**Route Detection (All SDK levels):**
- Collects all available network routes
- Prioritizes physical interfaces: WiFi (wlan0) > Ethernet (eth0) > Mobile data (rmnet)
- Avoids VPN tunnel interfaces (tun, tap, ppp) for local network scanning
- Falls back to gateway-derived subnet when route detection fails
- Handles scenarios with Private DNS, VPN, and multiple active connections

**Hostname Resolution:**
- Android 7-9 (API 24-28): Uses `nslookup` command (DNS properties accessible)
- Android 10+ (API 29+): Uses `getent hosts` command to avoid DNS property access warnings
- Eliminates "Access denied finding property net.dns*" warnings on modern Android
- Works seamlessly with Private DNS and encrypted DNS configurations

### Device Preference System
The app includes a comprehensive device preference system for managing custom device settings:
- **Device Naming**: Users can assign custom names to devices for easy identification
  - Names are stored in SharedPreferences with the device's MAC address as the key
  - Names persist across app restarts and device reboots
  - Named devices automatically appear at the top of the device listing
  - Proper error handling and logging for debugging name save failures
- **Device Pinning**: Users can pin important devices to keep them visible
  - Pinned devices appear below named devices in the listing
  - Pin status is also persisted to SharedPreferences
- **Blocked Device State Tracking**: Persistent tracking of blocked devices
  - Blocked state stored in SharedPreferences alongside device preferences
  - Automatic verification on each network scan
  - Restoration of blocks after app restart or kill
  - Cleanup of stale blocked states for devices no longer on network
  - "Unblock All" functionality accessible from main screen and settings
- **Smart Sorting**: Device listing uses a three-tier sort order:
  1. Devices with saved names (always at top)
  2. Pinned devices (without names)
  3. All other devices (sorted by IP address)
- **Preference Storage**: Uses Android's SharedPreferences for reliable persistence
  - Data stored in `device_preferences` SharedPreferences file
  - Each device preference stored as JSON with MAC address as unique identifier
  - Preferences loaded automatically during network scan
  - Suspend functions with proper coroutine handling for thread-safe operations

### DNS Spoofing Implementation
The DNS spoofing feature uses a UDP socket listener on port 53 to intercept DNS queries. The implementation includes:
- **DNS Query Handler** (`dns_handler.cpp`): Parses incoming DNS packets, extracts domain names, and crafts spoofed responses
- **DNS Packet Processing**: Decodes DNS names from wire format, validates query headers, and injects spoofed IP addresses into responses
- **Response Crafting**: Constructs valid DNS response packets with TTL and proper DNS record format for IPv4 addresses
- **Root Helper Integration**: Runs as a background process via the root helper binary to listen on port 53 and intercept all DNS queries on the network

### DHCP Spoofing Implementation
The DHCP spoofing feature intercepts DHCP requests and sends spoofed responses to assign custom IP configurations. The implementation includes:
- **DHCP Packet Handler** (`dhcp_spoofing.cpp`): Parses DHCP requests, validates magic cookies, and crafts spoofed responses
- **DHCP Packet Processing**: Decodes DHCP headers, extracts client MAC addresses, and injects spoofed IP/gateway/DNS configurations
- **Response Crafting**: Constructs valid DHCP response packets with proper header flags and network byte order encoding
- **Rule Management**: Thread-safe rule storage with MAC address-based targeting for selective device spoofing
- **Root Helper Integration**: Listens on port 67 (DHCP server port) to intercept all DHCP requests on the network

### Root Helper Binary
A standalone executable (`libharpy_root_helper.so`) is packaged with the app and can be invoked via `su` to perform privileged network operations without requiring the entire app to run as root. This provides better security isolation and allows for more granular permission control.

The root helper uses optimized raw socket implementations with advanced packet processing:
- **Poll-based packet reception**: Non-blocking I/O with proper timeout handling for better responsiveness
- **Comprehensive packet validation**: Multi-layer validation of Ethernet headers, ARP fields, IP addresses, and MAC addresses
- **Adaptive sweep pacing**: Fast first pass (0.5ms between packets) followed by thorough second pass (1.5ms between packets)
- **Socket buffer optimization**: 256KB buffers for handling burst traffic without packet loss
- **Response reliability tracking**: Monitors response counts per IP for network diagnostics
- **Error handling with backoff**: Automatic retry logic with exponential backoff on send failures
- **Optional third pass**: For longer scans (10+ seconds) to catch slow-responding devices

Supported commands:
- `scan <interface> <subnet> [timeout]` - Network device discovery (default 10s timeout)
- `mac <interface> <ip>` - MAC address resolution
- `block <interface> <target_ip> <gateway_ip> <our_mac>` - Bidirectional ARP spoofing for device blocking
- `block_all <interface> <gateway_ip> <our_mac>` - Nuclear option: Block all devices via gateway spoofing
- `unblock <interface> <target_ip> <target_mac> <gateway_ip> <gateway_mac>` - ARP cache restoration to unblock device
- `dns_spoof <interface> <domain> <spoofed_ip>` - DNS spoofing for domain redirection

## Requirements

- Android device with root access
- Android 7.0 (API level 24) or higher recommended
- Android SDK 36 (Android 16 "Baklava")
- AGP 8.13.2
- Gradle 8.13

## Legal Notice

This tool should only be used on networks you own or have explicit permission to manage. Unauthorized network interference may violate local laws.

## Development

The project follows a modern Android architecture with feature modules:

### Core Layer
- `core/network/` - Network utilities and protocols
- `core/ui/` - Common UI components and composables
- `core/utils/` - Shared utility classes and data models

### Feature Modules
- `features/network_monitor/` - Network monitoring feature with:
  - `data/` - Repository implementations and data sources
  - `domain/` - Business logic (UseCases)
  - `presentation/` - UI logic (ViewModels, Fragments/Activities)
  - `di/` - Dependency injection setup
- `features/dns/` - DNS spoofing feature with:
  - `presentation/ui/` - DNSSpoofingFragment for DNS rule management
- `features/dhcp/` - DHCP spoofing feature with:
  - `presentation/ui/` - DHCPSpoofingFragment for DHCP rule management
- `features/device_manager/` - Device management feature with the same layered architecture

### Architecture Principles
- **MVVM Pattern**: Clean separation of View, ViewModel, and Model
- **Clean Architecture**: Each layer has a specific responsibility
- **Feature-First Organization**: Related functionality is grouped together
- **Dependency Injection**: Proper decoupling of components with Hilt
- **State Management**: Using Kotlin Flows for reactive programming
- **Bottom Navigation**: Multi-feature navigation with dedicated fragments for each feature
- **Shared ViewModel**: Activity-scoped ViewModel shared across all fragments for persistent state
  - Network scan results persist across tab switches
  - Blocked device state maintained during navigation
  - Consistent state management across all features

### Navigation Structure
The app uses Android's BottomNavigationView to provide seamless navigation between features:
- Each feature is implemented as a separate Fragment
- Navigation state is managed by MainActivity
- Fragment switching is handled through the bottom navigation menu
- Each feature can maintain its own state and UI independently
- Activity-scoped ViewModel ensures data persistence across tab switches
  - Network scan results remain available when switching between tabs
  - Device blocking state persists during navigation
  - All fragments share the same ViewModel instance for consistent state

To build the project, ensure you have the Android SDK properly configured with the required API level and build tools.

## Project Roadmap

### Phase 1: Foundation & UI (Completed)
- [x] Project initialization with Android SDK 36 support
- [x] Root detection functionality
- [x] Basic UI with device listing
- [x] Network device discovery implementation
- [x] Device blocking/unblocking UI elements

### Phase 2: Core Network Functionality (In Progress)
- [x] Root access validation improvements
- [x] ARP scanning implementation
- [x] Device identification algorithms (enhanced with hardware type detection)
- [x] Network topology mapping
- [x] Real-time device monitoring
  - [x] Background service for continuous network scanning
  - [ ] WebSocket or similar connection for live updates
  - [x] Efficient polling mechanism to minimize battery drain
  - [ ] Notification system for new device detection
  - [ ] Live status updates for device connectivity
- [x] Device ping testing functionality
  - [x] TestPingUseCase for domain layer operations
  - [x] UI integration for ping test results
  - [x] State management for ping operations
- [x] Bidirectional ARP spoofing for device blocking
  - [x] Gateway MAC resolution for effective spoofing
  - [x] Unblock command with ARP cache restoration
  - [x] Aggressive spoofing interval (500ms)
  - [x] Error handling and debug logging
  - [x] Nuclear option for blocking all devices via gateway spoofing
  - [x] Broadcast ARP spoofing packets (300ms interval)
  - [x] Gateway detection and identification
- [x] Persistent blocked device state management
  - [x] Blocked state persistence in SharedPreferences
  - [x] Automatic verification and restoration on network scan
  - [x] Cleanup of stale blocked states
  - [x] "Unblock All" functionality with confirmation dialogs
  - [x] Prominent UI button for unblocking all devices
  - [x] Settings menu integration for unblock all

### Phase 3: Advanced Features (Completed)
- [x] ARP spoofing implementation for device blocking
- [x] Network traffic analysis
- [x] Blacklist/whitelist management
- [x] Scheduled blocking functionality
- [x] Network usage statistics

### Phase 4: Optimization & Polish (Completed)
- [x] Performance optimizations
  - [x] Network scan timeout handling (5 second limits)
  - [x] Removed sequential ping loop (was O(n) now O(1))
  - [x] Direct ARP table reading for faster discovery
  - [x] Process cleanup with destroyForcibly()
  - [x] Improved shell command execution
  - [x] Better error handling and recovery
  - [x] Reduced memory allocations in loops
  - [x] Efficient string parsing with regex caching
  - [x] 2-pass ARP sweep with improved pacing (1ms between packets, 20ms between batches)
  - [x] Adaptive sweep pacing with fast first pass (0.5ms) and thorough second pass (1.5ms)
  - [x] Poll-based packet reception with proper timeout handling
  - [x] Comprehensive ARP packet validation (Ethernet, IP, MAC address filtering)
  - [x] Socket buffer optimization (256KB) for burst traffic handling
  - [x] Response reliability tracking with per-IP response counts
  - [x] Error handling with send error tracking and automatic backoff
  - [x] Optional third pass for longer scans to catch slow responders
  - [x] Configurable timeout for root helper scan (default 10 seconds)
  - [x] Timeout handling with proper process termination
- [x] UI/UX enhancements
  - [x] Header section with app title and status
  - [x] Device count display
  - [x] Empty state messaging
  - [x] CardView-based device items with elevation
  - [x] Blocked status badge
  - [x] Improved color scheme and typography
  - [x] Better button states and visibility
  - [x] Enhanced loading indicators
  - [x] OLED dark theme with true blacks
  - [x] Immersive fullscreen mode with auto-hiding status bar
  - [x] Custom device naming for easy identification
  - [x] Device pinning to keep important devices at the top
  - [x] Device name persistence across app restarts
  - [x] Smart device listing sort order (named devices first, then pinned, then by IP)
  - [x] Long-press context menu for device actions
  - [x] Debug menu for clearing custom names
  - [x] Red highlighting for blocked devices
  - [x] Blue highlighting for current device
  - [x] Green highlighting for gateway device
  - [x] Immediate UI updates after block/unblock operations
  - [x] Bottom sheet UI for device actions
  - [x] Two-stage confirmation dialogs for nuclear option
  - [x] Bottom navigation bar for multi-feature navigation
  - [x] Dedicated DNS Spoofing feature fragment
  - [x] Dedicated DHCP Spoofing feature fragment
  - [x] Material Design icons
  - [x] Seamless fragment switching between features
  - [x] Blocking indicator overlay with progress feedback
  - [x] Granular loading state management (Scanning, Blocking, Unblocking, etc.)
  - [x] Animated visibility transitions with interpolators
  - [x] Rounded card background drawable for UI components
  - [x] Activity-scoped ViewModel for persistent state across tab switches
  - [x] Scan results persist when navigating between features
- [x] Error handling improvements
  - [x] NetworkError sealed class hierarchy
  - [x] RootError sealed class hierarchy for root-specific errors
  - [x] NetworkResult wrapper for safe operations
  - [x] RootResult wrapper for root operations
  - [x] Comprehensive error mapping utilities (NetworkErrorMapper, RootErrorMapper)
  - [x] User-friendly error messages
  - [x] Functional error handling with onSuccess/onError extensions
  - [x] Stack trace presentation for all errors
  - [x] Detailed error reporting with full diagnostics
  - [x] Error logging with complete stack traces
- [x] Vendor identification
  - [x] OUI database integration for accurate manufacturer lookup
  - [x] Multi-octet matching (5, 4, 3 octets) for flexible vendor detection
  - [x] Local database lookup (no network calls)
  - [x] Result caching for performance
  - [x] Fallback to hardcoded common vendors
- [ ] Localization support
- [ ] Accessibility features

### Phase 4.5: Logging & Permissions (Completed)
- [x] Centralized logging utility (LogUtils)
  - [x] Real-time file logging with timestamp
  - [x] Automatic log rotation when files exceed 5MB
  - [x] Debug mode toggle to disable rotation for continuous logging
  - [x] Logcat capture for native and system logs
  - [x] Async log writing with queue-based system
  - [x] Thread-safe logging operations
- [x] Permissions management system
  - [x] PermissionChecker utility for runtime permission validation
  - [x] Comprehensive permission list (network, storage, vibration)
  - [x] Android 11+ storage permission handling (MANAGE_EXTERNAL_STORAGE)
  - [x] Fallback for older Android versions (WRITE_EXTERNAL_STORAGE)
  - [x] PermissionsActivity for requesting permissions on app startup
  - [x] Visual permission status display with grant/deny indicators
  - [x] File provider configuration for secure log sharing
  - [x] SDK-aware DNS property access warnings (Android 7-8, 9, 10+)
  - [x] Warning-based notifications that don't block app access
  - [x] SDK-aware hostname resolution to eliminate DNS property warnings
- [x] Logging UI integration
  - [x] Logging settings menu item in settings fragment
  - [x] Logging settings dialog with debug mode toggle
  - [x] Log export functionality with file sharing
  - [x] Log deletion with confirmation dialog
  - [x] 3-second hold gesture to clear log buffer
  - [x] Haptic feedback (vibration) on successful actions
  - [x] Toast notifications for user feedback
- [x] Vibration utilities
  - [x] Cross-SDK vibration support (API 24+)
  - [x] VibrationEffect for Android 8.0+
  - [x] Deprecated method fallback for older versions
  - [x] Pattern-based vibration support
  - [x] Device vibrator capability checking

### Phase 5: Native Integration (In Progress)
- [x] Native C/C++ libraries for low-level network operations
  - [x] JNI interface for native operations (NativeNetworkOps.kt)
  - [x] Native wrapper with fallback to shell commands (NativeNetworkWrapper.kt)
  - [x] CMake build configuration for native library
  - [x] JNI bindings for ARP operations
  - [x] JNI bindings for network scanning
  - [x] Shell command fallback implementations
  - [x] Root helper binary for privileged operations
    - [x] Standalone executable packaged as libharpy_root_helper.so
    - [x] Support for network scanning via su
    - [x] Support for MAC address resolution via su
    - [x] Support for ARP spoofing via su
    - [x] Support for DNS spoofing via su
    - [x] Support for DHCP spoofing via su
  - [ ] Full libpcap integration for network scanning
  - [ ] Full libnet integration for ARP operations
- [x] JNI integration for ARP manipulation
  - [x] ARP spoofing interface (shell fallback ready)
  - [x] MAC address resolution interface (shell fallback ready)
  - [x] Raw ARP packet sending interface (shell fallback ready)
  - [ ] Native libnet implementation
- [x] DNS spoofing integration
  - [x] DNS spoofing C++ implementation files
  - [x] DNS query handler with packet parsing
    - [x] DNS header parsing and validation
    - [x] Domain name decoding from DNS packets
    - [x] DNS response crafting with spoofed IP injection
    - [x] IPv4 address encoding in DNS responses
  - [x] DNS spoofing server in root helper
    - [x] UDP socket listener on port 53
    - [x] DNS query interception and handling
    - [x] Spoofed response transmission to clients
    - [x] Root privilege requirement handling
  - [x] Root helper DNS spoofing command
  - [x] Kotlin API layer for DNS operations
  - [x] Repository methods for DNS spoofing
  - [x] ViewModel support for DNS spoofing
  - [x] Debug menu UI for DNS spoofing testing
- [x] DHCP spoofing integration
  - [x] DHCP spoofing C++ implementation files
  - [x] DHCP packet handler with request parsing
    - [x] DHCP header parsing and validation
    - [x] Client MAC address extraction from packets
    - [x] DHCP response crafting with custom IP/gateway/DNS
    - [x] Network byte order encoding for DHCP responses
  - [x] DHCP spoofing server in root helper
    - [x] UDP socket listener on port 67
    - [x] DHCP request interception and handling
    - [x] Spoofed response transmission to clients
    - [x] Root privilege requirement handling
  - [x] Rule-based targeting by MAC address
    - [x] Thread-safe rule storage and management
    - [x] Dynamic rule addition/removal
    - [x] Rule matching for selective device targeting
  - [x] Kotlin API layer for DHCP operations
  - [x] Repository methods for DHCP spoofing
  - [x] ViewModel support for DHCP spoofing
  - [x] Debug menu UI for DHCP spoofing testing
- [x] libpcap/libnet integration structure
  - [x] Network scan using shell commands (fallback)
  - [x] ARP operations using shell commands (fallback)
  - [x] Graceful fallback to shell commands when native unavailable
  - [x] Integration guide (LIBPCAP_LIBNET_INTEGRATION.md)
  - [x] Root helper binary for privileged operations
  - [x] Current implementation uses raw socket implementations (not libpcap/libnet yet)
  - [ ] Pre-built binary linking
  - [ ] Full libpcap packet capture
  - [ ] Full libnet packet crafting
- [x] Enhanced root command execution
  - [x] Native library loading with error handling
  - [x] Automatic fallback mechanism
  - [x] Logging for debugging native operations
  - [x] Root helper binary invocation via su
  - [x] Proper coroutine context with IO dispatcher
  - [x] Multi-method MAC address resolution (sysfs, ip link, ip addr, global fallback)
  - [x] Robust gateway IP detection with ConnectivityManager API
  - [x] Dynamic network interface detection
  - [x] Enhanced error messages with detailed context
- [x] Performance benchmarking structure
  - [x] Native operations logging
  - [x] Fallback tracking
  - [ ] Performance metrics collection
- [x] Improved network scanning and ARP operations
  - [x] Refactored C++ code for better performance
  - [x] Thread-safe device discovery with mutex protection
  - [x] Enhanced error handling in native code
  - [x] Optimized socket operations

### Phase 6: Testing & Deployment (Planned)
- [ ] Comprehensive unit testing
- [ ] Integration testing on various devices
- [ ] Security audit
- [ ] Beta testing program
- [ ] Production release preparation

### Phase 7: Non-Root Implementation (Future Plan)
- [ ] VpnService-based network monitoring
- [ ] Limited functionality for non-rooted devices
- [ ] Feature parity assessment
- [ ] Alternative network scanning methods

### Phase 8: Advanced Network Protocols (In Progress)
- [x] DNS spoofing and redirection
  - [x] Root helper command for DNS spoofing
  - [x] Kotlin API layer for DNS spoofing operations
  - [x] Debug menu integration for testing
  - [x] Domain-to-IP redirection support
  - [x] DNS packet parsing and response crafting
- [x] DHCP spoofing and response interception
  - [x] DHCP packet handler with request parsing
  - [x] Spoofed response crafting with custom IP/gateway/DNS
  - [x] Rule-based targeting by MAC address
  - [x] Thread-safe rule management
  - [x] Root helper integration on port 67
  - [x] Kotlin API layer for DHCP spoofing operations
  - [x] Debug menu integration for testing
- [ ] SSL/TLS interception and man-in-the-middle capabilities
- [ ] HTTP/HTTPS proxying and content manipulation
- [ ] Host header manipulation for complete domain redirection
- [ ] SSL certificate handling for HTTPS interception
- [ ] Custom packet injection and crafting
- [ ] Protocol-specific filtering (HTTP, HTTPS, DNS, etc.)
- [ ] Traffic shaping and bandwidth throttling per device
- [ ] Deep packet inspection for content-based filtering
- [ ] libpcap integration for raw packet capture
- [ ] libnet integration for low-level packet crafting

Note: DNS spoofing and DHCP spoofing have been implemented using the root helper architecture. Both features are accessible via the debug menu (long press the bug icon in the main app). DNS spoofing redirects domain queries to specified IP addresses, while DHCP spoofing intercepts DHCP requests and assigns custom IP configurations to target devices. The remaining features can be developed using the current raw socket implementation and do not require complete libpcap/libnet integration to begin implementation. The libpcap/libnet integration is planned as a future enhancement for improved packet processing capabilities. Additional features like HTTP/HTTPS proxying and SSL/TLS interception are planned to enable complete domain redirection functionality.

## Acknowledgements

- Inspired by the iOS jailbreak tweak "Harpy" for network monitoring and control
- Based on research into ARP spoofing techniques and network security concepts
- References to various open-source tools and libraries that informed the approach:
  - [dsniff](https://github.com/traviscross/dsniff) suite containing arpspoof for understanding ARP manipulation
  - [Android network security research](https://source.android.com/security/network-security) papers and documentation
  - [Kotlin and Android development community](https://developer.android.com/) resources
- Special thanks to the Android development community for best practices and guidance