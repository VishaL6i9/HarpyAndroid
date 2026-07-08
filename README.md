# HarpyAndroid

A network monitoring and management tool for Android, built with Jetpack Compose.

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.02-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-0.14.0--beta-blue.svg)](RELEASE_NOTES_0.13.0.md)

## Overview

Harpy is a network monitoring application that displays details and controls for devices on a local network. It allows discovering connected devices, managing network access, and configuring network protocols from a Jetpack Compose Material 3 interface.

> **Note:** This application requires root access to execute its active network modification features.

---

## Privileged Capabilities and Limitations

We analyzed the feasibility of running HarpyAndroid using Shizuku (which grants ADB shell UID 2000 permissions) instead of direct root (UID 0) access. The system permissions partition is as follows:

### Shizuku / ADB Shell Permissions (UID 2000)
* **Allowed**: Reading the ARP cache (`/proc/net/arp` or executing `ip neigh show`) to scan and discover local devices. This allows device discovery to function on Android 10 and higher, where standard applications are blocked from accessing these files.
* **Allowed**: Listing running system processes via the `ps` command.
* **Blocked**: Opening raw sockets (`AF_PACKET`) to send custom ARP frames. This operation requires the `CAP_NET_RAW` Linux capability, which is not granted to the `shell` user.
* **Blocked**: Binding to privileged UDP/TCP ports below 1024 (such as port 53 for DNS spoofing or port 67 for DHCP spoofing).

### Root Permissions (UID 0)
* **Allowed**: All discovery features, process management, custom raw socket creation, and binding to privileged system ports.

Because active network interception features rely on raw sockets and privileged ports, **ARP spoofing, DNS spoofing, DHCP spoofing, and the iOS Void Attack toolkit require root access.**

---

## Features

### Network Discovery
* **Real-time device scanning** using the ARP protocol.
* **Detailed device information** including IP address, MAC address, and device manufacturer.
* **Gateway detection** with interface indicators.
* **Custom device naming** with persistent storage across app restarts.
* **Device pinning** to keep selected devices visible at the top.
* **IPv4/IPv6 filtering** for list organization.
* **Real-time Interface Selection**: Dynamic detection of active network interfaces (such as `wlan0`, `ap0`, `eth0`) for scanning operations.
* **Interface Mismatch Protection**: Warning banner and instant interface switching logic when the system network changes.
* **Smart Device Name Caching**: Saved device names load before the first network scan after an app restart.

### Network Control (Requires Root)
* **Block/unblock devices** using three methods: ARP spoofing, blackhole routing, or traffic control.
* **Per-Device Rate Limiting**: Bandwidth limits for specific devices using the Linux traffic control utility.
* **Persistent blocking** across app restarts.
* **Bulk operations** to unblock all devices.
* **Ping testing** to check device connectivity.
* **Gateway blocking** to disconnect all devices on the subnet.

### Advanced Features (Requires Root)
* **DNS spoofing** with domain matching, target IP selection, and upstream query forwarding.
* **DHCP spoofing** with auto-detected gateway and interface configuration.
* **Spoofing session management**: Unified controls to pause, resume, and edit active DNS and DHCP sessions.
* **iOS Void Attack Toolkit**: Four Layer 3 and 4 vectors to block iOS network connectivity: DHCP self-implosion, ICMP redirect forgery, DNS nullification, and TCP RST asymmetry.
* **Performance monitoring** with real-time CPU/memory charts and process management.
* **Network topology mapping**.
* **Real-time logging** with export capability.
* **Helper binary** for running privileged operations.
* **Dynamic network interface detection** across all application screens.

### User Interface
* Built with **Jetpack Compose** and **Material 3**.
* **Dark theme** optimized for OLED displays.
* **Animations** and transitions.
* **Bottom navigation** for feature access.
* **Responsive layouts** adapting to different screen sizes.
* **Stack-based navigation** with scroll state preservation.
* **Form auto-fill** in spoofing dialogs with dropdown selectors.
* **MAC deduplication** to prevent UI errors on complex networks.
* **Full API 24/25 Support**: Compatible process management using custom `ProcessUtils` for Android 7.x devices.

---

## Getting Started

### Prerequisites

**Runtime Requirements:**
* Android device with root access.
* Android 7.0 (API 24) or higher.
* Active Wi-Fi connection.

**Development Requirements:**
* Android Studio Hedgehog or later.
* JDK 21.
* Android SDK 36.
* Gradle 8.13.

### Installation

#### From Source

1. Clone the repository:
```bash
git clone https://github.com/VishaL6i9/HarpyAndroid.git
cd HarpyAndroid
```

2. Build the debug APK for your desired flavor:

**Standard flavor:**
```bash
./gradlew assembleStandardDebug
```

**CTOS flavor:**
```bash
./gradlew assembleCtosDebug
```

3. Install on your device:

**Standard flavor:**
```bash
adb install app/build/outputs/apk/standard/debug/app-standard-debug.apk
```

**CTOS flavor:**
```bash
adb install app/build/outputs/apk/ctos/debug/app-ctos-debug.apk
```

Or build and install in one step:

**Standard flavor:**
```bash
./gradlew installStandardDebug
```

**CTOS flavor:**
```bash
./gradlew installCtosDebug
```

#### First Launch

1. Grant the requested application permissions.
2. Grant root access when prompted by your superuser manager.
3. Tap "Scan Network" to discover devices.

---

## Usage

### Scanning Your Network

1. Open the app and navigate to the **Network Monitor** tab.
2. Tap the **Scan Network** button. The app will detect the active interface and display a warning if a mismatch occurs.
3. Wait for the scan to complete.
4. View discovered devices in the device list.

### Managing Devices

**Block a device:**
* Tap the block icon on a device card.
* Confirm the action in the dialog.

**Unblock a device:**
* Tap the checkmark icon on a blocked device.

**Customize device name:**
* Tap the edit icon on any device.
* Enter a custom name.
* The device will appear at the top of the list.

**Pin a device:**
* Tap the pin icon to keep the device visible at the top.

**Test connectivity:**
* Tap the ping icon to check if a device is reachable.

### DNS Spoofing

1. Navigate to the **DNS Spoofing** tab.
2. Tap **Start DNS Spoofing**.
3. Enter the domain to spoof (e.g., `example.com`).
4. Enter the IP address to redirect to.
5. Specify the network interface (e.g., `wlan0`).
6. Tap **Start**.

### DHCP Spoofing

1. Navigate to the **DHCP Spoofing** tab.
2. Tap **Start DHCP Spoofing**.
3. Enter the target device's MAC address.
4. Configure the spoofed IP, gateway, and DNS settings.
5. Tap **Start**.

### iOS Void Attacks

1. Navigate to **Settings > Advanced > iOS Void Attacks**.
2. Target configuration:
   - Enter the **Target IP**. The MAC address auto-fills if a matching device was scanned.
   - **Router IP** and **Router MAC** auto-detect from the network gateway.
3. Deploy one or more void vectors:
   - **① DHCP Self-Implosion**: Forges a unicast DHCPACK with the router set to the client's own IP and a /32 subnet. The iOS device deletes its default gateway.
   - **② ICMP Redirect Forge**: Forges ICMP Type 5 redirects covering the IPv4 space via the client's own IP.
   - **③ DNS Nullification**: Sets the DNS Server to 0.0.0.0. The network interface remains active but names do not resolve.
   - **④ TCP RST Asymmetry**: Sniffs TCP SYN packets and replies with forged RST packets. Works when DHCP configuration is locked.
4. Tap **DEPLOY** on any vector to activate it. Tap **STOP ATTACK** to disable.

### Performance Monitoring

1. Navigate to **Settings > Performance Monitor**.
2. View real-time CPU usage, memory consumption, and thermal statistics.
3. Tap on any metric to view detailed graphs.
4. Access the process list to view running processes.
5. Sort processes by memory, CPU, name, or UID.
6. Stop processes or adjust OOM scores (requires root).

---

## Architecture

Harpy follows clean architecture guidelines and uses Product Flavors for build variants.

### Build Architecture: Product Flavors

Harpy uses **Android Product Flavors** to manage multiple build variants (standard and ctos) while keeping a shared codebase:

**Shared Code Structure (src/main/):**
* Common screens, repositories, use cases, and ViewModels.
* Common UI components and navigation.

**Flavor-Specific Code:**
* `src/standard/`: Standard theme and flavor-specific configuration.
* `src/ctos/`: CTOS theme and flavor-specific configuration.

**Theme Injection via Hilt:**
* `ThemeProvider` interface defines the theme contract.
* `StandardThemeModule`: Provides `StandardTheme` for the standard flavor.
* `CtosThemeModule`: Provides `CtosTheme` for the ctos flavor.
* Dynamic theme injection through Hilt.

This approach reduces code duplication across flavors.

### Project Structure

```
app/src/main/kotlin/com/vishal/harpy/
├── core/                          # Shared utilities and components
│   ├── native/                    # JNI bindings for C++ code
│   ├── network/                   # Network utilities
│   ├── ui/                        # Common UI components
│   ├── di/                        # Dependency injection modules
│   ├── service/                   # Background services
│   ├── state/                     # State management
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
│   │   ├── dhcp/
│   │   ├── settings/
│   │   ├── status/
│   │   ├── device_management/
│   │   └── performance/           # Performance monitoring screens
│   ├── components/                # Reusable UI components
│   ├── theme/                     # Material 3 theme (shared interface)
│   ├── di/                        # Theme provider modules
│   ├── viewmodel/                 # Shared ViewModels
│   └── HarpyApp.kt               # Main app composable
└── main/
    └── MainActivityCompose.kt     # Entry point

app/src/standard/kotlin/com/vishal/harpy/
├── ui/
│   ├── theme/
│   │   └── StandardTheme.kt       # Standard flavor theme
│   └── di/
│       └── StandardThemeModule.kt # Hilt module for standard theme
└── ...                            # Other flavor-specific overrides

app/src/ctos/kotlin/com/vishal/harpy/
├── ui/
│   ├── theme/
│   │   └── CtosTheme.kt           # CTOS flavor theme
│   └── di/
│       └── CtosThemeModule.kt     # Hilt module for CTOS theme
└── ...                            # Other flavor-specific overrides
```

### Tech Stack

**UI Layer:**
* Jetpack Compose for declarative UI.
* Material 3 components.
* Compose Navigation with stack-based back navigation.
* Lifecycle-aware state management.
* Scroll state preservation across navigation.

**Domain Layer:**
* Use cases for business logic.
* Repository pattern for data access.
* Kotlin Coroutines for async operations.

**Data Layer:**
* SharedPreferences with `SettingsRepository` for application and device preferences.
* Native C++ for low-level network operations.
* Helper binary for running privileged operations.

**Dependency Injection:**
* Hilt for compile-time DI with custom `ServiceEntryPoint`.

**Native Code:**
* C++ for raw socket operations.
* JNI for Kotlin-C++ interop.
* CMake for native builds.

---

## Technical Details

### Network Operations

Harpy uses a combination of techniques for network operations:

**Device Discovery:**
* ARP scanning with packet pacing.
* Multi-pass scanning for reliability.
* Vendor identification using the OUI database.

**Device Blocking:**
* Bidirectional ARP spoofing.
* Persistent block state tracking.
* Automatic restoration after app restart.

**DNS Spoofing:**
* UDP socket listener on port 53.
* DNS packet parsing and response crafting.
* Domain-to-IP redirection.

**DHCP Spoofing:**
* UDP socket listener on port 67.
* DHCP packet interception.
* Custom IP configuration injection.

### Privileged Helper Binary

For process separation and stability, privileged operations are executed by a separate helper binary (`libharpy_root_helper.so`).

The helper supports these command commands:
* `scan`: Network device discovery.
* `mac`: MAC address resolution.
* `block`: Device blocking via ARP spoofing.
* `block_iptables_drop`: Device blocking via blackhole route.
* `block_traffic_control`: Device rate limiting or blocking via traffic control.
* `unblock`: Device unblocking.
* `dns_spoof`: DNS query interception.
* `dhcp_spoof`: DHCP request interception.
* `ios_dhcp_void`: iOS DHCP self-implosion (XID sniff and unicast self-router ACK).
* `ios_dhcp_nullify_dns`: iOS DNS nullification via DHCPACK.
* `icmp_redirect`: ICMP Type 5 redirect forgery.
* `tcp_rst`: TCP RST asymmetry (SYN sniff and reply with forged RST).

---

## Legal Notice

⚠️ **Important:** This tool should only be used on networks you own or have explicit permission to manage. Unauthorized network interference is illegal under various local regulations.

The developers of Harpy are not responsible for any misuse of this application. Use at your own risk.

---

## Contributing

Contributions are welcome. Please submit a Pull Request.

### Development Setup

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/amazing-feature`).
3. Commit your changes (`git commit -m 'Add amazing feature'`).
4. Push to the branch (`git push origin feature/amazing-feature`).
5. Open a Pull Request.

### Code Style

* Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
* Use descriptive variable and function names.
* Add comments for non-obvious logic.
* Write unit tests for new features.

---

## Troubleshooting

### App crashes on launch
* Verify your device is rooted.
* Grant root permission when prompted.
* Check logcat for error logs.

### Network scan finds no devices
* Verify you are connected to a Wi-Fi network.
* Verify root access is granted.
* Increase the scan timeout in **Settings > Scan Settings**.
* Verify the correct network interface is selected in **Settings > Interface Selection** (the app displays a warning if there is a mismatch).

### Device blocking does not take effect
* Verify the target device is on the same local network.
* Verify the helper binary is installed.
* Check logs for error messages.

### Build errors
```bash
# Clean and rebuild
./gradlew clean
./gradlew assembleDebug --refresh-dependencies
```

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
