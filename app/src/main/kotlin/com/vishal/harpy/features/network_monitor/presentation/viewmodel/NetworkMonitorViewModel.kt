package com.vishal.harpy.features.network_monitor.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.harpy.features.network_monitor.domain.usecases.ScanNetworkUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.IsDeviceRootedUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.BlockDeviceUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.UnblockDeviceUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.MapNetworkTopologyUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.TestPingUseCase
import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.core.utils.NetworkTopology
import com.vishal.harpy.core.utils.NetworkResult
import com.vishal.harpy.core.utils.NetworkError
import com.vishal.harpy.core.utils.NetworkErrorMapper
import com.vishal.harpy.core.utils.DevicePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkMonitorViewModel @Inject constructor(
    private val scanNetworkUseCase: ScanNetworkUseCase,
    private val isDeviceRootedUseCase: IsDeviceRootedUseCase,
    private val blockDeviceUseCase: BlockDeviceUseCase,
    private val unblockDeviceUseCase: UnblockDeviceUseCase,
    private val mapNetworkTopologyUseCase: MapNetworkTopologyUseCase,
    private val testPingUseCase: TestPingUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val devicePreferenceRepository = DevicePreferenceRepository(context)

    private val _networkDevices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val networkDevices: StateFlow<List<NetworkDevice>> = _networkDevices.asStateFlow()

    private val _isRooted = MutableStateFlow(false)
    val isRooted: StateFlow<Boolean> = _isRooted.asStateFlow()

    private val _networkTopology = MutableStateFlow<NetworkTopology?>(null)
    val networkTopology: StateFlow<NetworkTopology?> = _networkTopology.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastError = MutableStateFlow<NetworkError?>(null)
    val lastError: StateFlow<NetworkError?> = _lastError.asStateFlow()

    private val _scanSuccess = MutableStateFlow(false)
    val scanSuccess: StateFlow<Boolean> = _scanSuccess.asStateFlow()

    private val _filterIPv4 = MutableStateFlow(true)
    val filterIPv4: StateFlow<Boolean> = _filterIPv4.asStateFlow()

    private val _filterIPv6 = MutableStateFlow(false)
    val filterIPv6: StateFlow<Boolean> = _filterIPv6.asStateFlow()

    private val _testPingResult = MutableStateFlow<Pair<String, Boolean>?>(null)
    val testPingResult: StateFlow<Pair<String, Boolean>?> = _testPingResult.asStateFlow()

    private val _filteredDevices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val filteredDevices: StateFlow<List<NetworkDevice>> = _filteredDevices.asStateFlow()

    init {
        checkRootAccess()
    }

    private fun checkRootAccess() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = isDeviceRootedUseCase()
                when (result) {
                    is NetworkResult.Success -> {
                        _isRooted.value = result.data
                    }

                    is NetworkResult.Error -> {
                        _lastError.value = result.error
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun scanNetwork() {
        if (!_isRooted.value) {
            _error.value = "Device is not rooted. Root access is required to scan the network."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = scanNetworkUseCase()
                when (result) {
                    is NetworkResult.Success -> {
                        // Load preferences for each device
                        val devicesWithPreferences = result.data.map { device ->
                            val preference =
                                devicePreferenceRepository.getDevicePreference(device.macAddress)
                            device.copy(
                                deviceName = preference?.deviceName,
                                isPinned = preference?.isPinned ?: false
                            )
                        }

                        // Sort: pinned devices first, then by IP
                        val sortedDevices = devicesWithPreferences.sortedWith(
                            compareBy({ !it.isPinned }, { it.ipAddress })
                        )

                        _networkDevices.value = sortedDevices
                        applyFilters()
                        _scanSuccess.value = true
                        if (sortedDevices.isEmpty()) {
                            _error.value = "No devices found on the network"
                        }
                    }

                    is NetworkResult.Error -> {
                        _lastError.value = result.error
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun blockDevice(device: NetworkDevice) {
        if (!_isRooted.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = blockDeviceUseCase(device)
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            _networkDevices.value = _networkDevices.value.map {
                                if (it.ipAddress == device.ipAddress) {
                                    it.copy(isBlocked = true)
                                } else {
                                    it
                                }
                            }
                            applyFilters()
                        }
                    }

                    is NetworkResult.Error -> {
                        _lastError.value = result.error
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun unblockDevice(device: NetworkDevice) {
        if (!_isRooted.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = unblockDeviceUseCase(device)
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            _networkDevices.value = _networkDevices.value.map {
                                if (it.ipAddress == device.ipAddress) {
                                    it.copy(isBlocked = false)
                                } else {
                                    it
                                }
                            }
                            applyFilters()
                        }
                    }

                    is NetworkResult.Error -> {
                        _lastError.value = result.error
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun mapNetworkTopology() {
        if (!_isRooted.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = mapNetworkTopologyUseCase()
                when (result) {
                    is NetworkResult.Success -> {
                        _networkTopology.value = result.data
                    }

                    is NetworkResult.Error -> {
                        _lastError.value = result.error
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun testPing(device: NetworkDevice) {
        viewModelScope.launch {
            _isLoading.value = true
            _testPingResult.value = null
            try {
                val result = testPingUseCase(device)
                when (result) {
                    is NetworkResult.Success -> {
                        _testPingResult.value = Pair(device.ipAddress, result.data)
                    }
                    is NetworkResult.Error -> {
                        _testPingResult.value = Pair(device.ipAddress, false)
                        _error.value = "Ping test failed: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                _testPingResult.value = Pair(device.ipAddress, false)
                _error.value = "Ping test error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPingResult() {
        _testPingResult.value = null
    }

    /**
     * Get the detailed error report including stack trace
     */
    fun getErrorDetails(): String {
        return _lastError.value?.getDetailedReport() ?: "No error details available"
    }

    /**
     * Get the stack trace of the last error
     */
    fun getErrorStackTrace(): String {
        return _lastError.value?.getStackTrace() ?: "No stack trace available"
    }

    /**
     * Set device name for a device
     */
    fun setDeviceName(device: NetworkDevice, deviceName: String?) {
        viewModelScope.launch {
            devicePreferenceRepository.setDeviceName(device.macAddress, deviceName)
            // Update the device in the list
            _networkDevices.value = _networkDevices.value.map {
                if (it.macAddress == device.macAddress) {
                    it.copy(deviceName = deviceName)
                } else {
                    it
                }
            }
        }
    }

    /**
     * Toggle pin status for a device
     */
    fun toggleDevicePin(device: NetworkDevice) {
        viewModelScope.launch {
            devicePreferenceRepository.togglePin(device.macAddress)
            // Update the device in the list and re-sort
            val updatedDevices = _networkDevices.value.map {
                if (it.macAddress == device.macAddress) {
                    it.copy(isPinned = !it.isPinned)
                } else {
                    it
                }
            }
            // Re-sort: pinned devices first
            val sortedDevices = updatedDevices.sortedWith(
                compareBy({ !it.isPinned }, { it.ipAddress })
            )
            _networkDevices.value = sortedDevices
        }
    }

    /**
     * Clear all custom device names
     */
    fun clearAllDeviceNames() {
        viewModelScope.launch {
            _networkDevices.value.forEach { device ->
                devicePreferenceRepository.setDeviceName(device.macAddress, null)
            }
            // Update all devices to remove names
            _networkDevices.value = _networkDevices.value.map {
                it.copy(deviceName = null)
            }
        }
    }

    /**
     * Toggle IPv4 filter
     */
    fun toggleIPv4Filter() {
        val newState = !_filterIPv4.value
        _filterIPv4.value = newState
        com.vishal.harpy.core.utils.LogUtils.d("NetworkMonitorVM", "IPv4 filter toggled to: $newState")
        applyFilters()
    }

    /**
     * Toggle IPv6 filter
     */
    fun toggleIPv6Filter() {
        val newState = !_filterIPv6.value
        _filterIPv6.value = newState
        com.vishal.harpy.core.utils.LogUtils.d("NetworkMonitorVM", "IPv6 filter toggled to: $newState")
        applyFilters()
    }

    /**
     * Apply current filters to the device list
     */
    private fun applyFilters() {
        val ipv4Enabled = _filterIPv4.value
        val ipv6Enabled = _filterIPv6.value

        com.vishal.harpy.core.utils.LogUtils.d(
            "NetworkMonitorVM",
            "Applying filters - IPv4: $ipv4Enabled, IPv6: $ipv6Enabled"
        )

        val filtered = _networkDevices.value.filter { device ->
            val isIPv4 =
                device.ipAddress.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))
            val isIPv6 = !isIPv4

            (isIPv4 && ipv4Enabled) || (isIPv6 && ipv6Enabled)
        }

        val originalCount = _networkDevices.value.size
        val filteredCount = filtered.size

        com.vishal.harpy.core.utils.LogUtils.d(
            "NetworkMonitorVM",
            "Filter applied: $originalCount devices -> $filteredCount devices"
        )

        _filteredDevices.value = filtered
    }

    /**
     * Start DNS spoofing for a domain
     */
    fun startDNSSpoofing(domain: String, spoofedIP: String, interfaceName: String = "wlan0") {
        if (!_isRooted.value) {
            _error.value = "Root access is required for DNS spoofing"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = scanNetworkUseCase.repository.startDNSSpoofing(domain, spoofedIP, interfaceName)
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            com.vishal.harpy.core.utils.LogUtils.i("NetworkMonitorVM", "DNS spoofing started for $domain -> $spoofedIP")
                            _error.value = "DNS spoofing started for $domain -> $spoofedIP"
                        } else {
                            _error.value = "Failed to start DNS spoofing"
                        }
                    }
                    is NetworkResult.Error -> {
                        _lastError.value = result.error
                        _error.value = "DNS spoofing failed: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = "DNS spoofing error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Stop DNS spoofing for a domain
     */
    fun stopDNSSpoofing(domain: String) {
        if (!_isRooted.value) {
            _error.value = "Root access is required for DNS spoofing"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = scanNetworkUseCase.repository.stopDNSSpoofing(domain)
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            com.vishal.harpy.core.utils.LogUtils.i("NetworkMonitorVM", "DNS spoofing stopped for $domain")
                            _error.value = "DNS spoofing stopped for $domain"
                        } else {
                            _error.value = "No active DNS spoofing found for $domain"
                        }
                    }
                    is NetworkResult.Error -> {
                        _lastError.value = result.error
                        _error.value = "Stop DNS spoofing failed: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = "Stop DNS spoofing error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Check if DNS spoofing is active for a domain
     */
    fun isDNSSpoofingActive(domain: String): Boolean {
        return scanNetworkUseCase.repository.isDNSSpoofingActive(domain)
    }


    /**
     * Check if DHCP spoofing is active
     */
    fun isDHCPSpoofingActive(): Boolean {
        return scanNetworkUseCase.repository.isDHCPSpoofingActive()
    }

    /**
     * Start DHCP spoofing for specific devices
     */
    fun startDHCPSpoofing(
        interfaceName: String = "wlan0",
        targetMacs: Array<String>,
        spoofedIPs: Array<String>,
        gatewayIPs: Array<String>,
        subnetMasks: Array<String>,
        dnsServers: Array<String>
    ) {
        if (!_isRooted.value) {
            _error.value = "Root access is required for DHCP spoofing"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = scanNetworkUseCase.repository.startDHCPSpoofing(
                    interfaceName,
                    targetMacs,
                    spoofedIPs,
                    gatewayIPs,
                    subnetMasks,
                    dnsServers
                )
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            com.vishal.harpy.core.utils.LogUtils.i("NetworkMonitorVM", "DHCP spoofing started for ${targetMacs.size} devices")
                            _error.value = "DHCP spoofing started for ${targetMacs.size} devices"
                        } else {
                            _error.value = "Failed to start DHCP spoofing"
                        }
                    }
                    is NetworkResult.Error -> {
                        _lastError.value = result.error
                        _error.value = "DHCP spoofing failed: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = "DHCP spoofing error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Stop DHCP spoofing
     */
    fun stopDHCPSpoofing() {
        if (!_isRooted.value) {
            _error.value = "Root access is required for DHCP spoofing"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = scanNetworkUseCase.repository.stopDHCPSpoofing()
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            com.vishal.harpy.core.utils.LogUtils.i("NetworkMonitorVM", "DHCP spoofing stopped")
                            _error.value = "DHCP spoofing stopped"
                        } else {
                            _error.value = "No active DHCP spoofing found"
                        }
                    }
                    is NetworkResult.Error -> {
                        _lastError.value = result.error
                        _error.value = "Stop DHCP spoofing failed: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = "Stop DHCP spoofing error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Reset the scan success flag
     */
    fun resetScanSuccess() {
        _scanSuccess.value = false
    }
}