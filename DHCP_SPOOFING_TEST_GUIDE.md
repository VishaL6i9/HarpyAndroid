# DHCP Spoofing Testing Guide

## What is DHCP Spoofing?

DHCP spoofing intercepts DHCP requests from devices and responds with fake IP configurations before the legitimate DHCP server can respond.

## Prerequisites

- Root access on your Android device
- Target device on the same network
- Know the target device's MAC address

## How to Test DHCP Spoofing

### Step 1: Start DHCP Spoofing in Harpy

1. Open Harpy app
2. Long-press the bug icon (debug menu)
3. Scroll to "DHCP Spoofing Test"
4. Enter:
   - **Target MAC**: The MAC address of the device you want to spoof (e.g., `fc:44:82:09:48:cc`)
   - **Spoofed IP**: The fake IP you want to assign (e.g., `192.168.1.100`)
   - **Gateway IP**: Your Android device's IP (e.g., `192.168.1.3`) - This makes your device the gateway
   - **Subnet Mask**: Usually `255.255.255.0`
   - **DNS Server**: Your Android device's IP or any DNS (e.g., `192.168.1.3` or `8.8.8.8`)
5. Click "Start DHCP Spoofing"

### Step 2: Trigger DHCP Request on Target Device

The target device needs to request a new IP. Choose one method:

#### Method A: Forget and Reconnect WiFi (Easiest)
1. On target device, go to WiFi settings
2. Forget the WiFi network
3. Reconnect to the network
4. Device will send DHCP DISCOVER

#### Method B: Toggle Airplane Mode
1. Turn on Airplane mode
2. Wait 5 seconds
3. Turn off Airplane mode
4. Device reconnects and requests new IP

#### Method C: Renew DHCP Lease
**On Android:**
- Settings > Network & Internet > WiFi > Advanced > IP settings > Switch to Static then back to DHCP

**On Windows:**
```cmd
ipconfig /release
ipconfig /renew
```

**On Linux/Mac:**
```bash
sudo dhclient -r
sudo dhclient
```

**On iOS:**
- Settings > WiFi > (i) icon > Renew Lease

### Step 3: Verify DHCP Spoofing Worked

#### Check 1: View Harpy Logs
1. In Harpy, go to Settings > Logging Settings
2. Click "Export Logs"
3. Look for these log entries:
   ```
   DHCP spoofing listening on port 67...
   DHCP spoofing rule matched for [MAC] -> [IP]
   Sending spoofed DHCP response to [MAC]
   ```

#### Check 2: Check Target Device's Network Settings
On the target device, check:
1. **IP Address**: Should be the spoofed IP you configured
2. **Gateway**: Should be your Android device's IP
3. **DNS Server**: Should be what you configured

**How to check:**
- **Android**: Settings > Network & Internet > WiFi > (tap network) > Advanced
- **iOS**: Settings > WiFi > (i) icon
- **Windows**: `ipconfig /all`
- **Linux/Mac**: `ifconfig` or `ip addr show`

#### Check 3: Test Internet Connectivity
If you set your Android device as the gateway:
- Target device will have NO internet (unless you set up IP forwarding)
- This confirms the spoofing worked - traffic is being redirected to your device

### Step 4: Monitor with Logcat (Advanced)

Run this command on your computer with ADB:
```bash
adb logcat | grep -E "DHCPSpoofing|dhcp_spoof"
```

You should see:
```
D/DHCPSpoofing: Starting DHCP spoofing on interface: wlan0
D/DHCPSpoofing: DHCP spoofing listening on port 67...
D/DHCPSpoofing: DHCP spoofing rule matched for fc:44:82:09:48:cc -> 192.168.1.100
D/DHCPSpoofing: Sending spoofed DHCP response
```

## Troubleshooting

### "No DHCP spoofing rule found for MAC"
- The target device's MAC doesn't match your configured MAC
- Double-check the MAC address (case-insensitive, but format matters)

### Target Device Still Gets Real IP
- Your spoofed response arrived too late
- Real DHCP server responded first
- Try again, or move your Android device closer to the target

### "Failed to create DHCP spoofing socket"
- Port 67 requires root access
- Make sure Harpy has root permissions
- Check if another app is using port 67

### Target Device Gets IP but No Internet
- **This is expected!** You set your device as the gateway
- To provide internet, you'd need to enable IP forwarding on your Android device
- This is actually proof that DHCP spoofing worked

## Expected Behavior

### When DHCP Spoofing Works:
1. ✅ Target device gets the spoofed IP address
2. ✅ Target device's gateway points to your Android device
3. ✅ Target device has no internet (unless you enable IP forwarding)
4. ✅ Harpy logs show "DHCP spoofing rule matched"

### When DHCP Spoofing Fails:
1. ❌ Target device gets IP from real DHCP server
2. ❌ Target device's gateway is the real router
3. ❌ Target device has normal internet
4. ❌ Harpy logs show "No DHCP spoofing rule found"

## Security Note

DHCP spoofing is a powerful network attack technique. Only use this on networks you own or have explicit permission to test. Unauthorized use may be illegal.

## Advanced: Enable IP Forwarding (Optional)

If you want the target device to have internet through your Android device:

1. Enable IP forwarding:
   ```bash
   su
   echo 1 > /proc/sys/net/ipv4/ip_forward
   ```

2. Set up NAT (Network Address Translation):
   ```bash
   iptables -t nat -A POSTROUTING -o wlan0 -j MASQUERADE
   iptables -A FORWARD -i wlan0 -o wlan0 -j ACCEPT
   ```

Now your Android device acts as a router, and you can intercept/monitor all traffic from the target device!
