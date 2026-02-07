package com.vishal.harpy.features.dns.data.repository

import com.vishal.harpy.features.dns.domain.repository.DnsRepository
import com.vishal.harpy.core.utils.NetworkResult
import com.vishal.harpy.core.utils.NetworkError
import com.vishal.harpy.core.utils.LogUtils
import com.vishal.harpy.core.native.NativeNetworkWrapper
import com.vishal.harpy.features.network_monitor.domain.usecases.IsDeviceRootedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class DnsRepositoryImpl @Inject constructor(
    private val context: Context,
    private val isDeviceRootedUseCase: IsDeviceRootedUseCase
) : DnsRepository {

    private val dnsSpoofingProcesses = ConcurrentHashMap<String, Process>()
    
    companion object {
        private const val TAG = "DnsRepositoryImpl"
    }

    override suspend fun startDNSSpoofing(domain: String, spoofedIP: String, interfaceName: String): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(TAG, "Starting DNS spoofing: $domain -> $spoofedIP on interface $interfaceName")

            val helperPath = NativeNetworkWrapper.getRootHelperPath(context) ?: run {
                LogUtils.e(TAG, "Root helper not found")
                return@withContext NetworkResult.error(NetworkError.NativeLibraryError(Exception("Root helper not found")))
            }

            // Check if root access is available
            val isRootedResult = isDeviceRootedUseCase()
            if (isRootedResult is NetworkResult.Success && !isRootedResult.data) {
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
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                try {
                    while (process.isAlive) {
                        val line = reader.readLine()
                        if (line != null) {
                            LogUtils.d(TAG, "DNS Spoofing Output: $line")
                            if (line.contains("DNS_SPOOF_STARTED")) {
                                LogUtils.i(TAG, "DNS spoofing started successfully for $domain -> $spoofedIP")
                            } else if (line.contains("DNS_SPOOF_STATUS")) {
                                LogUtils.d(TAG, "DNS Spoofing Status: $line")
                            }
                        } else {
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
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val errorReader = java.io.BufferedReader(java.io.InputStreamReader(errorStream))
                try {
                    while (process.isAlive) {
                        val errorLine = errorReader.readLine()
                        if (errorLine != null) {
                            LogUtils.e(TAG, "DNS Spoofing Error: $errorLine")
                        } else {
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
                NetworkResult.success(false) 
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error stopping DNS spoofing: ${e.message}", e)
            NetworkResult.error(NetworkError.CommandExecutionError(e))
        }
    }

    override fun isDNSSpoofingActive(domain: String): Boolean {
        val processKey = "dns_$domain"
        val process = dnsSpoofingProcesses[processKey]
        return process != null && process.isAlive
    }
}
