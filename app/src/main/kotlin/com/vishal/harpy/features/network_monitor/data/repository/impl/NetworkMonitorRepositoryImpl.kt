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
import com.vishal.harpy.core.utils.LogUtils
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import kotlin.text.Regex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class NetworkMonitorRepositoryImpl(private val context: android.content.Context) : NetworkMonitorRepository {

    companion object {
        private const val TAG = "NetworkMonitorRepoImpl"
        
        // Cache regex patterns for better performance
        private val SUBNET_PATTERN = Regex("""([0-9]+\.[0-9]+\.[0-9]+)\.0""")
        private val HOSTNAME_PATTERN = Regex("name = (.+)")
        private val FIELDS_SPLIT_PATTERN = Regex("\\s+")
    }

    private val blockingProcesses = java.util.concurrent.ConcurrentHashMap<String, Process>()
    private val dnsSpoofingProcesses = java.util.concurrent.ConcurrentHashMap<String, Process>()

    /**
     * Start DNS spoofing for a specific domain
     */
    override suspend fun startDNSSpoofing(domain: String, spoofedIP: String, interfaceName: String): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(TAG, "Starting DNS spoofing: $domain -> $spoofedIP on interface $interfaceName")

            val helperPath = NativeNetworkWrapper.getRootHelperPath(context) ?: run {
                LogUtils.e(TAG, "Root helper not found")
                return@withContext NetworkResult.error(NetworkError.NativeLibraryError(Exception("Root helper not found")))
            }

            // Check if root access is available
            val isRootedResult = isDeviceRooted()
            if (isRootedResult is com.vishal.harpy.core.utils.NetworkResult.Success && !isRootedResult.data) {
                LogUtils.e(TAG, "Root access not available for DNS spoofing")
                return@withContext NetworkResult.error(NetworkError.DeviceNotRootedError())
            }

            // Kill any existing DNS spoofing process for this domain
            val processKey = "dns_$domain"
            dnsSpoofingProcesses[processKey]?.destroyForcibly()

            // Build the command to execute the root helper with DNS spoofing
            val command = arrayOf("su", "-c", "$helperPath dns_spoof $interfaceName $domain $spoofedIP")

            LogUtils.d(TAG, "Executing DNS spoofing command: ${command.joinToString(" ")}")

            val process = Runtime.getRuntime().exec(command)
            dnsSpoofingProcesses[processKey] = process

            // Handle output streams
            val inputStream = process.inputStream
            val errorStream = process.errorStream

            // Read output in a background thread
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                try {
                    while (process.isAlive) {
                        val line = reader.readLine()
                        if (line != null) {
                            LogUtils.d(TAG, "DNS Spoofing Output: $line")
                            // Check for specific status messages
                            if (line.contains("DNS_SPOOF_STARTED")) {
                                LogUtils.i(TAG, "DNS spoofing started successfully for $domain -> $spoofedIP")
                            } else if (line.contains("DNS_SPOOF_STATUS")) {
                                LogUtils.d(TAG, "DNS Spoofing Status: $line")
                            }
                        } else {
                            // If readline returns null, the stream is closed
                            break
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading DNS spoofing output: ${e.message}", e)
                } finally {
                    reader.close()
                }
            }

            // Read error stream in a background thread
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val errorReader = java.io.BufferedReader(java.io.InputStreamReader(errorStream))
                try {
                    while (process.isAlive) {
                        val errorLine = errorReader.readLine()
                        if (errorLine != null) {
                            LogUtils.e(TAG, "DNS Spoofing Error: $errorLine")
                        } else {
                            // If readline returns null, the stream is closed
                            break
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading DNS spoofing error stream: ${e.message}", e)
                } finally {
                    errorReader.close()
                }
            }

            // Wait a bit to see if the process started successfully
            kotlinx.coroutines.delay(1000)

            if (process.isAlive) {
                LogUtils.i(TAG, "DNS spoofing process started successfully for $domain -> $spoofedIP")
                NetworkResult.success(true)
            } else {
                LogUtils.e(TAG, "DNS spoofing process failed to start for $domain -> $spoofedIP")
                NetworkResult.error(NetworkError.CommandExecutionError(Exception("DNS spoofing process exited early")))
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error starting DNS spoofing: ${e.message}", e)
            NetworkResult.error(NetworkError.CommandExecutionError(e))
        }
    }

    /**
     * Stop DNS spoofing for a specific domain
     */
    override suspend fun stopDNSSpoofing(domain: String): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(TAG, "Stopping DNS spoofing for domain: $domain")

            val processKey = "dns_$domain"
            val process = dnsSpoofingProcesses[processKey]

            if (process != null && process.isAlive) {
                process.destroyForcibly()
                dnsSpoofingProcesses.remove(processKey)
                LogUtils.i(TAG, "DNS spoofing stopped for domain: $domain")
                NetworkResult.success(true)
            } else {
                LogUtils.w(TAG, "No active DNS spoofing process found for domain: $domain")
                NetworkResult.success(false) // Not an error, just not running
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error stopping DNS spoofing: ${e.message}", e)
            NetworkResult.error(NetworkError.CommandExecutionError(e))
        }
    }

    /**
     * Check if DNS spoofing is active for a specific domain
     */
    override fun isDNSSpoofingActive(domain: String): Boolean {
        val processKey = "dns_$domain"
        val process = dnsSpoofingProcesses[processKey]
        return process != null && process.isAlive
    }

    /**
     * Start DHCP spoofing for specific devices
     */
    override suspend fun startDHCPSpoofing(
        interfaceName: String,
        targetMacs: Array<String>,
        spoofedIPs: Array<String>,
        gatewayIPs: Array<String>,
        subnetMasks: Array<String>,
        dnsServers: Array<String>
    ): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(TAG, "Starting DHCP spoofing for ${targetMacs.size} devices")

            val helperPath = NativeNetworkWrapper.getRootHelperPath(context) ?: run {
                LogUtils.e(TAG, "Root helper not found")
                return@withContext NetworkResult.error(NetworkError.NativeLibraryError(Exception("Root helper not found")))
            }

            // Check if root access is available
            val isRootedResult = isDeviceRooted()
            if (isRootedResult is NetworkResult.Success && !isRootedResult.data) {
                LogUtils.e(TAG, "Root access not available for DHCP spoofing")
                return@withContext NetworkResult.error(NetworkError.DeviceNotRootedError())
            }

            // Validate arrays have same size
            if (targetMacs.size != spoofedIPs.size ||
                targetMacs.size != gatewayIPs.size ||
                targetMacs.size != subnetMasks.size ||
                targetMacs.size != dnsServers.size) {
                LogUtils.e(TAG, "All DHCP spoofing arrays must have the same size")
                return@withContext NetworkResult.error(NetworkError.CommandExecutionError(Exception("Array sizes mismatch")))
            }

            // Build the command to execute the root helper with DHCP spoofing
            val command = arrayOf("su", "-c", "$helperPath dhcp_spoof $interfaceName ${targetMacs[0]} ${spoofedIPs[0]} ${gatewayIPs[0]} ${dnsServers[0]}")

            LogUtils.d(TAG, "Executing DHCP spoofing command: ${command.joinToString(" ")}")

            val process = Runtime.getRuntime().exec(command)

            // Handle output streams
            val inputStream = process.inputStream
            val errorStream = process.errorStream

            // Read output in a background thread
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                try {
                    while (process.isAlive) {
                        val line = reader.readLine()
                        if (line != null) {
                            LogUtils.d(TAG, "DHCP Spoofing Output: $line")
                            // Check for specific status messages
                            if (line.contains("DHCP_SPOOF_STARTED")) {
                                LogUtils.i(TAG, "DHCP spoofing started successfully")
                            } else if (line.contains("DHCP_SPOOF_STATUS")) {
                                LogUtils.d(TAG, "DHCP Spoofing Status: $line")
                            }
                        } else {
                            // If readline returns null, the stream is closed
                            break
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading DHCP spoofing output: ${e.message}", e)
                } finally {
                    reader.close()
                }
            }

            // Read error stream in a background thread
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val errorReader = java.io.BufferedReader(java.io.InputStreamReader(errorStream))
                try {
                    while (process.isAlive) {
                        val errorLine = errorReader.readLine()
                        if (errorLine != null) {
                            LogUtils.e(TAG, "DHCP Spoofing Error: $errorLine")
                        } else {
                            // If readline returns null, the stream is closed
                            break
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading DHCP spoofing error stream: ${e.message}", e)
                } finally {
                    errorReader.close()
                }
            }

            // Wait a bit to see if the process started successfully
            kotlinx.coroutines.delay(1000)

            if (process.isAlive) {
                LogUtils.i(TAG, "DHCP spoofing process started successfully")
                NetworkResult.success(true)
            } else {
                LogUtils.e(TAG, "DHCP spoofing process failed to start")
                NetworkResult.error(NetworkError.CommandExecutionError(Exception("DHCP spoofing process exited early")))
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error starting DHCP spoofing: ${e.message}", e)
            NetworkResult.error(NetworkError.CommandExecutionError(e))
        }
    }

    /**
     * Stop DHCP spoofing
     */
    override suspend fun stopDHCPSpoofing(): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(TAG, "Stopping DHCP spoofing is not implemented as it requires process management")
            // In a real implementation, we would need to track and kill the DHCP spoofing process
            NetworkResult.success(false) // Not implemented yet
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error stopping DHCP spoofing: ${e.message}", e)
            NetworkResult.error(NetworkError.CommandExecutionError(e))
        }
    }

    /**
     * Check if DHCP spoofing is active
     */
    override fun isDHCPSpoofingActive(): Boolean {
        // In a real implementation, this would check if any DHCP spoofing processes are running
        return false
    }

    /**
     * Start DNS spoofing for a specific domain (overload with default interface name)
     */
    suspend fun startDNSSpoofing(domain: String, spoofedIP: String): NetworkResult<Boolean> {
        return startDNSSpoofing(domain, spoofedIP, "wlan0")
    }

    override suspend fun scanNetwork(): NetworkResult<List<NetworkDevice>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(TAG, "Scanning network for connected devices...")

            val isRootedResult = isDeviceRooted()
            if (isRootedResult is NetworkResult.Success && isRootedResult.data) {
                val devices = scanNetworkWithRoot()
                NetworkResult.success(devices)
            } else {
                LogUtils.w(TAG, "Device is not rooted. Limited functionality available.")
                NetworkResult.success(emptyList())
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error scanning network: ${e.message}", e)
            NetworkResult.error(NetworkError.NetworkScanError(e))
        }
    }

    override suspend fun isDeviceRooted(): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
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
                    return@withContext NetworkResult.success(true)
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
                    return@withContext NetworkResult.success(true)
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
                    return@withContext NetworkResult.success(true)
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
        val ourIp = getOurIp() // Cache our IP once at the start of scan
        val gatewayIp = getGatewayIp() // Cache gateway IP once at the start of scan
        Log.d(TAG, "Starting root scan. Our IP: $ourIp, Gateway IP: $gatewayIp")

        try {
            Log.d(TAG, "Getting network route information...")
            
            // Try to get route with su first, then fallback to direct command
            var routeLine: String? = null
            val allRoutes = mutableListOf<String>()
            
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
                        allRoutes.add(line)
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
                            allRoutes.add(line)
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

            // Prioritize WiFi/Ethernet interfaces over VPN/tunnel interfaces
            // Prefer: wlan0, eth0, rmnet, then others, avoid tun/tap/ppp
            routeLine = allRoutes.firstOrNull { it.contains("dev wlan") } // WiFi first
                ?: allRoutes.firstOrNull { it.contains("dev eth") }        // Ethernet second
                ?: allRoutes.firstOrNull { it.contains("dev rmnet") }      // Mobile data third
                ?: allRoutes.firstOrNull { !it.contains("dev tun") && !it.contains("dev tap") && !it.contains("dev ppp") } // Any non-VPN
                ?: allRoutes.firstOrNull()                                  // Last resort: any route
            
            if (routeLine != null) {
                Log.d(TAG, "Selected route (prioritized WiFi/Ethernet): $routeLine")
            } else {
                Log.d(TAG, "No suitable route found from ${allRoutes.size} routes")
            }

            // If we have a route, extract subnet from it
            var subnet: String? = null
            if (routeLine != null) {
                val matchResult = SUBNET_PATTERN.find(routeLine)
                if (matchResult != null) {
                    subnet = matchResult.groupValues[1]
                }
            }
            
            // Fallback: If no subnet from route, derive from gateway IP
            if (subnet == null && gatewayIp.isNotEmpty()) {
                val gatewayParts = gatewayIp.split(".")
                if (gatewayParts.size >= 3) {
                    subnet = "${gatewayParts[0]}.${gatewayParts[1]}.${gatewayParts[2]}"
                    Log.d(TAG, "Using gateway-derived subnet: $subnet")
                }
            }

            if (subnet != null) {
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
                        val cmd = "chmod 755 $helperPath && LD_LIBRARY_PATH=$libDir $helperPath scan wlan0 $subnet 10 2>&1\n"
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
                        
                        // Increase waitFor to 15 seconds to allow for the 10-second scan + overhead
                        val completed = helperProcess.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
                        if (!completed) {
                            Log.w(TAG, "Root helper scan timed out, killing process")
                            helperProcess.destroyForcibly()
                        }
                        
                        if (helperFoundDevices.isNotEmpty()) {
                            Log.d(TAG, "Root helper found ${helperFoundDevices.size} devices")
                            for (deviceStr in helperFoundDevices) {
                                val parts = deviceStr.split("|")
                                if (parts.size == 2) {
                                    val ip = parts[0]
                                    val mac = parts[1]
                                    addDeviceToList(devices, ip, mac, "wlan0", null, ourIp, gatewayIp)
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
                                addDeviceToList(devices, ip, mac, deviceInterface, null, ourIp, gatewayIp)
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
                                addDeviceToList(devices, ip, mac, deviceInterface, null, ourIp, gatewayIp)
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
        hostname: String?,
        ourIp: String?,
        gatewayIp: String?
    ) {
        var resolvedHostname = hostname
        
        // Cache our IP to avoid calling getOurIp() in a loop
        val isOurDevice = (ip == ourIp)
        
        // Try to resolve hostname if not provided and not our device (our device usually resolves easily or we already know it)
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
            isCurrentDevice = isOurDevice,
            isGateway = (ip == gatewayIp)
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

    override suspend fun blockDevice(device: NetworkDevice): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val isRootedResult = isDeviceRooted()
            if (!(isRootedResult is NetworkResult.Success && isRootedResult.data)) {
                return@withContext NetworkResult.error(NetworkError.DeviceNotRootedError())
            }

            // Check if already blocking
            if (blockingProcesses.containsKey(device.ipAddress)) {
                return@withContext NetworkResult.success(true)
            }

            val gatewayIp = getGatewayIp() ?: run {
                Log.e(TAG, "Failed to get gateway IP")
                return@withContext NetworkResult.error(NetworkError.NetworkAccessError(Exception("Gateway IP lookup failed. Please check your network connection.")))
            }
            val helperPath = NativeNetworkWrapper.getRootHelperPath(context) ?: run {
                Log.e(TAG, "Failed to find root helper binary")
                return@withContext NetworkResult.error(NetworkError.NativeLibraryError(Exception("Root helper binary not found at expected path")))
            }
            val iface = getActiveInterface() ?: "wlan0"
            val ourMac = getOurMacAddress(iface) ?: "00:00:00:00:00:00"
            
            Log.d(TAG, "Gateway: $gatewayIp, Helper: $helperPath, Interface: $iface, OurMac: $ourMac")

            // Start blocking process in background
            val libDir = context.applicationInfo.nativeLibraryDir
            val cmd = if (device.isGateway) {
                "chmod 755 $helperPath && LD_LIBRARY_PATH=$libDir $helperPath block_all $iface $gatewayIp $ourMac\n"
            } else {
                "chmod 755 $helperPath && LD_LIBRARY_PATH=$libDir $helperPath block $iface ${device.ipAddress} $gatewayIp $ourMac\n"
            }
            
            Log.d(TAG, "Starting ${if (device.isGateway) "NUCLEAR" else "blocking"} process: $cmd")
            val process = Runtime.getRuntime().exec("su")
            val out = DataOutputStream(process.outputStream)
            out.writeBytes(cmd)
            out.flush()
            // Keep output stream open or the process might exit depending on su implementation
            
            blockingProcesses[device.ipAddress] = process
            
            Log.i(TAG, "${if (device.isGateway) "NUCLEAR blocking" else "Persistent blocking"} started for ${device.ipAddress}")
            NetworkResult.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking device ${device.ipAddress}: ${e.message}", e)
            NetworkResult.error(NetworkError.BlockDeviceError(e))
        }
    }

    override suspend fun unblockDevice(device: NetworkDevice): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val process = blockingProcesses.remove(device.ipAddress)
            if (process != null) {
                // Perform proactive restoration
                val gatewayIp = getGatewayIp()
                val iface = getActiveInterface() ?: "wlan0"
                val helperPath = NativeNetworkWrapper.getRootHelperPath(context)
                
                if (gatewayIp != null && helperPath != null) {
                    val gatewayMac = getOurMacAddress(iface) // Wait, this is OUR mac. I need the true gateway mac.
                    // Actually, the app likely already contains the gateway mac if it scanned.
                    // Let's try to resolve it using the helper or use a broadcast-friendly restoration.
                    
                    // Actually, the most robust way is to use the helper to get the mac first.
                    val cmdUnblock = "chmod 755 $helperPath && $helperPath unblock $iface ${device.ipAddress} ${device.macAddress ?: "00:00:00:00:00:00"} $gatewayIp"
                    
                    // We need the gateway MAC. In a real scenario, we'd fetch it. 
                    // Let's assume we can get it or use the repository's knowledge.
                    val gMac = getGatewayMacInternal(iface, gatewayIp) ?: "ff:ff:ff:ff:ff:ff" 
                    
                    val fullUnblockCmd = "$cmdUnblock $gMac\n"
                    
                    val restorer = Runtime.getRuntime().exec("su")
                    val restorerOut = DataOutputStream(restorer.outputStream)
                    restorerOut.writeBytes(fullUnblockCmd)
                    restorerOut.writeBytes("exit\n")
                    restorerOut.flush()
                    restorerOut.close()
                    restorer.waitFor()
                }

                // Try to kill the root process
                val killer = Runtime.getRuntime().exec("su")
                val out = DataOutputStream(killer.outputStream)
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

    private fun getGatewayMacInternal(iface: String, ip: String): String? {
        try {
            // First check ARP table via ip neigh
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip neigh show $ip"))
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val line = r.readLine()
            r.close()
            p.waitFor()
            
            if (!line.isNullOrEmpty() && line.contains("lladdr")) {
                val parts = line.split(" ")
                val index = parts.indexOf("lladdr")
                if (index != -1 && index + 1 < parts.size) {
                    val mac = parts[index + 1]
                    if (mac.contains(":") && mac != "00:00:00:00:00:00") return mac
                }
            }
            
            // Fallback: Use helper to resolve MAC if not in cache
            val helperPath = NativeNetworkWrapper.getRootHelperPath(context)
            if (helperPath != null) {
                val p2 = Runtime.getRuntime().exec(arrayOf("su", "-c", "$helperPath mac $iface $ip"))
                val r2 = BufferedReader(InputStreamReader(p2.inputStream))
                val mac = r2.readLine()?.trim()
                r2.close()
                p2.waitFor()
                if (!mac.isNullOrEmpty() && mac.contains(":")) return mac
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to resolve gateway MAC internally: ${e.message}")
        }
        return null
    }

    override suspend fun mapNetworkTopology(): NetworkResult<NetworkTopology> = withContext(Dispatchers.IO) {
        try {
            val scanResult = scanNetwork()
            if (scanResult is NetworkResult.Error) {
                return@withContext NetworkResult.error(scanResult.error)
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
        Log.d(TAG, "Attempting to get gateway IP...")
        
        // Method 1: ConnectivityManager
        try {
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val linkProperties = connectivityManager?.getLinkProperties(activeNetwork)
            
            val gateway = linkProperties?.routes?.firstOrNull { 
                it.isDefaultRoute && it.gateway is java.net.Inet4Address 
            }?.gateway?.hostAddress
            
            if (gateway != null) {
                Log.d(TAG, "Gateway IPv4 found via ConnectivityManager: $gateway")
                return gateway
            }
            
            // Fallback to any default gateway if IPv4 specific wasn't found (though unlikely for block to work)
            val anyGateway = linkProperties?.routes?.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress
            if (anyGateway != null) {
                Log.d(TAG, "Gateway IP (fallback) found via ConnectivityManager: $anyGateway")
                return anyGateway
            }
        } catch (e: Exception) {
            Log.d(TAG, "ConnectivityManager gateway lookup failed: ${e.message}")
        }

        // Method 2: Shell fallback
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip route get 1.1.1.1 | grep -o 'via [0-9.]*' | awk '{print \$2}'"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val gatewayIp = reader.readLine()
            Log.d(TAG, "Gateway IP output (shell): $gatewayIp")
            reader.close()
            process.waitFor()
            gatewayIp?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting gateway IP via shell: ${e.message}", e)
            null
        }
    }

    private fun getOurMacAddress(iface: String = "wlan0"): String? {
        Log.d(TAG, "Getting MAC address for interface: $iface")
        
        // Method 1: cat /sys/class/net/iface/address
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /sys/class/net/$iface/address"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val mac = reader.readLine()?.trim()
            reader.close()
            process.waitFor()
            if (!mac.isNullOrEmpty() && mac != "00:00:00:00:00:00" && mac.contains(":")) {
                Log.d(TAG, "MAC found via sysfs: $mac")
                return mac
            }
        } catch (e: Exception) {
            Log.d(TAG, "Sysfs MAC lookup failed: ${e.message}")
        }

        // Method 2: ip link show
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip link show $iface | grep -o 'link/ether [0-9a-f:]*' | awk '{print \$2}'"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val mac = reader.readLine()?.trim()
            reader.close()
            process.waitFor()
            if (!mac.isNullOrEmpty() && mac != "00:00:00:00:00:00" && mac.contains(":")) {
                Log.d(TAG, "MAC found via ip link: $mac")
                return mac
            }
        } catch (e: Exception) {
            Log.d(TAG, "ip link MAC lookup failed: ${e.message}")
        }

        // Method 3: ip addr show
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip addr show $iface | grep -o 'link/ether [0-9a-f:]*' | awk '{print \$2}'"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val mac = reader.readLine()?.trim()
            reader.close()
            process.waitFor()
            if (!mac.isNullOrEmpty() && mac != "00:00:00:00:00:00" && mac.contains(":")) {
                Log.d(TAG, "MAC found via ip addr: $mac")
                return mac
            }
        } catch (e: Exception) {
            Log.d(TAG, "ip addr MAC lookup failed: ${e.message}")
        }

        // Method 4: All Interfaces Fallback
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip link show | grep 'link/ether' | grep -v '00:00:00:00:00:00' | head -n 1 | awk '{print \$2}'"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val mac = reader.readLine()?.trim()
            reader.close()
            process.waitFor()
            if (!mac.isNullOrEmpty() && mac.contains(":")) {
                Log.d(TAG, "MAC found via global ip link search: $mac")
                return mac
            }
        } catch (e: Exception) {
            Log.d(TAG, "Global MAC search failed: ${e.message}")
        }

        Log.e(TAG, "All MAC detection methods failed for $iface")
        return null
    }

    override suspend fun testPing(device: NetworkDevice): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            // First try ICMP Ping
            val pingProcess = Runtime.getRuntime().exec("ping -c 1 -W 1 ${device.ipAddress}")
            val exitCode = pingProcess.waitFor()
            
            if (exitCode == 0) {
                Log.d(TAG, "Ping successful for ${device.ipAddress}")
                return@withContext NetworkResult.success(true)
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

    private fun getActiveInterface(): String? {
        try {
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val linkProperties = connectivityManager?.getLinkProperties(activeNetwork)
            return linkProperties?.interfaceName
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get active interface: ${e.message}")
        }
        return null
    }

    private fun getOurIp(): String? {
        // Method 1: ConnectivityManager (Primary Android API)
        try {
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val linkProperties = connectivityManager?.getLinkProperties(activeNetwork)
            
            val ipAddress = linkProperties?.linkAddresses?.firstOrNull { 
                it.address is java.net.Inet4Address && !it.address.isLoopbackAddress 
            }?.address?.hostAddress
            
            if (ipAddress != null) {
                return ipAddress
            }
        } catch (e: Exception) {
            Log.d(TAG, "ConnectivityManager IP lookup failed: ${e.message}")
        }

        // Method 2: Robust Shell Fallback (ip route get)
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip route get 1.1.1.1 | grep -o 'src [0-9.]*' | awk '{print \$2}'"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val ourIp = reader.readLine()
            reader.close()
            process.waitFor()
            ourIp?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Shell IP identification fallback failed: ${e.message}")
            null
        }
    }
}
