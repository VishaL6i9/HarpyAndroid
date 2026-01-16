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

class NetworkMonitorRepositoryImpl(private val context: android.content.Context) : NetworkMonitorRepository {

    companion object {
        private const val TAG = "NetworkMonitorRepoImpl"
        
        // Cache regex patterns for better performance
        private val SUBNET_PATTERN = Regex("""([0-9]+\.[0-9]+\.[0-9]+)\.0""")
        private val HOSTNAME_PATTERN = Regex("name = (.+)")
        private val FIELDS_SPLIT_PATTERN = Regex("\\s+")
    }

    private val blockingProcesses = java.util.concurrent.ConcurrentHashMap<String, Process>()

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

                    // Try robust root helper binary first
                    Log.d(TAG, "Attempting robust root helper scan...")
                    val helperPath = NativeNetworkWrapper.getRootHelperPath(context)
                    
                    if (helperPath != null) {
                        try {
                            val helperProcess = Runtime.getRuntime().exec("su")
                            val helperOutput = DataOutputStream(helperProcess.outputStream)
                            
                            // Ensure executable permission and run with proper library path
                            val libDir = context.applicationInfo.nativeLibraryDir
                            val cmd = "chmod 755 $helperPath && LD_LIBRARY_PATH=$libDir $helperPath scan wlan0 $subnet 2>&1\n"
                            Log.d(TAG, "Executing root helper: $cmd")
                            helperOutput.writeBytes(cmd)
                            helperOutput.writeBytes("exit\n")
                            helperOutput.flush()
                            helperOutput.close()

                            val helperReader = BufferedReader(InputStreamReader(helperProcess.inputStream))
                            var helperLine: String?
                            val helperFoundDevices = mutableListOf<String>()
                            while (helperReader.readLine().also { helperLine = it } != null) {
                                Log.d(TAG, "Root helper output: $helperLine")
                                if (helperLine != null && helperLine!!.contains("|") && 
                                    !helperLine!!.startsWith("DEBUG:") && !helperLine!!.startsWith("INFO:")) {
                                    helperFoundDevices.add(helperLine!!)
                                }
                            }
                            helperReader.close()
                            
                            helperProcess.waitFor()
                            
                            if (helperFoundDevices.isNotEmpty()) {
                                Log.d(TAG, "Root helper found ${helperFoundDevices.size} devices")
                                for (deviceStr in helperFoundDevices) {
                                    val parts = deviceStr.split("|")
                                    if (parts.size == 2) {
                                        val ip = parts[0]
                                        val mac = parts[1]
                                        addDeviceToList(devices, ip, mac, "wlan0", null)
                                        Log.d(TAG, "Root helper found: $ip ($mac)")
                                    }
                                }
                                
                                if (devices.isNotEmpty()) {
                                    Log.d(TAG, "Scan complete. Found ${devices.size} devices (root helper)")
                                    return devices
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Root helper scan failed: ${e.message}")
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
            deviceInterface = deviceInterface ?: "unknown",
            isCurrentDevice = (ip == getOurIp())
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
            if (!(isRootedResult is NetworkResult.Success && isRootedResult.data)) {
                return NetworkResult.error(NetworkError.DeviceNotRootedError())
            }

            // Check if already blocking
            if (blockingProcesses.containsKey(device.ipAddress)) {
                return NetworkResult.success(true)
            }

            val gatewayIp = getGatewayIp() ?: return NetworkResult.error(NetworkError.NetworkAccessError())
            val helperPath = NativeNetworkWrapper.getRootHelperPath(context) ?: return NetworkResult.error(NetworkError.NativeLibraryError())
            val ourMac = getOurMacAddress() ?: "00:00:00:00:00:00" // Should ideally get this from route
            val iface = "wlan0" // Should ideally get this from route

            // Start blocking process in background
            val libDir = context.applicationInfo.nativeLibraryDir
            val cmd = "chmod 755 $helperPath && LD_LIBRARY_PATH=$libDir $helperPath block $iface ${device.ipAddress} $gatewayIp $ourMac\n"
            
            Log.d(TAG, "Starting blocking process: $cmd")
            val process = Runtime.getRuntime().exec("su")
            val out = DataOutputStream(process.outputStream)
            out.writeBytes(cmd)
            out.flush()
            // Keep output stream open or the process might exit depending on su implementation
            
            blockingProcesses[device.ipAddress] = process
            
            Log.i(TAG, "Persistent blocking started for ${device.ipAddress}")
            NetworkResult.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking device ${device.ipAddress}: ${e.message}", e)
            NetworkResult.error(NetworkError.BlockDeviceError(e))
        }
    }

    override suspend fun unblockDevice(device: NetworkDevice): NetworkResult<Boolean> {
        return try {
            val process = blockingProcesses.remove(device.ipAddress)
            if (process != null) {
                // Try to kill the root process
                val killer = Runtime.getRuntime().exec("su")
                val out = DataOutputStream(killer.outputStream)
                // We need to kill the specific helper process. Since su might spawn it in a new session,
                // we'll try to kill it by name if possible, or just destroy the process we have.
                // However, killing the 'su' child might not be enough.
                out.writeBytes("pkill -f libharpy_root_helper.so\n")
                out.writeBytes("exit\n")
                out.flush()
                out.close()
                killer.waitFor()
                
                process.destroy()
                Log.i(TAG, "Blocking stopped for ${device.ipAddress}")
            }
            NetworkResult.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error unblocking device ${device.ipAddress}: ${e.message}", e)
            NetworkResult.error(NetworkError.BlockDeviceError(e))
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

    override suspend fun testPing(device: NetworkDevice): NetworkResult<Boolean> {
        return try {
            // First try ICMP Ping
            val pingProcess = Runtime.getRuntime().exec("ping -c 1 -W 1 ${device.ipAddress}")
            val exitCode = pingProcess.waitFor()
            
            if (exitCode == 0) {
                Log.d(TAG, "Ping successful for ${device.ipAddress}")
                return NetworkResult.success(true)
            }

            // If ping fails, check ARP table as a fallback (some devices block ICMP but are active)
            Log.d(TAG, "Ping failed for ${device.ipAddress} (exit $exitCode), checking ARP table...")
            
            val arpProcess = Runtime.getRuntime().exec("su -c \"ip neigh show ${device.ipAddress}\"")
            val reader = BufferedReader(InputStreamReader(arpProcess.inputStream))
            val line = reader.readLine()
            reader.close()
            arpProcess.waitFor()

            if (line != null && (line.contains("REACHABLE") || line.contains("DELAY") || line.contains("PROBE"))) {
                Log.d(TAG, "Device ${device.ipAddress} is found in ARP table state: $line")
                NetworkResult.success(true)
            } else {
                Log.d(TAG, "Device ${device.ipAddress} appears to be offline.")
                NetworkResult.success(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error testing ping for ${device.ipAddress}: ${e.message}", e)
            NetworkResult.error(NetworkError.CommandExecutionError(e))
        }
    }

    private fun getOurIp(): String? {
        return try {
            val process = Runtime.getRuntime().exec("su -c \"ip route | grep src | awk '{print \$NF}'\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val ourIp = reader.readLine()
            reader.close()
            process.waitFor()
            ourIp?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting our IP: ${e.message}", e)
            null
        }
    }
}
