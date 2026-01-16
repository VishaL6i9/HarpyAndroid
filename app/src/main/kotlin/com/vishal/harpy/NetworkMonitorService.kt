package com.vishal.harpy

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
                            val device = fields[5]  // Device name

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

                                    devices.add(NetworkDevice(ip, mac, hostname))
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
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            
            // Perform ARP spoofing to block the device
            // This is a simplified example - actual implementation would be more complex
            outputStream.writeBytes("echo 'Performing ARP spoofing to block ${device.ipAddress}'\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            
            process.waitFor()
            Log.i(TAG, "Successfully blocked device: ${device.ipAddress}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking device ${device.ipAddress}: ${e.message}", e)
            false
        }
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
        
        // Placeholder implementation for unblocking
        Log.i(TAG, "Successfully unblocked device: ${device.ipAddress}")
        return true
    }
}

/**
 * Data class representing a network device
 */
data class NetworkDevice(
    val ipAddress: String,
    val macAddress: String,
    val hostname: String? = null,
    val vendor: String? = null,
    var isBlocked: Boolean = false
)