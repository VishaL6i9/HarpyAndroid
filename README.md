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

The root-based implementation is built using native Kotlin for the Android application layer, with native C/C++ code using libpcap/libnet libraries accessed through JNI for low-level network operations when root access is available. The app leverages root access to execute ARP spoofing techniques similar to the original iOS Harpy tweak.

## Requirements

- Android device with root access
- Android 7.0 (API level 24) or higher recommended

## Legal Notice

This tool should only be used on networks you own or have explicit permission to manage. Unauthorized network interference may violate local laws.