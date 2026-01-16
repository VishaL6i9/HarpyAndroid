package com.vishal.harpy.features.network_monitor.data.repository.impl

import com.vishal.harpy.features.network_monitor.data.repository.NetworkMonitorRepository
import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.core.utils.NetworkTopology
import com.vishal.harpy.core.utils.NetworkResult
import com.vishal.harpy.core.utils.NetworkError
import com.vishal.harpy.core.utils.RootError
import com.vishal.harpy.core.utils.RootErrorMapper
import com.vishal.harpy.core.utils.VendorLookup
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

                    // Perform network discovery using multiple methods
                    Log.d(TAG, "Performing network discovery...")
                    try {
                        val discoveryProcess = Runtime.getRuntime().exec("su")
                        val discoveryOutput = DataOutputStream(discoveryProcess.outputStream)
                        
                        // Method 1: Broadcast ping to trigger ARP responses
                        discoveryOutput.writeBytes("ping -b -c 1 -W 1 $subnet.255 > /dev/null 2>&1\n")
                        
                        // Method 2: Parallel TCP/UDP probes to common ports (faster than ICMP)
                        // This triggers ARP responses from devices even if they don't respond to ping
                        discoveryOutput.writeBytes("for i in {1..254}; do timeout 0.1 bash -c \"</dev/tcp/$subnet.\$i/22\" > /dev/null 2>&1 & done\n")
                        discoveryOutput.writeBytes("for i in {1..254}; do timeout 0.1 bash -c \"</dev/tcp/$subnet.\$i/80\" > /dev/null 2>&1 & done\n")
                        discoveryOutput.writeBytes("for i in {1..254}; do timeout 0.1 bash -c \"</dev/tcp/$subnet.\$i/443\" > /dev/null 2>&1 & done\n")
                        
                        // Method 3: Use arping if available
                        discoveryOutput.writeBytes("for i in {1..254}; do arping -c 1 -w 10 $subnet.\$i > /dev/null 2>&1 & done\n")
                        
                        discoveryOutput.writeBytes("wait\n")
                        discoveryOutput.writeBytes("exit\n")
                        discoveryOutput.flush()
                        discoveryOutput.close()
                        
                        val completed = discoveryProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                        if (!completed) {
                            discoveryProcess.destroyForcibly()
                        }
                        Log.d(TAG, "Network discovery completed")
                    } catch (e: Exception) {
                        Log.d(TAG, "Network discovery failed: ${e.message}")
                    }

                    // Now read ARP table after discovery using 'ip neigh' which is more comprehensive
                    Log.d(TAG, "Reading ARP table...")
                    val arpProcess = Runtime.getRuntime().exec("su")
                    val arpOutput = DataOutputStream(arpProcess.outputStream)
                    
                    // Use 'ip neigh' which is more comprehensive than /proc/net/arp
                    arpOutput.writeBytes("ip neigh show\n")
                    arpOutput.writeBytes("exit\n")
                    arpOutput.flush()
                    arpOutput.close()
                    
                    val arpReader = BufferedReader(InputStreamReader(arpProcess.inputStream))

                    var line: String?
                    val seenMacs = mutableSetOf<String>()  // Track seen MACs to avoid duplicates
                    
                    while (arpReader.readLine().also { line = it } != null) {
                        Log.d(TAG, "ARP entry: $line")
                        
                        // Parse 'ip neigh' output format: "192.168.29.1 dev wlan0 lladdr b4:8c:9d:8c:ef:09 REACHABLE"
                        val parts = line?.trim()?.split(Regex("\\s+"))
                        
                        if (parts != null && parts.size >= 5) {
                            val ip = parts[0]
                            val mac = parts.getOrNull(4)  // lladdr value
                            val state = parts.getOrNull(5)  // State (REACHABLE, STALE, etc.)
                            val deviceInterface = parts.getOrNull(2)  // dev value
                            
                            Log.d(TAG, "Parsed: IP=$ip, MAC=$mac, State=$state, Device=$deviceInterface")
                            
                            // Only accept IPv4 addresses (skip IPv6 like fe80:: or 2405:)
                            val isIPv4 = ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))
                            
                            // Accept devices with valid MAC addresses, IPv4 only, and not already seen
                            if (isIPv4 && mac != null && mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})$")) && 
                                mac != "00:00:00:00:00:00" && mac != "<incomplete>" && !seenMacs.contains(mac)) {
                                
                                seenMacs.add(mac)
                                
                                var hostname: String? = null
                                try {
                                    val hostnameProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", "nslookup $ip"))
                                    val hostnameReader = BufferedReader(InputStreamReader(hostnameProcess.inputStream))
                                    var hostnameLine: String?
                                    while (hostnameReader.readLine().also { hostnameLine = it } != null) {
                                        if (hostnameLine!!.contains("name = ")) {
                                            val nameMatch = HOSTNAME_PATTERN.find(hostnameLine!!)
                                            if (nameMatch != null) {
                                                hostname = nameMatch.groupValues[1].trimEnd('.')
                                                break
                                            }
                                        }
                                    }
                                    hostnameReader.close()
                                    hostnameProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                                    hostnameProcess.destroyForcibly()
                                } catch (e: Exception) {
                                    Log.d(TAG, "Could not resolve hostname for $ip: ${e.message}")
                                }

                                val vendor = identifyVendor(mac)
                                val deviceType = identifyDeviceType(NetworkDevice(ip, mac, hostname, vendor, "Unknown"))

                                val networkDevice = NetworkDevice(
                                    ipAddress = ip,
                                    macAddress = mac,
                                    hostname = hostname,
                                    vendor = vendor,
                                    deviceType = deviceType,
                                    hwType = "Unknown",
                                    mask = "*",
                                    deviceInterface = deviceInterface ?: "unknown"
                                )
                                devices.add(networkDevice)
                                Log.d(TAG, "Found device: $ip ($mac) - $vendor (state=$state)")
                            } else {
                                Log.d(TAG, "Skipping entry with invalid MAC: $mac")
                            }
                        }
                    }

                    arpReader.close()
                    val completed = arpProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                    if (!completed) {
                        arpProcess.destroyForcibly()
                        Log.w(TAG, "ARP read command timed out")
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