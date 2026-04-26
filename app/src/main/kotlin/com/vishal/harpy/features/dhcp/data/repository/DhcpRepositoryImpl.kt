package com.vishal.harpy.features.dhcp.data.repository

import com.vishal.harpy.features.dhcp.domain.repository.DhcpRepository
import com.vishal.harpy.core.utils.NetworkResult
import com.vishal.harpy.core.utils.NetworkError
import com.vishal.harpy.core.utils.LogUtils
import com.vishal.harpy.core.utils.ProcessUtils
import com.vishal.harpy.core.native.NativeNetworkWrapper
import com.vishal.harpy.features.network_monitor.domain.usecases.IsDeviceRootedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class DhcpRepositoryImpl @Inject constructor(
    private val context: Context,
    private val isDeviceRootedUseCase: IsDeviceRootedUseCase
) : DhcpRepository {

    private val dhcpSpoofingProcesses = ConcurrentHashMap<String, Process>()

    companion object {
        private const val TAG = "DhcpRepositoryImpl"
    }

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
            val isRootedResult = isDeviceRootedUseCase()
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
                    while (com.vishal.harpy.core.utils.ProcessUtils.isAlive(process)) {
                        val line = reader.readLine()
                        if (line != null) {
                            LogUtils.d(TAG, "DHCP Spoofing Output: $line")
                            if (line.contains("DHCP_SPOOF_STARTED")) {
                                LogUtils.i(TAG, "DHCP spoofing started successfully")
                            } else if (line.contains("DHCP_SPOOF_STATUS")) {
                                LogUtils.d(TAG, "DHCP Spoofing Status: $line")
                            }
                        } else {
                            break
                        }
                    }
                } catch (e: java.io.InterruptedIOException) {
                    // Expected when process is stopped - read thread interrupted by close
                    LogUtils.d(TAG, "DHCP output reading interrupted (process stopped)")
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading DHCP spoofing output: ${e.message}", e)
                } finally {
                    try {
                        reader.close()
                    } catch (e: Exception) {
                        // Ignore close errors
                    }
                }
            }

            // Read error stream in a background thread
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val errorReader = java.io.BufferedReader(java.io.InputStreamReader(errorStream))
                try {
                    while (com.vishal.harpy.core.utils.ProcessUtils.isAlive(process)) {
                        val errorLine = errorReader.readLine()
                        if (errorLine != null) {
                            LogUtils.e(TAG, "DHCP Spoofing Error: $errorLine")
                        } else {
                            break
                        }
                    }
                } catch (e: java.io.InterruptedIOException) {
                    // Expected when process is stopped - read thread interrupted by close
                    LogUtils.d(TAG, "DHCP error stream reading interrupted (process stopped)")
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading DHCP spoofing error stream: ${e.message}", e)
                } finally {
                    try {
                        errorReader.close()
                    } catch (e: Exception) {
                        // Ignore close errors
                    }
                }
            }

            // Wait a bit to see if the process started successfully
            kotlinx.coroutines.delay(1000)

            if (ProcessUtils.isAlive(process)) {
                // Store process for later management
                dhcpSpoofingProcesses["dhcp_main"] = process
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

    override suspend fun stopDHCPSpoofing(): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(TAG, "Stopping DHCP spoofing")

            val process = dhcpSpoofingProcesses["dhcp_main"]

            if (process != null && ProcessUtils.isAlive(process)) {
                ProcessUtils.destroyForcibly(process)
                dhcpSpoofingProcesses.remove("dhcp_main")
                LogUtils.i(TAG, "DHCP spoofing stopped successfully")
                NetworkResult.success(true)
            } else {
                LogUtils.w(TAG, "No active DHCP spoofing process found")
                NetworkResult.success(false)
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error stopping DHCP spoofing: ${e.message}", e)
            NetworkResult.error(NetworkError.CommandExecutionError(e))
        }
    }

    override fun isDHCPSpoofingActive(): Boolean {
        val process = dhcpSpoofingProcesses["dhcp_main"]
        return process != null && ProcessUtils.isAlive(process)
    }
}
