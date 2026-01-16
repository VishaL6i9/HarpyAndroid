package com.vishal.harpy

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import kotlin.text.Regex

/**
 * Service to handle network monitoring and device management
 * This service will scan the local network, identify connected devices,
 * and provide functionality to block/unblock devices using root access
 */
class NetworkMonitorService {
    
    companion object {
        private const val TAG = "NetworkMonitorService"
    }
    
    /**
     * Scan the local network for connected devices
     * @return List of NetworkDevice objects representing connected devices
     */
    fun scanNetwork(): List<NetworkDevice> {
        val devices = mutableListOf<NetworkDevice>()
        
        // This is a placeholder implementation
        // Actual implementation would use ARP scanning or similar techniques
        Log.d(TAG, "Scanning network for connected devices...")
        
        // Check if device is rooted
        if (isDeviceRooted()) {
            // Perform network scan using root commands
            devices.addAll(scanNetworkWithRoot())
        } else {
            Log.w(TAG, "Device is not rooted. Limited functionality available.")
            // Fallback to non-root network information if needed
        }
        
        return devices
    }
    
    /**
     * Check if the device has root access
     * @return true if device is rooted, false otherwise
     */
    fun isDeviceRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            // Check if we got root access by attempting a simple root command
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

            // If the command executed successfully and we got a response,
            // we likely have root access
            response != null && response.contains("uid=0")
        } catch (e: IOException) {
            Log.d(TAG, "Device is not rooted: ${e.message}")
            false
        } catch (e: InterruptedException) {
            Log.d(TAG, "Root check interrupted: ${e.message}")
            false
        }
    }
    
    /**
     * Perform network scan using root commands
     * @return List of NetworkDevice objects
     */
    private fun scanNetworkWithRoot(): List<NetworkDevice> {
        val devices = mutableListOf<NetworkDevice>()

        try {
            // First, get the current network interface information
            val ipProcess = Runtime.getRuntime().exec("su -c \"ip route | grep -E '^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+' | head -n 1\"")
            val ipReader = BufferedReader(InputStreamReader(ipProcess.inputStream))
            val routeLine = ipReader.readLine()
            ipReader.close()
            ipProcess.waitFor()

            if (routeLine != null) {
                // Extract the subnet from the route line
                val subnetPattern = Regex("""([0-9]+\.[0-9]+\.[0-9]+)\.0""")
                val matchResult = subnetPattern.find(routeLine)

                if (matchResult != null) {
                    val subnet = matchResult.groupValues[1]

                    // Ping sweep to populate ARP table
                    for (i in 1..254) {
                        val pingProcess = Runtime.getRuntime().exec("su -c \"ping -c 1 -W 1 ${subnet}.${i}\"")
                        pingProcess.waitFor()
                    }

                    // Now read the ARP table to get connected devices
                    val arpProcess = Runtime.getRuntime().exec("su -c \"cat /proc/net/arp\"")
                    val arpReader = BufferedReader(InputStreamReader(arpProcess.inputStream))

                    var line: String?
                    while (arpReader.readLine().also { line = it } != null) {
                        val fields = line?.split("\\s+".toRegex())
                        if (fields != null && fields.size >= 6) {
                            val ip = fields[0]  // IP address
                            val hwType = fields[1]  // Hardware type
                            val flags = fields[2]  // Flags
                            val mac = fields[3]  // MAC address
                            val mask = fields[4]  // Mask
                            val deviceInterface = fields[5]  // Device name

                            // Only add entries that are marked as reachable (flags contain 0x2)
                            if (flags.contains("0x2") || flags.contains("0x6")) {
                                if (mac != "00:00:00:00:00:00" && mac != "<incomplete>") {
                                    // Attempt to get hostname (this might not always work)
                                    var hostname: String? = null
                                    try {
                                        val hostnameProcess = Runtime.getRuntime().exec("su -c \"nslookup $ip\"")
                                        val hostnameReader = BufferedReader(InputStreamReader(hostnameProcess.inputStream))
                                        var hostnameLine: String?
                                        while (hostnameReader.readLine().also { hostnameLine = it } != null) {
                                            if (hostnameLine!!.contains("name = ")) {
                                                val nameMatch = Regex("name = (.+)").find(hostnameLine!!)
                                                if (nameMatch != null) {
                                                    hostname = nameMatch.groupValues[1].trimEnd('.')
                                                    break
                                                }
                                            }
                                        }
                                        hostnameReader.close()
                                        hostnameProcess.waitFor()
                                    } catch (e: Exception) {
                                        Log.d(TAG, "Could not resolve hostname for $ip: ${e.message}")
                                    }

                                    // Determine device type based on hardware type
                                    val hwTypeDescription = when(hwType.lowercase()) {
                                        "0x1" -> "Ethernet"
                                        "0x19" -> "WiFi"
                                        "0x420" -> "Bridge"
                                        else -> "Unknown"
                                    }

                                    val vendor = identifyVendor(mac)
                                    val deviceType = identifyDeviceType(NetworkDevice(ip, mac, hostname, vendor, hwTypeDescription))

                                    // Create the device with all available information
                                    val networkDevice = NetworkDevice(
                                        ipAddress = ip,
                                        macAddress = mac,
                                        hostname = hostname,
                                        vendor = vendor,
                                        deviceType = deviceType,
                                        hwType = hwTypeDescription,
                                        mask = mask,
                                        deviceInterface = deviceInterface
                                    )
                                    devices.add(networkDevice)
                                }
                            }
                        }
                    }

                    arpReader.close()
                    arpProcess.waitFor()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning network: ${e.message}", e)
        }

        return devices
    }

    /**
     * Identify device vendor based on MAC address OUI (Organizationally Unique Identifier)
     * @param macAddress The MAC address to identify
     * @return Vendor name if found, null otherwise
     */
    private fun identifyVendor(macAddress: String): String? {
        // This is a simplified vendor identification based on common prefixes
        // In a real implementation, this would use a comprehensive OUI database
        val oui = macAddress.substring(0, 8).uppercase()

        return when (oui) {
            "00:50:43" -> "Siemens"
            "00:50:C2" -> "IEEE Registration Authority"
            "00:60:2F" -> "Hewlett Packard"
            "00:A0:C9" -> "Intel Corporation"
            "00:E0:4C" -> "Realtek Semiconductor Corp."
            "08:00:27" -> "Oracle VirtualBox"
            "1C:69:7A" -> "AcSiP Technology Corp."
            "24:4B:03" -> "Samsung Electronics Co., Ltd"
            "28:C6:3F" -> "Apple, Inc."
            "38:4F:F0" -> "Samsung Electronics Co., Ltd"
            "40:B0:FA" -> "LG Electronics (Mobile Communications)"
            "44:D9:E7" -> "Ubiquiti Networks Inc."
            "5C:F9:DD" -> "Dell Inc."
            "6C:EC:5A" -> "Hon Hai Precision Ind. Co.,Ltd."
            "78:4F:43" -> "Apple, Inc."
            "80:A5:89" -> "AzureWave Technology Inc."
            "8C:1F:64" -> "Intel Corporate"
            "9C:93:4E" -> "ASUSTek Computer, Inc."
            "AC:DE:48" -> "Intel Corporate"
            "B8:27:EB" -> "Raspberry Pi Foundation"
            "BC:5F:F4" -> "Dell Inc."
            "C8:60:00" -> "Apple, Inc."
            "D8:3B:BF" -> "Samsung Electronics Co., Ltd"
            "DC:A6:32" -> "Raspberry Pi Trading Ltd"
            "E4:5D:52" -> "Intel Corporate"
            "EC:26:CA" -> "TP-Link Technologies Co., Ltd."
            "F0:18:98" -> "Apple, Inc."
            "F4:8C:50" -> "Intel Corporate"
            else -> null
        }
    }

    /**
     * Identify device type based on various heuristics
     * @param device The network device to identify
     * @return Device type if identified, null otherwise
     */
    private fun identifyDeviceType(device: NetworkDevice): String? {
        // Identify device type based on vendor, IP range, or other heuristics
        val vendor = device.vendor ?: identifyVendor(device.macAddress)

        // Common heuristics for device type identification
        return when {
            vendor?.contains("Apple", ignoreCase = true) == true -> "Phone/Tablet"
            vendor?.contains("Samsung", ignoreCase = true) == true -> "Phone/Tablet"
            vendor?.contains("Intel", ignoreCase = true) == true -> "Computer"
            vendor?.contains("Dell", ignoreCase = true) == true -> "Computer"
            vendor?.contains("HP", ignoreCase = true) == true ||
            vendor?.contains("Hewlett", ignoreCase = true) == true -> "Computer/Printer"
            vendor?.contains("Raspberry", ignoreCase = true) == true -> "IoT/Single-board computer"
            vendor?.contains("TP-Link", ignoreCase = true) == true ||
            vendor?.contains("Ubiquiti", ignoreCase = true) == true ||
            vendor?.contains("Realtek", ignoreCase = true) == true -> "Router/Network equipment"
            device.hwType?.contains("WiFi", ignoreCase = true) == true -> "Wireless Device"
            device.hwType?.contains("Ethernet", ignoreCase = true) == true -> "Wired Device"
            else -> null
        }
    }

    /**
     * Block a specific device from the network using ARP spoofing
     * @param device The device to block
     * @return true if blocking was successful, false otherwise
     */
    fun blockDevice(device: NetworkDevice): Boolean {
        if (!isDeviceRooted()) {
            Log.e(TAG, "Cannot block device: Device is not rooted")
            return false
        }

        return try {
            // Get the gateway IP to perform ARP spoofing between the target device and gateway
            val gatewayIp = getGatewayIp()
            if (gatewayIp.isNullOrEmpty()) {
                Log.e(TAG, "Could not determine gateway IP for ARP spoofing")
                return false
            }

            // Perform ARP spoofing to redirect traffic between target device and gateway
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)

            // Send fake ARP replies to the target device, claiming we are the gateway
            outputStream.writeBytes("arping -U -c 1 -s $gatewayIp ${device.ipAddress}\n")

            // Also send fake ARP replies to the gateway, claiming we are the target device
            outputStream.writeBytes("arping -U -c 1 -s ${device.ipAddress} $gatewayIp\n")

            // Alternatively, we can poison the ARP tables directly using arptables or similar
            // This is a more aggressive approach that associates our MAC with the target's IP
            val ourMac = getOurMacAddress()
            if (!ourMac.isNullOrEmpty()) {
                outputStream.writeBytes("arp -s ${device.ipAddress} $ourMac\n")
            }

            outputStream.writeBytes("exit\n")
            outputStream.flush()

            process.waitFor()
            Log.i(TAG, "Successfully initiated ARP spoofing to block device: ${device.ipAddress}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking device ${device.ipAddress}: ${e.message}", e)
            false
        }
    }

    /**
     * Get our own device's MAC address
     * @return MAC address as a string, or null if unable to determine
     */
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
                // Try alternative interfaces
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

    /**
     * Map the network topology to understand device relationships
     * @return NetworkTopology object containing network structure information
     */
    fun mapNetworkTopology(): NetworkTopology {
        val devices = scanNetwork()
        val gatewayIp = getGatewayIp()

        // Identify the gateway device
        val gatewayDevice = devices.find { it.ipAddress == gatewayIp }

        // Group devices by type
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

        return NetworkTopology(
            gatewayDevice,
            devices,
            devicesByType,
            unknownDevices
        )
    }

    /**
     * Get the gateway IP address of the current network
     * @return Gateway IP address as a string
     */
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
    
    // Variables for managing continuous scanning
    private var isScanning = false
    private var scanInterval: Long = 30000 // 30 seconds default
    private var scanHandler: Handler? = null
    private var scanRunnable: Runnable? = null

    /**
     * Starts continuous network scanning
     * @param intervalMs Interval between scans in milliseconds
     * @return true if scanning started successfully, false otherwise
     */
    fun startScanning(intervalMs: Long = 30000): Boolean {
        if (!isDeviceRooted()) {
            Log.e(TAG, "Cannot start scanning: Device is not rooted")
            return false
        }

        if (isScanning) {
            Log.w(TAG, "Scanning is already running")
            return false
        }

        isScanning = true
        scanInterval = intervalMs
        scanHandler = Handler(Looper.getMainLooper())

        scanRunnable = object : Runnable {
            override fun run() {
                if (isScanning) {
                    Log.d(TAG, "Performing network scan...")
                    val devices = scanNetwork()
                    onDevicesScanned(devices)

                    // Schedule next scan
                    scanHandler?.postDelayed(this, scanInterval)
                }
            }
        }

        // Start the first scan
        scanRunnable?.run()
        Log.i(TAG, "Started continuous network scanning with interval: ${intervalMs}ms")
        return true
    }

    /**
     * Stops continuous network scanning
     */
    fun stopScanning() {
        isScanning = false
        scanHandler?.removeCallbacks(scanRunnable ?: Runnable {})
        Log.i(TAG, "Stopped continuous network scanning")
    }

    /**
     * Checks if continuous scanning is currently active
     * @return true if scanning is active, false otherwise
     */
    fun isScanning(): Boolean {
        return isScanning
    }

    /**
     * Callback function when devices are scanned
     * This can be overridden or connected to listeners to handle scan results
     */
    private fun onDevicesScanned(devices: List<NetworkDevice>) {
        Log.d(TAG, "Found ${devices.size} devices in network scan")
        // This is where we would notify listeners about the scan results
        // In a real implementation, this would trigger UI updates or notifications
    }

    /**
     * Unblocks a previously blocked device
     * @param device The device to unblock
     * @return true if unblocking was successful, false otherwise
     */
    fun unblockDevice(device: NetworkDevice): Boolean {
        if (!isDeviceRooted()) {
            Log.e(TAG, "Cannot unblock device: Device is not rooted")
            return false
        }

        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)

            // Clear the poisoned ARP entry for the device
            outputStream.writeBytes("arp -d ${device.ipAddress}\n")

            // Optionally, refresh the ARP table for the device by pinging it
            outputStream.writeBytes("ping -c 1 -W 1 ${device.ipAddress}\n")

            outputStream.writeBytes("exit\n")
            outputStream.flush()

            process.waitFor()
            Log.i(TAG, "Successfully unblocked device: ${device.ipAddress}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unblocking device ${device.ipAddress}: ${e.message}", e)
            false
        }
    }

    /**
     * Analyzes network traffic for a specific device
     * @param device The device to analyze traffic for
     * @return NetworkTrafficStats object containing traffic analysis
     */
    fun analyzeTrafficForDevice(device: NetworkDevice): NetworkTrafficStats {
        return try {
            // Get network statistics from /proc/net/dev
            val process = Runtime.getRuntime().exec("su -c \"cat /proc/net/dev\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            var receivedBytes = 0L
            var transmittedBytes = 0L
            var receivedPackets = 0L
            var transmittedPackets = 0L

            while (reader.readLine().also { line = it } != null) {
                val trimmedLine = line!!.trim()

                // Skip header lines
                if (trimmedLine.startsWith("Inter-") || trimmedLine.startsWith("face")) {
                    continue
                }

                // Parse interface statistics
                val parts = trimmedLine.split("\\s+".toRegex())
                if (parts.size >= 10) {
                    val interfaceName = parts[0].removeSuffix(":")

                    // Only analyze traffic on active network interfaces
                    if (interfaceName.startsWith("wlan") || interfaceName.startsWith("eth") || interfaceName.startsWith("rmnet")) {
                        val rxBytes = parts[1].toLongOrNull() ?: 0L
                        val rxPackets = parts[2].toLongOrNull() ?: 0L
                        val txBytes = parts[9].toLongOrNull() ?: 0L
                        val txPackets = parts[10].toLongOrNull() ?: 0L

                        receivedBytes += rxBytes
                        receivedPackets += rxPackets
                        transmittedBytes += txBytes
                        transmittedPackets += txPackets
                    }
                }
            }

            reader.close()
            process.waitFor()

            // Get connection information for the specific device
            val connections = getConnectionInfoForDevice(device.ipAddress)

            NetworkTrafficStats(
                device = device,
                totalReceivedBytes = receivedBytes,
                totalTransmittedBytes = transmittedBytes,
                totalReceivedPackets = receivedPackets,
                totalTransmittedPackets = transmittedPackets,
                activeConnections = connections,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing traffic for device ${device.ipAddress}: ${e.message}", e)
            NetworkTrafficStats(
                device = device,
                totalReceivedBytes = 0,
                totalTransmittedBytes = 0,
                totalReceivedPackets = 0,
                totalTransmittedPackets = 0,
                activeConnections = emptyList(),
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Gets connection information for a specific device
     * @param ipAddress The IP address to get connections for
     * @return List of ConnectionInfo objects
     */
    private fun getConnectionInfoForDevice(ipAddress: String): List<ConnectionInfo> {
        val connections = mutableListOf<ConnectionInfo>()

        try {
            // Check TCP connections
            val tcpProcess = Runtime.getRuntime().exec("su -c \"cat /proc/net/tcp\"")
            val tcpReader = BufferedReader(InputStreamReader(tcpProcess.inputStream))

            var line: String?
            while (tcpReader.readLine().also { line = it } != null) {
                if (line!!.contains(ipAddress.replace(".", " ").replace(" ", ""))) {
                    // Parse TCP connection info
                    val parts = line!!.split("\\s+".toRegex())
                    if (parts.size >= 8) {
                        val localAddr = parseHexAddress(parts[1])
                        val remoteAddr = parseHexAddress(parts[2])
                        val state = parseTcpState(parts[3])

                        connections.add(ConnectionInfo(
                            localAddress = localAddr.first,
                            localPort = localAddr.second,
                            remoteAddress = remoteAddr.first,
                            remotePort = remoteAddr.second,
                            protocol = "TCP",
                            state = state,
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                }
            }

            tcpReader.close()
            tcpProcess.waitFor()

            // Check UDP connections
            val udpProcess = Runtime.getRuntime().exec("su -c \"cat /proc/net/udp\"")
            val udpReader = BufferedReader(InputStreamReader(udpProcess.inputStream))

            while (udpReader.readLine().also { line = it } != null) {
                if (line!!.contains(ipAddress.replace(".", " ").replace(" ", ""))) {
                    // Parse UDP connection info
                    val parts = line!!.split("\\s+".toRegex())
                    if (parts.size >= 7) {
                        val localAddr = parseHexAddress(parts[1])
                        val remoteAddr = parseHexAddress(parts[2]) // For UDP, this might be empty

                        connections.add(ConnectionInfo(
                            localAddress = localAddr.first,
                            localPort = localAddr.second,
                            remoteAddress = remoteAddr.first,
                            remotePort = remoteAddr.second,
                            protocol = "UDP",
                            state = "ESTABLISHED", // UDP is connectionless
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                }
            }

            udpReader.close()
            udpProcess.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connection info for device $ipAddress: ${e.message}", e)
        }

        return connections
    }

    /**
     * Parses hexadecimal address and port from /proc/net/tcp format
     * @param hexAddrPort Hexadecimal string in format "address:port"
     * @return Pair of IP address and port
     */
    private fun parseHexAddress(hexAddrPort: String): Pair<String, Int> {
        val parts = hexAddrPort.split(":")
        if (parts.size < 2) return Pair("", 0)

        // Parse IP address (reverse byte order)
        val hexIp = parts[0]
        if (hexIp.length != 8) return Pair("", 0)

        val ipParts = mutableListOf<String>()
        for (i in 0 until 8 step 2) {
            val hexByte = hexIp.substring(i, i + 2)
            val decimalByte = Integer.parseInt(hexByte, 16)
            ipParts.add(decimalByte.toString())
        }

        // Parse port
        val hexPort = parts[1]
        val port = Integer.parseInt(hexPort, 16)

        return Pair(ipParts.reversed().joinToString("."), port)
    }

    /**
     * Parses TCP state from hexadecimal value
     * @param hexState Hexadecimal state value
     * @return Human-readable state string
     */
    private fun parseTcpState(hexState: String): String {
        return when (hexState.lowercase()) {
            "01" -> "ESTABLISHED"
            "02" -> "SYN_SENT"
            "03" -> "SYN_RECV"
            "04" -> "FIN_WAIT1"
            "05" -> "FIN_WAIT2"
            "06" -> "TIME_WAIT"
            "07" -> "CLOSE"
            "08" -> "CLOSE_WAIT"
            "09" -> "LAST_ACK"
            "0a" -> "LISTEN"
            "0b" -> "CLOSING"
            else -> "UNKNOWN"
        }
    }

    // Properties to hold the blacklist and whitelist
    private val blacklist = mutableSetOf<String>() // IP addresses
    private val whitelist = mutableSetOf<String>() // IP addresses

    /**
     * Adds a device to the blacklist
     * @param device The device to add to blacklist
     * @return true if successfully added, false otherwise
     */
    fun addToBlacklist(device: NetworkDevice): Boolean {
        synchronized(blacklist) {
            val result = blacklist.add(device.ipAddress)
            // Remove from whitelist if it was there
            removeFromWhitelist(device)
            Log.d(TAG, "Added ${device.ipAddress} to blacklist")
            return result
        }
    }

    /**
     * Adds a device to the whitelist
     * @param device The device to add to whitelist
     * @return true if successfully added, false otherwise
     */
    fun addToWhitelist(device: NetworkDevice): Boolean {
        synchronized(whitelist) {
            val result = whitelist.add(device.ipAddress)
            // Remove from blacklist if it was there
            removeFromBlacklist(device)
            Log.d(TAG, "Added ${device.ipAddress} to whitelist")
            return result
        }
    }

    /**
     * Removes a device from the blacklist
     * @param device The device to remove from blacklist
     * @return true if successfully removed, false otherwise
     */
    fun removeFromBlacklist(device: NetworkDevice): Boolean {
        synchronized(blacklist) {
            val result = blacklist.remove(device.ipAddress)
            Log.d(TAG, "Removed ${device.ipAddress} from blacklist")
            return result
        }
    }

    /**
     * Removes a device from the whitelist
     * @param device The device to remove from whitelist
     * @return true if successfully removed, false otherwise
     */
    fun removeFromWhitelist(device: NetworkDevice): Boolean {
        synchronized(whitelist) {
            val result = whitelist.remove(device.ipAddress)
            Log.d(TAG, "Removed ${device.ipAddress} from whitelist")
            return result
        }
    }

    /**
     * Checks if a device is blacklisted
     * @param device The device to check
     * @return true if device is blacklisted, false otherwise
     */
    fun isBlacklisted(device: NetworkDevice): Boolean {
        synchronized(blacklist) {
            return device.ipAddress in blacklist
        }
    }

    /**
     * Checks if a device is whitelisted
     * @param device The device to check
     * @return true if device is whitelisted, false otherwise
     */
    fun isWhitelisted(device: NetworkDevice): Boolean {
        synchronized(whitelist) {
            return device.ipAddress in whitelist
        }
    }

    /**
     * Gets all blacklisted devices
     * @return List of blacklisted NetworkDevice objects
     */
    fun getBlacklistedDevices(allDevices: List<NetworkDevice>): List<NetworkDevice> {
        synchronized(blacklist) {
            return allDevices.filter { it.ipAddress in blacklist }
        }
    }

    /**
     * Gets all whitelisted devices
     * @return List of whitelisted NetworkDevice objects
     */
    fun getWhitelistedDevices(allDevices: List<NetworkDevice>): List<NetworkDevice> {
        synchronized(whitelist) {
            return allDevices.filter { it.ipAddress in whitelist }
        }
    }

    /**
     * Clears the blacklist
     */
    fun clearBlacklist() {
        synchronized(blacklist) {
            blacklist.clear()
            Log.d(TAG, "Blacklist cleared")
        }
    }

    /**
     * Clears the whitelist
     */
    fun clearWhitelist() {
        synchronized(whitelist) {
            whitelist.clear()
            Log.d(TAG, "Whitelist cleared")
        }
    }

    /**
     * Gets the count of blacklisted devices
     * @return Number of blacklisted devices
     */
    fun getBlacklistCount(): Int {
        synchronized(blacklist) {
            return blacklist.size
        }
    }

    /**
     * Gets the count of whitelisted devices
     * @return Number of whitelisted devices
     */
    fun getWhitelistCount(): Int {
        synchronized(whitelist) {
            return whitelist.size
        }
    }

    /**
     * Applies blacklist/whitelist rules to a list of devices
     * @param devices The list of devices to filter
     * @return Filtered list based on blacklist/whitelist rules
     */
    fun applyFilterRules(devices: List<NetworkDevice>): List<NetworkDevice> {
        return devices.filter { device ->
            // If whitelist is not empty, only allow whitelisted devices
            if (whitelist.isNotEmpty()) {
                device.ipAddress in whitelist
            } else {
                // If no whitelist, check if device is blacklisted
                device.ipAddress !in blacklist
            }
        }
    }
}

/**
 * Data class representing network device
 */
data class NetworkDevice(
    val ipAddress: String,
    val macAddress: String,
    val hostname: String? = null,
    val vendor: String? = null,
    val deviceType: String? = null, // e.g., phone, laptop, IoT device
    val hwType: String? = null,    // Hardware type (ethernet, wifi, etc.)
    val mask: String? = null,      // Network mask
    val deviceInterface: String? = null, // Network interface (wlan0, eth0, etc.)
    var isBlocked: Boolean = false
)

/**
 * Data class representing network traffic statistics
 */
data class NetworkTrafficStats(
    val device: NetworkDevice,
    val totalReceivedBytes: Long,
    val totalTransmittedBytes: Long,
    val totalReceivedPackets: Long,
    val totalTransmittedPackets: Long,
    val activeConnections: List<ConnectionInfo>,
    val timestamp: Long
)

/**
 * Data class representing a network connection
 */
data class ConnectionInfo(
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val protocol: String, // TCP, UDP, etc.
    val state: String,   // ESTABLISHED, LISTEN, etc.
    val timestamp: Long
)

/**
 * Data class representing network topology
 */
data class NetworkTopology(
    val gatewayDevice: NetworkDevice?,
    val allDevices: List<NetworkDevice>,
    val devicesByType: Map<String, List<NetworkDevice>>,
    val unknownDevices: List<NetworkDevice>
)