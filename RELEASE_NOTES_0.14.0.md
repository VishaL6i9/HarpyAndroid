# HarpyAndroid v0.14.0-beta

## What's New

### iOS Void Attack Toolkit
- **DHCP Self-Implosion (Primary Void)**: Forges unicast DHCPACK packets with the router set to the client's own IP address and a /32 subnet mask. The iOS kernel deletes its default gateway and installs a host route pointing to itself — outbound traffic collapses. Captures XID from real DHCP requests to race and beat the legitimate server's response.
- **ICMP Redirect Forge (Secondary Void)**: Sends forged ICMP Type 5 redirect packets to iOS devices, advertising the client's own IP as the gateway for the entire IPv4 space (0.0.0.0/1 + 128.0.0.0/1). iOS inserts dynamic host routes that void all external destinations.
- **DNS Nullification (Tertiary Void)**: Sends unicast DHCPACK with DNS Server (Option 6) set to 0.0.0.0. Network stays up but no hostnames resolve — browsers spin indefinitely, iMessage fails, App Store breaks.
- **TCP RST Asymmetry (Failsafe)**: Sniffs every TCP SYN from the target and immediately responds with a forged TCP RST spoofing the destination server. Every connection attempt is killed at the handshake level. Pure Layer 4 — works when DHCP is locked or protected by MDM profiles.

### Auto-Detection for iOS Attacks
- **Gateway Auto-Fill**: Router IP and MAC are automatically detected from scanned network devices and pre-filled into the iOS attack target configuration.
- **Target MAC Auto-Fill**: When a target IP is entered that matches a scanned device, the MAC address is automatically populated.

## Improvements

- **iOS Attack UI**: Dedicated screen under Settings with individual deploy/stop controls for each of the 4 void vectors, status indicators, and target configuration fields.
- **Shared ViewModel Wiring**: iOS attack screen now shares the same `NetworkMonitorViewModel` as the bottom navigation screens, ensuring scanned device data is always available.

## Technical Changes

- Added 3 new C++ native modules: `ios_dhcp_void` (XID sniff + unicast DHCPACK forging), `icmp_redirect` (ICMP Type 5 packet crafting), `tcp_rst_spoof` (SYN sniff + forged RST with cooldown caching).
- Added `IosAttackRepository` with full process lifecycle management for all 4 attack vectors via root helper binary.
- Added `IosAttackViewModel` with state management and auto-fill methods for router/target device information.
- Registered 4 new root helper CLI commands: `ios_dhcp_void`, `ios_dhcp_nullify_dns`, `icmp_redirect`, `tcp_rst`.
- Updated `NetworkMonitorViewModel` sharing to support auto-detection in the iOS attack screen.
- Fixed sealed class pattern matching for `NetworkResult` across iOS attack ViewModel.
