package com.vishal.harpy.features.network_monitor.data.repository.impl

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.core.utils.NetworkTopology
import com.vishal.harpy.core.utils.NetworkResult
import com.vishal.harpy.core.utils.NetworkError
import com.vishal.harpy.core.utils.RootError
import com.vishal.harpy.core.utils.RootErrorMapper
import com.vishal.harpy.core.utils.VendorLookup
import com.vishal.harpy.core.native.NativeNetworkWrapper
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import kotlin.text.Regex

class NetworkMonitorRepositoryImpl : NetworkMonitorRepository {

    companion object {
        private const val TAG = "NetworkMonitorRepoImpl"
        
        // Cache regex patterns for better performance
        private val SUBNET_PATTERN = Regex("""([0-9]+\.[0-9]+\.[0-9]+)\.0""")
        private val HOSTNAME_PATTERN = Regex("name = (.+)")
        private val FIELDS_SPLIT_PATTERN = Regex("\\s+")
    }

    override suspend fun scanNetwork(): NetworkResult<List<NetworkDevice>> {
        return try {
            Log.d(TAG, "Scanning network for connected devices...")

            val isRootedResult = isDeviceRooted()
            if (isRootedResult is NetworkResult.Success && isRootedResult.data) {
                val devices = scanNetworkWithRoot()
                NetworkResult.success(devices)
            } else {
                Log.w(TAG, "Device is not rooted. Limited functionality available.")
                NetworkResult.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning network: ${e.message}", e)
            NetworkResult.error(NetworkError.NetworkScanError(e))
        }
    }

    override suspend fun isDeviceRooted(): NetworkResult<Boolean> {
        return try {
            // Try multiple methods to detect root
            
            // Method 1: Try su command
            try {
                val process = Runtime.getRuntime().exec("su")
                val outputStream = process.outputStream
                val inputStream = process.inputStream

                outputStream.write("id\n".toByteArray())
                outputStream.flush()
                outputStream.close()

                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = reader.readLine()
                reader.close()
                inputStream.close()

                process.waitFor()
                process.destroy()

                if (response != null && response.contains("uid=0")) {
                    Log.d(TAG, "Device is rooted (su method)")
                    return NetworkResult.success(true)
                }
            } catch (e: Exception) {
                Log.d(TAG, "su method failed: ${e.message}")
            }

            // Method 2: Check for Magisk
            try {
                val magiskProcess = Runtime.getRuntime().exec("which magisk")
                val magiskReader = BufferedReader(InputStreamReader(magiskProcess.inputStream))
                val magiskPath = magiskReader.readLine()
                magiskReader.close()
                magiskProcess.waitFor()

                if (!magiskPath.isNullOrEmpty()) {
                    Log.d(TAG, "Device is rooted (Magisk detected at: $magiskPath)")
                    return NetworkResult.success(true)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Magisk check failed: ${e.message}")
            }

            // Method 3: Check for /system/xbin/su or /system/bin/su
            try {
                val suFile1 = java.io.File("/system/xbin/su")
                val suFile2 = java.io.File("/system/bin/su")
                val suFile3 = java.io.File("/data/adb/magisk/magisk")
                
                if (suFile1.exists() || suFile2.exists() || suFile3.exists()) {
                    Log.d(TAG, "Device is rooted (su binary found)")
                    return NetworkResult.success(true)
                }
            } catch (e: Exception) {
                Log.d(TAG, "File check failed: ${e.message}")
            }

            Log.d(TAG, "Device is not rooted")
            NetworkResult.success(false)
        } catch (e: IOException) {
            Log.d(TAG, "Device is not rooted: ${e.message}")
            val rootError = RootError.RootAccessDeniedError(e)
            RootErrorMapper.logErrorWithStackTrace("Root check failed with IOException", rootError)
            NetworkResult.error(NetworkError.DeviceNotRootedError(e))
        } catch (e: InterruptedException) {
            Log.e(TAG, "Root check interrupted: ${e.message}", e)
            val rootError = RootError.RootTimeoutError(e)
            RootErrorMapper.logErrorWithStackTrace("Root check interrupted", rootError)
            NetworkResult.error(NetworkError.CommandExecutionError(e))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during root check: ${e.message}", e)
            val rootError = RootError.RootCheckFailedError(e)
            RootErrorMapper.logErrorWithStackTrace("Unexpected error during root check", rootError)
            NetworkResult.error(NetworkError.DeviceNotRootedError(e))
        }
    }

    private fun scanNetworkWithRoot(): List<NetworkDevice> {
        val devices = mutableListOf<NetworkDevice>()

        try {
            Log.d(TAG, "Getting network route information...")
            
            // Try to get route with su first, then fallback to direct command
            var routeLine: String? = null
            
            try {
                val suProcess = Runtime.getRuntime().exec("su")
                val suOutput = DataOutputStream(suProcess.outputStream)
                suOutput.writeBytes("ip route\n")
                suOutput.writeBytes("exit\n")
                suOutput.flush()
                suOutput.close()
                
                val suReader = BufferedReader(InputStreamReader(suProcess.inputStream))
                var line: String?
                while (suReader.readLine().also { line = it } != null) {
                    Log.d(TAG, "Route line: $line")
                    if (line != null && line.matches(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+.*"))) {
                        routeLine = line
                        break
                    }
                }
                suReader.close()
                
                val completed = suProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                if (!completed) {
                    suProcess.destroyForcibly()
                }
            } catch (e: Exception) {
                Log.d(TAG, "su route command failed: ${e.message}, trying direct command")
                try {
                    val ipProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ip route"))
                    val ipReader = BufferedReader(InputStreamReader(ipProcess.inputStream))
                    var line: String?
                    while (ipReader.readLine().also { line = it } != null) {
                        Log.d(TAG, "Route line (direct): $line")
                        if (line != null && line.matches(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+.*"))) {
                            routeLine = line
                            break
                        }
                    }
                    ipReader.close()
                    
                    val completed = ipProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                    if (!completed) {
                        ipProcess.destroyForcibly()
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Direct route command also failed: ${e2.message}")
                }
            }

            if (routeLine != null) {
                Log.d(TAG, "Found route: $routeLine")
                val matchResult = SUBNET_PATTERN.find(routeLine)

                if (matchResult != null) {
                    val subnet = matchResult.groupValues[1]
                    Log.d(TAG, "Scanning subnet: $subnet.0/24")

                    // Try native implementation first
                    Log.d(TAG, "Attempting native network scan...")
                    val nativeWrapper = NativeNetworkWrapper()
                    
                    if (nativeWrapper.isNativeAvailable()) {
                        val nativeDevices = nativeWrapper.scanNetworkNative(
                            "wlan0",
                            subnet,
                            10  // Increased timeout to 10 seconds
                        )
                        
                        if (nativeDevices.isNotEmpty()) {
                            Log.d(TAG, "Native scan found ${nativeDevices.size} devices")
                            for (ip in nativeDevices) {
                                Log.d(TAG, "Native found: $ip")
                                
                                // Get MAC from ARP table
                                try {
                                    val arpProcess = Runtime.getRuntime().exec("su")
                                    val arpOutput = DataOutputStream(arpProcess.outputStream)
                                    arpOutput.writeBytes("ip neigh show $ip\n")
                                    arpOutput.writeBytes("exit\n")
                                    arpOutput.flush()
                                    arpOutput.close()
                                    
                                    val arpReader = BufferedReader(InputStreamReader(arpProcess.inputStream))
                                    var line: String?
                                    while (arpReader.readLine().also { line = it } != null) {
                                        val parts = line?.trim()?.split(Regex("\\s+"))
                                        if (parts != null && parts.size >= 5) {
                                            val mac = parts[4]
                                            if (mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})$"))) {
                                                addDeviceToList(devices, ip, mac, "wlan0", null)
                                                Log.d(TAG, "Found device (native): $ip ($mac)")
                                                break
                                            }
                                        }
                                    }
                                    arpReader.close()
                                    arpProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                                    arpProcess.destroyForcibly()
                                } catch (e: Exception) {
                                    Log.d(TAG, "Could not get MAC for $ip: ${e.message}")
                                }
                            }
                            
                            if (devices.isNotEmpty()) {
                                Log.d(TAG, "Scan complete. Found ${devices.size} devices (native)")
                                return devices
                            }
                        }
                    }
                    
                    // Fallback to shell-based discovery if native didn't work
                    Log.d(TAG, "Native scan failed or returned no devices, using shell fallback...")
                    try {
                        val discoveryProcess = Runtime.getRuntime().exec("su")
                        val discoveryOutput = DataOutputStream(discoveryProcess.outputStream)
                        
                        // Method 1: Try nmap first (most comprehensive)
                        discoveryOutput.writeBytes("nmap -sn $subnet.0/24 > /dev/null 2>&1 || true\n")
                        
                        // Method 2: Try arp-scan (comprehensive ARP scanning)
                        discoveryOutput.writeBytes("arp-scan -l 2>/dev/null || true\n")
                        
                        // Method 3: Parallel TCP connections to common ports (triggers ARP from firewalled devices)
                        // This is more reliable than ping for devices that block ICMP
                        discoveryOutput.writeBytes("for i in {1..254}; do (timeout 0.1 bash -c \"</dev/tcp/$subnet.\$i/22\" || timeout 0.1 bash -c \"</dev/tcp/$subnet.\$i/80\" || timeout 0.1 bash -c \"</dev/tcp/$subnet.\$i/443\") > /dev/null 2>&1 & done\n")
                        discoveryOutput.writeBytes("wait\n")
                        
                        // Method 4: Use ip route get to trigger ARP lookups (forces kernel to do ARP)
                        discoveryOutput.writeBytes("for i in {1..254}; do ip route get $subnet.\$i > /dev/null 2>&1 & done\n")
                        discoveryOutput.writeBytes("wait\n")
                        
                        // Method 5: Broadcast ping
                        discoveryOutput.writeBytes("ping -b -c 1 -W 1 $subnet.255 > /dev/null 2>&1 || true\n")
                        
                        discoveryOutput.writeBytes("sleep 2\n")
                        discoveryOutput.writeBytes("exit\n")
                        discoveryOutput.flush()
                        discoveryOutput.close()
                        
                        val completed = discoveryProcess.waitFor(25, java.util.concurrent.TimeUnit.SECONDS)
                        if (!completed) {
                            discoveryProcess.destroyForcibly()
                        }
                        Log.d(TAG, "Network discovery completed")
                    } catch (e: Exception) {
                        Log.d(TAG, "Network discovery failed: ${e.message}")
                    }

                    // Now read ARP table after discovery - try multiple methods
                    Log.d(TAG, "Reading ARP table...")
                    val seenMacs = mutableSetOf<String>()  // Track seen MACs to avoid duplicates
                    
                    // Method 1: Try 'ip neigh show' first (more comprehensive)
                    try {
                        val arpProcess = Runtime.getRuntime().exec("su")
                        val arpOutput = DataOutputStream(arpProcess.outputStream)
                        
                        arpOutput.writeBytes("ip neigh show\n")
                        arpOutput.writeBytes("exit\n")
                        arpOutput.flush()
                        arpOutput.close()
                        
                        val arpReader = BufferedReader(InputStreamReader(arpProcess.inputStream))
                        var line: String?
                        
                        while (arpReader.readLine().also { line = it } != null) {
                            Log.d(TAG, "ARP entry (ip neigh): $line")
                            
                            // Parse 'ip neigh' output format: "192.168.29.1 dev wlan0 lladdr b4:8c:9d:8c:ef:09 REACHABLE"
                            val parts = line?.trim()?.split(Regex("\\s+"))
                            
                            if (parts != null && parts.size >= 5) {
                                val ip = parts[0]
                                val mac = parts.getOrNull(4)  // lladdr value
                                val state = parts.getOrNull(5)  // State (REACHABLE, STALE, etc.)
                                val deviceInterface = parts.getOrNull(2)  // dev value
                                
                                Log.d(TAG, "Parsed (ip neigh): IP=$ip, MAC=$mac, State=$state, Device=$deviceInterface")
                                
                                // Only accept IPv4 addresses (skip IPv6 like fe80:: or 2405:)
                                val isIPv4 = ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))
                                
                                // Accept devices with valid MAC addresses, IPv4 only, and not already seen
                                if (isIPv4 && mac != null && mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})$")) && 
                                    mac != "00:00:00:00:00:00" && mac != "<incomplete>" && !seenMacs.contains(mac)) {
                                    
                                    seenMacs.add(mac)
                                    addDeviceToList(devices, ip, mac, deviceInterface, null)
                                    Log.d(TAG, "Found device: $ip ($mac) (state=$state)")
                                }
                            }
                        }
                        
                        arpReader.close()
                        val completed = arpProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                        if (!completed) {
                            arpProcess.destroyForcibly()
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "ip neigh show failed: ${e.message}")
                    }
                    
                    // Method 2: Fallback to /proc/net/arp (catches devices ip neigh might miss)
                    try {
                        Log.d(TAG, "Reading /proc/net/arp as fallback...")
                        val procProcess = Runtime.getRuntime().exec("su")
                        val procOutput = DataOutputStream(procProcess.outputStream)
                        
                        procOutput.writeBytes("cat /proc/net/arp\n")
                        procOutput.writeBytes("exit\n")
                        procOutput.flush()
                        procOutput.close()
                        
                        val procReader = BufferedReader(InputStreamReader(procProcess.inputStream))
                        var line: String?
                        
                        while (procReader.readLine().also { line = it } != null) {
                            if (line == null) continue
                            
                            Log.d(TAG, "ARP line (/proc): $line")
                            
                            // Skip header line
                            if (line!!.startsWith("IP address")) continue
                            
                            // Parse /proc/net/arp format: "192.168.29.1 0x1 0x2 b4:8c:9d:8c:ef:09 * wlan0"
                            // When split: [IP, HWtype, Flags, MAC, Mask, Device]
                            val parts = line!!.trim().split(Regex("\\s+"))
                            
                            if (parts.size >= 6) {
                                val ip = parts[0]
                                val mac = parts[3]  // MAC is at index 3, not 4
                                val deviceInterface = parts.getOrNull(5) ?: "unknown"
                                
                                Log.d(TAG, "Parsed (/proc): IP=$ip, MAC=$mac, Device=$deviceInterface")
                                
                                // Only accept IPv4 addresses
                                val isIPv4 = ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))
                                
                                // Accept devices with valid MAC addresses, IPv4 only, and not already seen
                                if (isIPv4 && mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})$")) && 
                                    mac != "00:00:00:00:00:00" && !seenMacs.contains(mac)) {
                                    
                                    seenMacs.add(mac)
                                    addDeviceToList(devices, ip, mac, deviceInterface, null)
                                    Log.d(TAG, "Found device (from /proc): $ip ($mac)")
                                }
                            }
                        }
                        
                        procReader.close()
                        val completed = procProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                        if (!completed) {
                            procProcess.destroyForcibly()
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "/proc/net/arp read failed: ${e.message}")
                    }
                    
                    Log.d(TAG, "Scan complete. Found ${devices.size} devices")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning network: ${e.message}", e)
        }

        return devices
    }

    private fun identifyVendor(macAddress: String): String? {
        return VendorLookup.getVendor(macAddress)
    }

    private fun addDeviceToList(
        devices: MutableList<NetworkDevice>,
        ip: String,
        mac: String,
        deviceInterface: String?,
        hostname: String?
    ) {
        var resolvedHostname = hostname
        
        // Try to resolve hostname if not provided
        if (resolvedHostname == null) {
            try {
                val hostnameProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", "nslookup $ip"))
                val hostnameReader = BufferedReader(InputStreamReader(hostnameProcess.inputStream))
                var hostnameLine: String?
                while (hostnameReader.readLine().also { hostnameLine = it } != null) {
                    if (hostnameLine!!.contains("name = ")) {
                        val nameMatch = HOSTNAME_PATTERN.find(hostnameLine!!)
                        if (nameMatch != null) {
                            resolvedHostname = nameMatch.groupValues[1].trimEnd('.')
                            break
                        }
                    }
                }
                hostnameReader.close()
                hostnameProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                hostnameProcess.destroyForcibly()
            } catch (e: Exception) {
                Log.d(TAG, "Could not resolve hostname for $ip: ${e.message}")
            }
        }

        val vendor = identifyVendor(mac)
        val deviceType = identifyDeviceType(NetworkDevice(ip, mac, resolvedHostname, vendor, "Unknown"))

        val networkDevice = NetworkDevice(
            ipAddress = ip,
            macAddress = mac,
            hostname = resolvedHostname,
            vendor = vendor,
            deviceType = deviceType,
            hwType = "Unknown",
            mask = "*",
            deviceInterface = deviceInterface ?: "unknown"
        )
        devices.add(networkDevice)
    }

    private fun identifyDeviceType(device: NetworkDevice): String? {
        val vendor = device.vendor ?: identifyVendor(device.macAddress)

        return when {
            vendor?.contains("Apple", ignoreCase = true) == true -> "iPhone/iPad"
            vendor?.contains("Samsung", ignoreCase = true) == true -> "Samsung Phone/Tablet"
            vendor?.contains("Intel", ignoreCase = true) == true -> "Computer"
            vendor?.contains("Dell", ignoreCase = true) == true -> "Dell Computer"
            vendor?.contains("HP", ignoreCase = true) == true ||
            vendor?.contains("Hewlett", ignoreCase = true) == true -> "HP Computer/Printer"
            vendor?.contains("Raspberry", ignoreCase = true) == true -> "Raspberry Pi"
            vendor?.contains("TP-Link", ignoreCase = true) == true ||
            vendor?.contains("Ubiquiti", ignoreCase = true) == true -> "Router/Network Equipment"
            vendor?.contains("Realtek", ignoreCase = true) == true -> "Network Device"
            vendor?.contains("Mediatek", ignoreCase = true) == true -> "Mobile Device"
            vendor?.contains("AzureWave", ignoreCase = true) == true -> "WiFi Module/Adapter"
            vendor?.contains("Broadcom", ignoreCase = true) == true -> "WiFi Module"
            device.hwType?.contains("WiFi", ignoreCase = true) == true -> "Wireless Device"
            device.hwType?.contains("Ethernet", ignoreCase = true) == true -> "Wired Device"
            else -> null
        }
    }

    override suspend fun blockDevice(device: NetworkDevice): NetworkResult<Boolean> {
        return try {
            val isRootedResult = isDeviceRooted()
            if (isRootedResult is NetworkResult.Error) {
                return isRootedResult as NetworkResult<Boolean>
            }

            if (!(isRootedResult as NetworkResult.Success).data) {
                Log.e(TAG, "Cannot block device: Device is not rooted")
                return NetworkResult.error(NetworkError.DeviceNotRootedError())
            }

            val gatewayIp = getGatewayIp()
            if (gatewayIp.isNullOrEmpty()) {
                Log.e(TAG, "Could not determine gateway IP for ARP spoofing")
                return NetworkResult.error(NetworkError.NetworkAccessError())
            }

            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)

            outputStream.writeBytes("arping -U -c 1 -s $gatewayIp ${device.ipAddress}\n")
            outputStream.writeBytes("arping -U -c 1 -s ${device.ipAddress} $gatewayIp\n")

            val ourMac = getOurMacAddress()
            if (!ourMac.isNullOrEmpty()) {
                outputStream.writeBytes("arp -s ${device.ipAddress} $ourMac\n")
            }

            outputStream.writeBytes("exit\n")
            outputStream.flush()

            process.waitFor()
            Log.i(TAG, "Successfully initiated ARP spoofing to block device: ${device.ipAddress}")
            NetworkResult.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking device ${device.ipAddress}: ${e.message}", e)
            NetworkResult.error(NetworkError.BlockDeviceError(e))
        }
    }

    override suspend fun unblockDevice(device: NetworkDevice): NetworkResult<Boolean> {
        return try {
            val isRootedResult = isDeviceRooted()
            if (isRootedResult is NetworkResult.Error) {
                return isRootedResult as NetworkResult<Boolean>
            }

            if (!(isRootedResult as NetworkResult.Success).data) {
                Log.e(TAG, "Cannot unblock device: Device is not rooted")
                return NetworkResult.error(NetworkError.DeviceNotRootedError())
            }

            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)

            outputStream.writeBytes("arp -d ${device.ipAddress}\n")
            outputStream.writeBytes("ping -c 1 -W 1 ${device.ipAddress}\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            process.waitFor()
            Log.i(TAG, "Successfully unblocked device: ${device.ipAddress}")
            NetworkResult.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error unblocking device ${device.ipAddress}: ${e.message}", e)
            NetworkResult.error(NetworkError.UnblockDeviceError(e))
        }
    }

    override suspend fun mapNetworkTopology(): NetworkResult<NetworkTopology> {
        return try {
            val scanResult = scanNetwork()
            if (scanResult is NetworkResult.Error) {
                return NetworkResult.error(scanResult.error)
            }

            val devices = (scanResult as NetworkResult.Success).data
            val gatewayIp = getGatewayIp()

            val gatewayDevice = devices.find { it.ipAddress == gatewayIp }

            val devicesByType = mutableMapOf<String, MutableList<NetworkDevice>>()
            val unknownDevices = mutableListOf<NetworkDevice>()

            devices.forEach { device ->
                if (device.deviceType != null) {
                    if (!devicesByType.containsKey(device.deviceType)) {
                        devicesByType[device.deviceType] = mutableListOf()
                    }
                    devicesByType[device.deviceType]?.add(device)
                } else {
                    unknownDevices.add(device)
                }
            }

            NetworkResult.success(
                NetworkTopology(
                    gatewayDevice,
                    devices,
                    devicesByType,
                    unknownDevices
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mapping network topology: ${e.message}", e)
            NetworkResult.error(NetworkError.NetworkScanError(e))
        }
    }

    private fun getGatewayIp(): String? {
        return try {
            val process = Runtime.getRuntime().exec("su -c \"ip route | grep default | awk '{print \$3}'\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val gatewayIp = reader.readLine()
            reader.close()
            process.waitFor()

            gatewayIp?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting gateway IP: ${e.message}", e)
            null
        }
    }

    private fun getOurMacAddress(): String? {
        return try {
            val process = Runtime.getRuntime().exec("su -c \"cat /sys/class/net/wlan0/address\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val macAddress = reader.readLine()
            reader.close()
            process.waitFor()

            if (!macAddress.isNullOrEmpty() && macAddress != "00:00:00:00:00:00") {
                macAddress.trim()
            } else {
                val interfaces = arrayOf("wlan0", "wlan1", "eth0", "rndis0")
                for (iface in interfaces) {
                    val altProcess = Runtime.getRuntime().exec("su -c \"cat /sys/class/net/$iface/address\"")
                    val altReader = BufferedReader(InputStreamReader(altProcess.inputStream))
                    val altMac = altReader.readLine()
                    altReader.close()
                    altProcess.waitFor()

                    if (!altMac.isNullOrEmpty() && altMac != "00:00:00:00:00:00") {
                        return altMac.trim()
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MAC address: ${e.message}", e)
            null
        }
    }
}