# Harpy

A powerful network monitoring and management tool for Android, built with modern Jetpack Compose.

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.02-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Overview

Harpy is a network monitoring application that gives you complete visibility and control over devices on your local network. Discover connected devices, manage network access, and configure advanced network protocols—all from an intuitive Material 3 interface.

> **Note:** This application requires root access to function properly.

## Features

### Network Discovery
- **Real-time device scanning** using ARP protocol
- **Detailed device information** including IP address, MAC address, and manufacturer
- **Gateway detection** with visual indicators
- **Custom device naming** with persistent storage
- **Device pinning** to keep important devices at the top
- **IPv4/IPv6 filtering** for better organization

### Network Control
- **Block/unblock devices** using ARP spoofing
- **Persistent blocking** that survives app restarts
- **Bulk operations** to unblock all devices at once
- **Ping testing** to verify device connectivity
- **Gateway blocking** (nuclear option) to disconnect all devices

### Advanced Features
- **DNS spoofing** for domain redirection
- **DHCP spoofing** for custom IP assignment
- **Network topology mapping**
- **Real-time logging** with export functionality
- **Root helper binary** for secure privileged operations

### Modern UI
- Built entirely with **Jetpack Compose** and **Material 3**
- **Dark theme** optimized for OLED displays
- **Smooth animations** and transitions
- **Bottom navigation** for easy feature access
- **Responsive design** that adapts to different screen sizes

## Screenshots

_Coming soon_

## Getting Started

### Prerequisites

**Runtime Requirements:**
- Android device with root access
- Android 7.0 (API 24) or higher
- Active Wi-Fi connection

**Development Requirements:**
- Android Studio Hedgehog or later
- JDK 21
- Android SDK 36
- Gradle 8.13

### Installation

#### From Source

1. Clone the repository:
```bash
git clone https://github.com/VishaL6i9/HarpyAndroid.git
cd HarpyAndroid
```

2. Build the debug APK:
```bash
./gradlew assembleDebug
```

3. Install on your device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or build and install in one step:
```bash
./gradlew installDebug
```

#### First Launch

1. Grant all requested permissions
2. Grant root access when prompted
3. Tap "Scan Network" to discover devices

## Usage

### Scanning Your Network

1. Open the app and navigate to the **Network Monitor** tab
2. Tap the **Scan Network** button
3. Wait for the scan to complete (typically 5-10 seconds)
4. View all discovered devices in the list

### Managing Devices

**Block a device:**
- Tap the block icon on any device card
- Confirm the action in the dialog

**Unblock a device:**
- Tap the checkmark icon on a blocked device

**Customize device name:**
- Tap the edit icon on any device
- Enter a custom name
- The device will appear at the top of the list

**Pin a device:**
- Tap the pin icon to keep the device visible at the top

**Test connectivity:**
- Tap the ping icon to verify if a device is reachable

### DNS Spoofing

1. Navigate to the **DNS Spoofing** tab
2. Tap **Start DNS Spoofing**
3. Enter the domain to spoof (e.g., `example.com`)
4. Enter the IP address to redirect to
5. Specify the network interface (usually `wlan0`)
6. Tap **Start**

### DHCP Spoofing

1. Navigate to the **DHCP Spoofing** tab
2. Tap **Start DHCP Spoofing**
3. Enter the target device's MAC address
4. Configure the spoofed IP, gateway, and DNS settings
5. Tap **Start**

## Architecture

Harpy follows modern Android development best practices with a clean architecture approach.

### Project Structure

```
app/src/main/kotlin/com/vishal/harpy/
├── core/                          # Shared utilities and components
│   ├── native/                    # JNI bindings for C++ code
│   ├── network/                   # Network utilities
│   ├── ui/                        # Common UI components
│   └── utils/                     # Helper classes
├── features/                      # Feature modules
│   ├── device_manager/
│   │   ├── data/                  # Data layer (repositories)
│   │   ├── domain/                # Business logic (use cases)
│   │   └── presentation/          # ViewModels
│   ├── network_monitor/
│   ├── dns/
│   │   ├── data/
│   │   ├── domain/
│   │   └── di/
│   └── dhcp/
│       ├── data/
│       ├── domain/
│       └── di/
├── ui/                            # Compose UI layer
│   ├── screens/                   # Screen composables
│   │   ├── network/
│   │   ├── dns/
│   │   └── dhcp/
│   ├── theme/                     # Material 3 theme
│   └── HarpyApp.kt               # Main app composable
└── main/
    └── MainActivityCompose.kt     # Entry point
```

### Tech Stack

**UI Layer:**
- Jetpack Compose for declarative UI
- Material 3 components
- Compose Navigation
- Lifecycle-aware state management

**Domain Layer:**
- Use cases for business logic
- Repository pattern for data access
- Kotlin Coroutines for async operations

**Data Layer:**
- SharedPreferences with `SettingsRepository` for application and device preferences
- Native C++ for low-level network operations
- Root helper binary for privileged operations

**Dependency Injection:**
- Hilt for compile-time DI

**Native Code:**
- C++ for raw socket operations
- JNI for Kotlin-C++ interop
- CMake for native builds

## Technical Details

### Network Operations

Harpy uses a combination of techniques for network operations:

**Device Discovery:**
- ARP scanning with optimized packet pacing
- Multi-pass scanning for reliability
- Vendor identification using OUI database

**Device Blocking:**
- Bidirectional ARP spoofing
- Persistent block state tracking
- Automatic restoration after app restart

**DNS Spoofing:**
- UDP socket listener on port 53
- DNS packet parsing and response crafting
- Domain-to-IP redirection

**DHCP Spoofing:**
- UDP socket listener on port 67
- DHCP packet interception
- Custom IP configuration injection

### Root Helper Binary

For security and stability, privileged operations are performed by a separate root helper binary (`libharpy_root_helper.so`). This provides:

- Better security isolation
- Granular permission control
- Improved stability
- Easier debugging

The helper supports these commands:
- `scan` - Network device discovery
- `mac` - MAC address resolution
- `block` - Device blocking via ARP spoofing
- `unblock` - Device unblocking
- `dns_spoof` - DNS query interception
- `dhcp_spoof` - DHCP request interception

## Performance

Harpy is optimized for efficiency:

- **Fast scanning:** Typical network scan completes in 5-10 seconds
- **Low memory footprint:** Efficient data structures and caching
- **Battery friendly:** Operations are performed on-demand
- **Smooth UI:** 60 FPS animations with Compose

## Security & Privacy

- **Root access required:** All privileged operations require explicit root permission
- **Local operation:** No data is sent to external servers
- **Transparent logging:** All operations are logged for debugging
- **Open source:** Code is available for security review

## Legal Notice

⚠️ **Important:** This tool should only be used on networks you own or have explicit permission to manage. Unauthorized network interference may violate local laws and regulations.

The developers of Harpy are not responsible for any misuse of this application. Use at your own risk.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### Development Setup

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add comments for complex logic
- Write unit tests for new features

## Roadmap

### Completed ✅
- [x] Jetpack Compose migration with Material 3
- [x] Network device discovery and management
- [x] Device blocking/unblocking with ARP spoofing
- [x] DNS and DHCP spoofing
- [x] Persistent device preferences
- [x] Root helper binary architecture
- [x] Real-time logging system with log management utilities
- [x] Comprehensive application settings (Scan timeout, interface selection, debug mode)

### In Progress 🚧
- [ ] Comprehensive unit testing
- [ ] Integration tests
- [ ] Performance benchmarking

### Planned 📋
- [ ] Network traffic analysis
- [ ] Bandwidth monitoring per device
- [ ] Scheduled blocking
- [ ] Network usage statistics
- [ ] SSL/TLS interception
- [ ] HTTP/HTTPS proxying
- [ ] VpnService-based non-root mode

## Troubleshooting

### App crashes on launch
- Ensure your device is rooted
- Grant root permission when prompted
- Check logcat for error messages

### Network scan finds no devices
- Verify you're connected to Wi-Fi
- Check that root access is granted
- Try increasing scan timeout in **Settings > Scan Settings**
- Ensure the correct network interface is selected in **Settings > Interface Selection** (usually `wlan0`)

### Device blocking doesn't work
- Ensure the device is on the same network
- Verify root helper binary is installed correctly
- Check logs for error messages

### Build errors
```bash
# Clean and rebuild
./gradlew clean
./gradlew assembleDebug --refresh-dependencies
```

## Acknowledgements

Harpy is inspired by the iOS jailbreak tweak of the same name. Special thanks to:

- The Android development community
- Contributors to open-source networking tools
- The Jetpack Compose team at Google

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contact

- **Issues:** [GitHub Issues](https://github.com/VishaL6i9/HarpyAndroid/issues)
- **Discussions:** [GitHub Discussions](https://github.com/VishaL6i9/HarpyAndroid/discussions)

---

**Disclaimer:** This tool is provided for educational and authorized network management purposes only. Always obtain proper authorization before monitoring or modifying network traffic.
