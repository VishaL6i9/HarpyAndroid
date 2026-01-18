# Harpy Android

A network monitoring and control application for Android, inspired by the iOS jailbreak tweak Harpy. This app allows users to discover devices on their local network and manage network access.

## Features

### Root-Based Functionality (Current Implementation)
- Network device discovery using ARP scanning with instant results
- Detailed device information (IP address, MAC address, hostname, vendor)
- Ability to block/disconnect specific devices from the network using ARP spoofing
- Gateway detection and identification with visual indicators
- Nuclear option: Block all devices on the network by spoofing the gateway
- Device management interface for controlling network access
- Real-time network monitoring
- Custom device naming for easy identification
- Device pinning to keep important devices at the top
- Vendor lookup using OUI database for accurate manufacturer identification
- Immersive fullscreen mode with auto-hiding status bar
- OLED-optimized dark theme with true blacks
- Device ping testing to verify connectivity
- Root helper binary for privileged network operations
- Bottom sheet UI for device actions with confirmation dialogs

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

### Non-Root Approach (Future Plan)
- Network monitoring using Android's VpnService
- Traffic analysis for the device's own network usage
- Limited functionality compared to root-based approach
- Compatible with non-rooted devices

## Technical Implementation

The root-based implementation is built using native Kotlin for the Android application layer, with native C/C++ code using libpcap/libnet libraries accessed through JNI for low-level network operations when root access is available. The app leverages root access to execute ARP spoofing techniques similar to the original iOS Harpy tweak.

### Root Helper Binary
A standalone executable (`libharpy_root_helper.so`) is packaged with the app and can be invoked via `su` to perform privileged network operations without requiring the entire app to run as root. This provides better security isolation and allows for more granular permission control.

Supported commands:
- `scan <interface> <subnet> [timeout]` - Network device discovery (default 10s timeout)
- `mac <interface> <ip>` - MAC address resolution
- `block <interface> <target_ip> <gateway_ip> <our_mac>` - Bidirectional ARP spoofing for device blocking
- `block_all <interface> <gateway_ip> <our_mac>` - Nuclear option: Block all devices via gateway spoofing
- `unblock <interface> <target_ip> <target_mac> <gateway_ip> <gateway_mac>` - ARP cache restoration to unblock device

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
- `features/device_manager/` - Device management feature with the same layered architecture

### Architecture Principles
- **MVVM Pattern**: Clean separation of View, ViewModel, and Model
- **Clean Architecture**: Each layer has a specific responsibility
- **Feature-First Organization**: Related functionality is grouped together
- **Dependency Injection**: Proper decoupling of components
- **State Management**: Using Kotlin Flows for reactive programming

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
  - [x] Long-press context menu for device actions
  - [x] Debug menu for clearing custom names
  - [x] Red highlighting for blocked devices
  - [x] Blue highlighting for current device
  - [x] Green highlighting for gateway device
  - [x] Immediate UI updates after block/unblock operations
  - [x] Bottom sheet UI for device actions
  - [x] Two-stage confirmation dialogs for nuclear option
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
  - [ ] Full libpcap integration for network scanning
  - [ ] Full libnet integration for ARP operations
- [x] JNI integration for ARP manipulation
  - [x] ARP spoofing interface (shell fallback ready)
  - [x] MAC address resolution interface (shell fallback ready)
  - [x] Raw ARP packet sending interface (shell fallback ready)
  - [ ] Native libnet implementation
- [x] libpcap/libnet integration structure
  - [x] Network scan using shell commands (fallback)
  - [x] ARP operations using shell commands (fallback)
  - [x] Graceful fallback to shell commands when native unavailable
  - [x] Integration guide (LIBPCAP_LIBNET_INTEGRATION.md)
  - [x] Root helper binary for privileged operations
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

### Phase 8: Advanced Network Protocols (Future Plan)
- [ ] DNS spoofing and redirection
- [ ] DHCP spoofing and response interception
- [ ] SSL/TLS interception and man-in-the-middle capabilities
- [ ] Custom packet injection and crafting
- [ ] Protocol-specific filtering (HTTP, HTTPS, DNS, etc.)
- [ ] Traffic shaping and bandwidth throttling per device
- [ ] Deep packet inspection for content-based filtering
- [ ] libpcap integration for raw packet capture
- [ ] libnet integration for low-level packet crafting

## Acknowledgements

- Inspired by the iOS jailbreak tweak "Harpy" for network monitoring and control
- Based on research into ARP spoofing techniques and network security concepts
- References to various open-source tools and libraries that informed the approach:
  - [dsniff](https://github.com/traviscross/dsniff) suite containing arpspoof for understanding ARP manipulation
  - [Android network security research](https://source.android.com/security/network-security) papers and documentation
  - [Kotlin and Android development community](https://developer.android.com/) resources
- Special thanks to the Android development community for best practices and guidance