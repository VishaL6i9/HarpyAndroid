package com.vishal.harpy.features.network_monitor.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.harpy.features.network_monitor.domain.usecases.ScanNetworkUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.IsDeviceRootedUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.BlockDeviceUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.UnblockDeviceUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.UnblockAllDevicesUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.MapNetworkTopologyUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.TestPingUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.IsDeviceBlockedUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.RestoreBlockedDevicesUseCase
import com.vishal.harpy.features.dns.domain.usecases.StartDnsSpoofingUseCase
import com.vishal.harpy.features.dns.domain.usecases.StopDnsSpoofingUseCase
import com.vishal.harpy.features.dns.domain.usecases.IsDnsSpoofingActiveUseCase
import com.vishal.harpy.features.dhcp.domain.usecases.StartDhcpSpoofingUseCase
import com.vishal.harpy.features.dhcp.domain.usecases.StopDhcpSpoofingUseCase
import com.vishal.harpy.features.dhcp.domain.usecases.IsDhcpSpoofingActiveUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.GetActiveInterfaceUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.GetOurIpUseCase
import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.core.utils.NetworkTopology
import com.vishal.harpy.core.utils.NetworkResult
import com.vishal.harpy.core.utils.NetworkError
import com.vishal.harpy.core.utils.NetworkErrorMapper
import com.vishal.harpy.core.utils.DevicePreferenceRepository
import com.vishal.harpy.core.utils.SpoofingSession
import com.vishal.harpy.core.utils.DhcpSpoofingRule
import com.vishal.harpy.core.state.SpoofingSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.vishal.harpy.core.utils.SettingsRepository
import com.vishal.harpy.core.utils.AppSettings

enum class LoadingState {
    None, Scanning, Blocking, Unblocking, MappingTopology, TestingPing, DNSSpoofing, DHCPSpoofing
}

private const val TAG = "NetworkMonitorVM"

@HiltViewModel
class NetworkMonitorViewModel @Inject constructor(
    private val scanNetworkUseCase: ScanNetworkUseCase,
    private val isDeviceRootedUseCase: IsDeviceRootedUseCase,
    private val blockDeviceUseCase: BlockDeviceUseCase,
    private val unblockDeviceUseCase: UnblockDeviceUseCase,
    private val unblockAllDevicesUseCase: UnblockAllDevicesUseCase,
    private val mapNetworkTopologyUseCase: MapNetworkTopologyUseCase,
    private val testPingUseCase: TestPingUseCase,
    private val isDeviceBlockedUseCase: IsDeviceBlockedUseCase,
    private val restoreBlockedDevicesUseCase: RestoreBlockedDevicesUseCase,
    private val startDnsSpoofingUseCase: StartDnsSpoofingUseCase,
    private val stopDnsSpoofingUseCase: StopDnsSpoofingUseCase,
    private val isDnsSpoofingActiveUseCase: IsDnsSpoofingActiveUseCase,
    private val startDhcpSpoofingUseCase: StartDhcpSpoofingUseCase,
    private val stopDhcpSpoofingUseCase: StopDhcpSpoofingUseCase,
    private val isDhcpSpoofingActiveUseCase: IsDhcpSpoofingActiveUseCase,
    private val getActiveInterfaceUseCase: GetActiveInterfaceUseCase,
    private val getOurIpUseCase: GetOurIpUseCase,
    private val sessionManager: SpoofingSessionManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val devicePreferenceRepository = DevicePreferenceRepository(context)

    private val _networkDevices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val networkDevices: StateFlow<List<NetworkDevice>> = _networkDevices.asStateFlow()

    private val _isRooted = MutableStateFlow(false)
    val isRooted: StateFlow<Boolean> = _isRooted.asStateFlow()

    private val _networkTopology = MutableStateFlow<NetworkTopology?>(null)
    val networkTopology: StateFlow<NetworkTopology?> = _networkTopology.asStateFlow()

    private val _loadingState: MutableStateFlow<LoadingState> = MutableStateFlow(LoadingState.None)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

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

    private val _detectedInterface = MutableStateFlow<String?>(null)
    val detectedInterface: StateFlow<String?> = _detectedInterface.asStateFlow()

    private val _detectedIp = MutableStateFlow<String?>(null)
    val detectedIp: StateFlow<String?> = _detectedIp.asStateFlow()

    val appSettings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = settingsRepository.loadSettings()
        )

    private val _logCount = MutableStateFlow(0)
    val logCount: StateFlow<Int> = _logCount.asStateFlow()

    /**
     * Get blacklisted (blocked) devices
     */
    val blacklistedDevices: StateFlow<List<NetworkDevice>> = _networkDevices
        .map { devices -> devices.filter { it.isBlocked } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Get whitelisted devices
     */
    val whitelistedDevices: StateFlow<List<NetworkDevice>> = _networkDevices
        .map { devices -> 
            val whitelistedMacs = devicePreferenceRepository.getWhitelistedDevices()
            devices.filter { whitelistedMacs.contains(it.macAddress) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Get all devices
     */
    val devices: StateFlow<List<NetworkDevice>> = networkDevices

    private val _dnsSearchQuery = MutableStateFlow("")
    val dnsSearchQuery: StateFlow<String> = _dnsSearchQuery.asStateFlow()

    /**
     * Get all active DNS spoofing sessions
     */
    val dnsSessions: StateFlow<List<SpoofingSession.Dns>> = sessionManager.sessions
        .map { sessions -> 
            val dnsOnly = sessions.filterIsInstance<SpoofingSession.Dns>()
            val query = _dnsSearchQuery.value
            if (query.isBlank()) {
                dnsOnly
            } else {
                dnsOnly.filter { 
                    it.domain.contains(query, ignoreCase = true) || 
                    it.spoofedIP.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Get all active DHCP spoofing sessions
     */
    val dhcpSessions: StateFlow<List<SpoofingSession.Dhcp>> = sessionManager.sessions
        .map { sessions -> sessions.filterIsInstance<SpoofingSession.Dhcp>() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        checkRootAccessInternal()
        refreshLogCount()
        refreshDetectedInterface()
        loadCachedDevicePreferences()
    }

    /**
     * Load all cached device preferences on app startup
     * This ensures device names persist even before first scan after reboot
     */
    private fun loadCachedDevicePreferences() {
        viewModelScope.launch {
            try {
                // Only load cached devices if we don't have any devices yet
                if (_networkDevices.value.isNotEmpty()) {
                    com.vishal.harpy.core.utils.LogUtils.d("NetworkMonitorVM", "Skipping cached load - devices already present")
                    return@launch
                }
                
                val allPreferences = devicePreferenceRepository.getAllDevicePreferences()
                if (allPreferences.isNotEmpty()) {
                    // Create NetworkDevice objects from cached preferences
                    val cachedDevices = allPreferences.map { pref ->
                        NetworkDevice(
                            ipAddress = "Unknown",
                            macAddress = pref.macAddress,
                            deviceName = pref.deviceName,
                            isPinned = pref.isPinned,
                            isBlocked = pref.isBlocked
                        )
                    }
                    _networkDevices.value = cachedDevices
                    applyFilters()
                    com.vishal.harpy.core.utils.LogUtils.d("NetworkMonitorVM", "Loaded ${cachedDevices.size} cached device preferences")
                }
            } catch (e: Exception) {
                com.vishal.harpy.core.utils.LogUtils.e("NetworkMonitorVM", "Error loading cached preferences: ${e.message}")
            }
        }
    }

    private fun refreshDetectedInterface() {
        val iface = getActiveInterfaceUseCase()
        _detectedInterface.value = iface
        _detectedIp.value = getOurIpUseCase(iface)
    }

    fun updateInterface(interfaceName: String) {
        viewModelScope.launch {
            settingsRepository.updateNetworkInterface(interfaceName)
            _detectedInterface.value = getActiveInterfaceUseCase()
        }
    }

    private fun checkRootAccessInternal() {
        viewModelScope.launch {
            _loadingState.value = LoadingState.Scanning
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
                _loadingState.value = LoadingState.None
            }
        }
    }

    fun scanNetwork() {
        if (!_isRooted.value) {
            _error.value = "Device is not rooted. Root access is required to scan the network."
            return
        }

        viewModelScope.launch {
            _loadingState.value = LoadingState.Scanning
            _error.value = null
            refreshDetectedInterface()
            try {
                val result = scanNetworkUseCase(appSettings.value.networkInterface)
                when (result) {
                    is NetworkResult.Success -> {
                        // Load preferences for each device
                        val devicesWithPreferences = result.data.map { device ->
                            val preference =
                                devicePreferenceRepository.getDevicePreference(device.macAddress)
                            device.copy(
                                deviceName = preference?.deviceName,
                                isPinned = preference?.isPinned ?: false,
                                isBlocked = preference?.isBlocked ?: false
                            )
                        }

                        // Merge with cached devices not in current scan to preserve names
                        val currentMacs = devicesWithPreferences.map { it.macAddress }.toSet()
                        val cachedDevices = _networkDevices.value.filter { !currentMacs.contains(it.macAddress) }
                        val mergedDevices = devicesWithPreferences + cachedDevices

                        // Sort: current device first, then saved names, then pinned, then IP
                        val sortedDevices = mergedDevices.sortedWith(
                            compareBy(
                                { !it.isCurrentDevice },     // Current device first (false < true)
                                { it.deviceName == null },  // Devices with names first (false < true)
                                { !it.isPinned },            // Then pinned devices
                                { it.ipAddress }             // Then by IP address
                            )
                        )

                        // Final fail-safe: Ensure unique MAC addresses to prevent UI crashes
                        val distinctDevices = sortedDevices.distinctBy { it.macAddress }
                        
                        _networkDevices.value = distinctDevices
                        applyFilters()
                        _scanSuccess.value = true
                        
                        // Verify and restore blocked devices
                        verifyAndRestoreBlockedDevices(sortedDevices)
                        
                        if (result.data.isEmpty()) {
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
                _loadingState.value = LoadingState.None
            }
        }
    }

    private fun verifyAndRestoreBlockedDevices(devices: List<NetworkDevice>) {
        viewModelScope.launch {
            try {
                // Clean up stale blocked devices (devices no longer on network)
                val currentMacs = devices.map { it.macAddress }
                devicePreferenceRepository.cleanupStaleBlockedDevices(currentMacs)
                
                // Check which devices are marked as blocked but not actively blocked
                val devicesToVerify = devices.filter { it.isBlocked }
                
                if (devicesToVerify.isEmpty()) {
                    return@launch
                }
                
                com.vishal.harpy.core.utils.LogUtils.d(
                    "NetworkMonitorVM",
                    "Verifying ${devicesToVerify.size} blocked devices"
                )
                
                // Verify each device's block status
                val devicesNeedingRestore = devicesToVerify.filter { device ->
                    !isDeviceBlockedUseCase(device.ipAddress)
                }
                
                if (devicesNeedingRestore.isNotEmpty()) {
                    com.vishal.harpy.core.utils.LogUtils.i(
                        "NetworkMonitorVM",
                        "Restoring ${devicesNeedingRestore.size} device blocks"
                    )
                    
                    // Restore blocks for devices that should be blocked
                    val restoreResult = restoreBlockedDevicesUseCase(devicesNeedingRestore, appSettings.value.networkInterface)
                    
                    when (restoreResult) {
                        is NetworkResult.Success -> {
                            val restoredCount = restoreResult.data
                            if (restoredCount > 0) {
                                com.vishal.harpy.core.utils.LogUtils.i(
                                    "NetworkMonitorVM",
                                    "Successfully restored $restoredCount device blocks"
                                )
                            }
                        }
                        is NetworkResult.Error -> {
                            com.vishal.harpy.core.utils.LogUtils.e(
                                "NetworkMonitorVM",
                                "Error restoring blocks: ${restoreResult.error.message}"
                            )
                        }
                    }
                } else {
                    com.vishal.harpy.core.utils.LogUtils.d(
                        "NetworkMonitorVM",
                        "All blocked devices are actively blocked"
                    )
                }
            } catch (e: Exception) {
                com.vishal.harpy.core.utils.LogUtils.e(
                    "NetworkMonitorVM",
                    "Error verifying blocked devices: ${e.message}"
                )
            }
        }
    }

    fun blockDevice(device: NetworkDevice) {
        if (!_isRooted.value) return

        viewModelScope.launch {
            _loadingState.value = LoadingState.Blocking
            _error.value = null
            try {
                Log.d(TAG, "Blocking device ${device.ipAddress} using method: ${appSettings.value.blockingMethod}")
                val result = blockDeviceUseCase(device, appSettings.value.networkInterface, appSettings.value.blockingMethod)
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            // Persist blocked state
                            devicePreferenceRepository.setBlockedStatus(device.macAddress, true)
                            
                            _networkDevices.value = _networkDevices.value.map {
                                if (it.ipAddress == device.ipAddress) {
                                    it.copy(isBlocked = true)
                                } else {
                                    it
                                }
                            }
                            applyFilters()
                            Log.d(TAG, "✓ Device blocked successfully")
                        }
                    }

                    is NetworkResult.Error -> {
                        Log.e(TAG, "✗ Failed to block device: ${result.error.message}")
                        _lastError.value = result.error
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Block device exception: ${e.message}", e)
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = e.message
            } finally {
                _loadingState.value = LoadingState.None
            }
        }
    }

    fun unblockDevice(device: NetworkDevice) {
        if (!_isRooted.value) return

        viewModelScope.launch {
            _loadingState.value = LoadingState.Unblocking
            _error.value = null
            try {
                val result = unblockDeviceUseCase(device, appSettings.value.networkInterface)
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            // Persist unblocked state
                            devicePreferenceRepository.setBlockedStatus(device.macAddress, false)
                            
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
                _loadingState.value = LoadingState.None
            }
        }
    }

    fun unblockAllDevices() {
        if (!_isRooted.value) return

        viewModelScope.launch {
            _loadingState.value = LoadingState.Unblocking
            _error.value = null
            try {
                val result = unblockAllDevicesUseCase(appSettings.value.networkInterface)
                when (result) {
                    is NetworkResult.Success -> {
                        val unblockCount = result.data
                        
                        // Persist unblocked state for all devices
                        devicePreferenceRepository.unblockAllDevices()
                        
                        // Update all devices in memory
                        _networkDevices.value = _networkDevices.value.map {
                            it.copy(isBlocked = false)
                        }
                        applyFilters()
                        
                        _error.value = "Unblocked $unblockCount device(s)"
                        com.vishal.harpy.core.utils.LogUtils.i("NetworkMonitorVM", "Unblocked $unblockCount devices")
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
                _loadingState.value = LoadingState.None
            }
        }
    }

    fun mapNetworkTopology() {
        if (!_isRooted.value) return

        viewModelScope.launch {
            _loadingState.value = LoadingState.MappingTopology
            _error.value = null
            try {
                val result = mapNetworkTopologyUseCase(appSettings.value.networkInterface)
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
                _loadingState.value = LoadingState.None
            }
        }
    }

    fun testPing(device: NetworkDevice) {
        viewModelScope.launch {
            _loadingState.value = LoadingState.TestingPing
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
                _loadingState.value = LoadingState.None
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
            try {
                // Save to repository first - MUST await completion before updating UI
                devicePreferenceRepository.setDeviceName(device.macAddress, deviceName)
                
                // Update the device in the list
                _networkDevices.value = _networkDevices.value.map {
                    if (it.macAddress == device.macAddress) {
                        it.copy(deviceName = deviceName)
                    } else {
                        it
                    }
                }
                
                // Apply filters to update the filtered list
                applyFilters()
                
                com.vishal.harpy.core.utils.LogUtils.d("NetworkMonitorVM", "Device name set for ${device.macAddress}: $deviceName")
            } catch (e: Exception) {
                com.vishal.harpy.core.utils.LogUtils.e("NetworkMonitorVM", "Error setting device name: ${e.message}")
                _error.value = "Failed to set device name: ${e.message}"
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
            // Re-sort: current device first, then saved names, then pinned, then IP
            val sortedDevices = updatedDevices.sortedWith(
                compareBy(
                    { !it.isCurrentDevice },     // Current device first (false < true)
                    { it.deviceName == null },  // Devices with names first (false < true)
                    { !it.isPinned },            // Then pinned devices
                    { it.ipAddress }             // Then by IP address
                )
            )
            _networkDevices.value = sortedDevices
            applyFilters()
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
    fun startDNSSpoofing(domain: String, spoofedIP: String, interfaceName: String? = null) {
        if (!_isRooted.value) {
            _error.value = "Root access is required for DNS spoofing"
            return
        }

        val activeIface = interfaceName ?: appSettings.value.networkInterface

        viewModelScope.launch {
            _loadingState.value = LoadingState.DNSSpoofing
            _error.value = null

            try {
                // Check if session for this domain already exists
                val existingSession = sessionManager.sessions.value
                    .filterIsInstance<SpoofingSession.Dns>()
                    .find { it.domain == domain }

                if (existingSession == null) {
                    val session = SpoofingSession.Dns(
                        domain = domain,
                        spoofedIP = spoofedIP,
                        interfaceName = activeIface,
                        isActive = false,
                        startTime = null
                    )
                    sessionManager.addSession(session)
                }

                val result = startDnsSpoofingUseCase(domain, spoofedIP, activeIface)
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            val sessionToUpdate = sessionManager.sessions.value
                                .filterIsInstance<SpoofingSession.Dns>()
                                .find { it.domain == domain }
                            
                            sessionToUpdate?.let {
                                val activeSession = it.copy(
                                    isActive = true,
                                    startTime = java.time.LocalDateTime.now()
                                )
                                sessionManager.updateSession(activeSession)
                            }
                            com.vishal.harpy.core.utils.LogUtils.i("NetworkMonitorVM", "DNS spoofing started for $domain -> $spoofedIP")
                            _error.value = null
                        } else {
                            _error.value = "Failed to start DNS spoofing"
                        }
                    }
                    is NetworkResult.Error -> {
                        _lastError.value = result.error
                        _error.value = "DNS spoofing failed: ${result.error.message}"
                        // Only remove if it was just created and didn't exist before
                        // (Usually we keep rules now, so we can just leave it inactive)
                    }
                }
            } catch (e: Exception) {
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = "DNS spoofing error: ${e.message}"
            } finally {
                _loadingState.value = LoadingState.None
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
            _loadingState.value = LoadingState.DNSSpoofing
            _error.value = null
            try {
                // Find session first
                val sessionToUpdate = sessionManager.sessions.value
                    .filterIsInstance<SpoofingSession.Dns>()
                    .find { it.domain == domain }

                val result = stopDnsSpoofingUseCase(domain)
                
                // Mark as inactive instead of removing
                sessionToUpdate?.let { 
                    sessionManager.updateSession(it.copy(isActive = false))
                }
                
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            com.vishal.harpy.core.utils.LogUtils.i("NetworkMonitorVM", "DNS spoofing stopped for $domain")
                            _error.value = "DNS spoofing stopped for $domain"
                        } else {
                            _error.value = "DNS spoofing for $domain was already stopped"
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
                _loadingState.value = LoadingState.None
            }
        }
    }

    /**
     * Permanently remove a DNS spoofing rule
     */
    fun removeDNSRule(domain: String) {
        val session = sessionManager.sessions.value
            .filterIsInstance<SpoofingSession.Dns>()
            .find { it.domain == domain }
        
        session?.let { 
            viewModelScope.launch {
                if (it.isActive) {
                    stopDNSSpoofing(it.domain)
                }
                sessionManager.removeSession(it.id)
            }
        }
    }

    fun setDnsSearchQuery(query: String) {
        _dnsSearchQuery.value = query
    }

    /**
     * Stop all DNS spoofing processes
     */
    fun stopAllDNSSpoofing() {
        viewModelScope.launch {
            val activeDns = sessionManager.sessions.value
                .filterIsInstance<SpoofingSession.Dns>()
                .filter { it.isActive }
            
            activeDns.forEach { session ->
                stopDNSSpoofing(session.domain)
            }
            
            if (activeDns.isNotEmpty()) {
                _error.value = "Stopped ${activeDns.size} DNS spoofing processes"
            }
        }
    }

    /**
     * Remove all stopped DNS rules
     */
    fun clearInactiveDNSRules() {
        val inactiveRules = sessionManager.sessions.value
            .filterIsInstance<SpoofingSession.Dns>()
            .filter { !it.isActive }
        
        inactiveRules.forEach { rule ->
            sessionManager.removeSession(rule.id)
        }
    }

    /**
     * Check if DNS spoofing is active for a domain
     */
    fun isDNSSpoofingActive(domain: String): Boolean {
        return isDnsSpoofingActiveUseCase(domain)
    }


    /**
     * Check if DHCP spoofing is active
     */
    fun isDHCPSpoofingActive(): Boolean {
        return isDhcpSpoofingActiveUseCase()
    }

    /**
     * Start DHCP spoofing for specific devices
     */
    fun startDHCPSpoofing(
        interfaceName: String? = null,
        targetMacs: Array<String>,
        spoofedIPs: Array<String>,
        gatewayIPs: Array<String>,
        subnetMasks: Array<String>,
        dnsServers: Array<String>
    ) {
        if (!_isRooted.value) {
            _error.value = "Root access is required for DHCP spoofing"
            Log.e(TAG, "DHCP spoofing requires root access")
            return
        }

        val activeIface = interfaceName ?: appSettings.value.networkInterface
        Log.d(TAG, "Starting DHCP spoofing on interface: $activeIface with ${targetMacs.size} device(s)")

        viewModelScope.launch {
            _loadingState.value = LoadingState.DHCPSpoofing
            _error.value = null

            val dhcpRules = targetMacs.mapIndexed { index, mac ->
                Log.d(TAG, "  Rule $index: $mac -> ${spoofedIPs[index]} (gw: ${gatewayIPs[index]}, mask: ${subnetMasks[index]}, dns: ${dnsServers[index]})")
                DhcpSpoofingRule(
                    targetMac = mac,
                    spoofedIP = spoofedIPs[index],
                    gatewayIP = gatewayIPs[index],
                    subnetMask = subnetMasks[index],
                    dnsServer = dnsServers[index]
                )
            }

            val session = SpoofingSession.Dhcp(
                interfaceName = activeIface,
                rules = dhcpRules,
                isActive = false,
                startTime = null
            )
            sessionManager.addSession(session)
            Log.d(TAG, "Created DHCP session: ${session.id}")

            try {
                val result = startDhcpSpoofingUseCase(
                    activeIface,
                    targetMacs,
                    spoofedIPs,
                    gatewayIPs,
                    subnetMasks,
                    dnsServers
                )
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            val activeSession = session.copy(
                                isActive = true,
                                startTime = java.time.LocalDateTime.now()
                            )
                            sessionManager.updateSession(activeSession)
                            Log.d(TAG, "✓ DHCP spoofing started successfully for ${targetMacs.size} devices")
                            com.vishal.harpy.core.utils.LogUtils.i("NetworkMonitorVM", "DHCP spoofing started for ${targetMacs.size} devices")
                            _error.value = null
                        } else {
                            Log.e(TAG, "✗ DHCP spoofing returned false")
                            _error.value = "Failed to start DHCP spoofing"
                            sessionManager.removeSession(session.id)
                        }
                    }
                    is NetworkResult.Error -> {
                        Log.e(TAG, "✗ DHCP spoofing error: ${result.error.message}")
                        _lastError.value = result.error
                        _error.value = "DHCP spoofing failed: ${result.error.message}"
                        sessionManager.removeSession(session.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ DHCP spoofing exception: ${e.message}", e)
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = "DHCP spoofing error: ${e.message}"
                sessionManager.removeSession(session.id)
            } finally {
                _loadingState.value = LoadingState.None
            }
        }
    }

    /**
     * Stop DHCP spoofing
     */
    fun stopDHCPSpoofing() {
        if (!_isRooted.value) {
            _error.value = "Root access is required for DHCP spoofing"
            Log.e(TAG, "Stop DHCP spoofing requires root access")
            return
        }

        Log.d(TAG, "Stopping DHCP spoofing")

        viewModelScope.launch {
            _loadingState.value = LoadingState.DHCPSpoofing
            _error.value = null
            try {
                val result = stopDhcpSpoofingUseCase()
                when (result) {
                    is NetworkResult.Success -> {
                        if (result.data) {
                            // Mark ALL active DHCP sessions as inactive
                            val activeDhcpSessions = sessionManager.sessions.value
                                .filterIsInstance<SpoofingSession.Dhcp>()
                                .filter { it.isActive }
                            
                            Log.d(TAG, "Found ${activeDhcpSessions.size} active DHCP session(s)")
                            activeDhcpSessions.forEach { session ->
                                Log.d(TAG, "Marking session ${session.id} as inactive")
                                sessionManager.updateSession(session.copy(isActive = false))
                            }
                            
                            Log.d(TAG, "✓ DHCP spoofing stopped successfully")
                            com.vishal.harpy.core.utils.LogUtils.i("NetworkMonitorVM", "DHCP spoofing stopped")
                            _error.value = "DHCP spoofing stopped"
                        } else {
                            Log.w(TAG, "No active DHCP spoofing found")
                            _error.value = "No active DHCP spoofing found"
                        }
                    }
                    is NetworkResult.Error -> {
                        Log.e(TAG, "✗ Stop DHCP spoofing error: ${result.error.message}")
                        _lastError.value = result.error
                        _error.value = "Stop DHCP spoofing failed: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Stop DHCP spoofing exception: ${e.message}", e)
                val error = NetworkError.UnknownError(e)
                _lastError.value = error
                _error.value = "Stop DHCP spoofing error: ${e.message}"
            } finally {
                _loadingState.value = LoadingState.None
            }
        }
    }

    /**
     * Reset the scan success flag
     */
    fun resetScanSuccess() {
        _scanSuccess.value = false
    }

    /**
     * Check root access status
     */
    fun checkRootAccess() {
        viewModelScope.launch {
            _loadingState.value = LoadingState.Scanning
            _error.value = null
            try {
                val result = isDeviceRootedUseCase()
                when (result) {
                    is NetworkResult.Success -> {
                        _isRooted.value = result.data
                        if (result.data) {
                            com.vishal.harpy.core.utils.LogUtils.i("NetworkMonitorVM", "Root access confirmed")
                            _error.value = "Root access confirmed"
                        } else {
                            com.vishal.harpy.core.utils.LogUtils.w("NetworkMonitorVM", "Root access not available")
                            _error.value = "Root access not available"
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
                _loadingState.value = LoadingState.None
            }
        }
    }

    private fun refreshLogCount() {
        _logCount.value = com.vishal.harpy.core.utils.LogUtils.getLogCount(context)
    }

    /**
     * Delete all log files
     */
    fun cleanLogs() {
        viewModelScope.launch {
            if (com.vishal.harpy.core.utils.LogUtils.cleanAllLogs(context)) {
                refreshLogCount()
            }
        }
    }

    /**
     * Clear current log file content
     */
    fun clearCurrentLog() {
        viewModelScope.launch {
            if (com.vishal.harpy.core.utils.LogUtils.clearCurrentLog(context)) {
                refreshLogCount()
            }
        }
    }

    /**
     * Update scan timeout setting
     */
    fun updateScanTimeout(timeout: Int) {
        viewModelScope.launch {
            settingsRepository.updateScanTimeout(timeout)
        }
    }

    /**
     * Update network interface setting
     */
    fun updateNetworkInterface(interfaceName: String) {
        viewModelScope.launch {
            settingsRepository.updateNetworkInterface(interfaceName)
        }
    }

    /**
     * Update debug mode setting
     */
    fun updateDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateDebugMode(enabled)
        }
    }

    /**
     * Update verbose logging setting
     */
    fun updateVerboseLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateVerboseLogging(enabled)
        }
    }

    /**
     * Update DNS settings
     */
    fun updateDnsSettings(customDns: String, fallbackDns: String) {
        viewModelScope.launch {
            settingsRepository.updateDnsSettings(customDns, fallbackDns)
        }
    }

    /**
     * Update DHCP lease time setting
     */
    fun updateDhcpLeaseTime(leaseTimeSeconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateDhcpLeaseTime(leaseTimeSeconds)
        }
    }

    /**
     * Update whitelist enabled setting
     */
    fun updateWhitelistEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateWhitelistEnabled(enabled)
        }
    }

    /**
     * Update blocking method setting
     */
    fun updateBlockingMethod(method: com.vishal.harpy.core.utils.BlockingMethod) {
        viewModelScope.launch {
            settingsRepository.updateBlockingMethod(method)
        }
    }

    /**
     * Get available network interfaces on device
     */
    fun getAvailableNetworkInterfaces(): List<String> {
        return try {
            val interfaces = mutableListOf<String>()
            val networkInterfaces = java.net.NetworkInterface.getNetworkInterfaces()
            
            while (networkInterfaces.hasMoreElements()) {
                val iface = networkInterfaces.nextElement()
                val name = iface.name
                
                // Filter out virtual/tunnel interfaces, keep real network interfaces
                if (!name.startsWith("tun") && !name.startsWith("tap") && 
                    !name.startsWith("ppp") && !name.startsWith("lo") &&
                    iface.isUp) {
                    interfaces.add(name)
                }
            }
            
            // Sort with common interfaces first
            interfaces.sortedWith(compareBy { name ->
                when {
                    name == "wlan0" -> 0
                    name == "eth0" -> 1
                    name.startsWith("wlan") -> 2
                    name.startsWith("eth") -> 3
                    name.startsWith("rmnet") -> 4
                    else -> 5
                }
            })
        } catch (e: Exception) {
            com.vishal.harpy.core.utils.LogUtils.e("NetworkMonitorVM", "Error getting network interfaces: ${e.message}")
            listOf("wlan0", "eth0", "rmnet0")
        }
    }

    /**
     * Add device to whitelist
     */
    fun addToWhitelist(device: NetworkDevice) {
        viewModelScope.launch {
            try {
                devicePreferenceRepository.addToWhitelist(device.macAddress)
                com.vishal.harpy.core.utils.LogUtils.d("NetworkMonitorVM", "Added ${device.deviceName} to whitelist")
            } catch (e: Exception) {
                _error.value = "Failed to add device to whitelist: ${e.message}"
            }
        }
    }

    /**
     * Remove device from whitelist
     */
    fun removeFromWhitelist(device: NetworkDevice) {
        viewModelScope.launch {
            try {
                devicePreferenceRepository.removeFromWhitelist(device.macAddress)
                com.vishal.harpy.core.utils.LogUtils.d("NetworkMonitorVM", "Removed ${device.deviceName} from whitelist")
            } catch (e: Exception) {
                _error.value = "Failed to remove device from whitelist: ${e.message}"
            }
        }
    }

    /**
     * Remove all stopped DHCP rules
     */
    fun clearInactiveDHCPRules() {
        val inactiveRules = sessionManager.sessions.value
            .filterIsInstance<SpoofingSession.Dhcp>()
            .filter { !it.isActive }
        
        inactiveRules.forEach { rule ->
            sessionManager.removeSession(rule.id)
        }
    }

    /**
     * Remove a specific DHCP rule
     */
    fun removeDHCPRule(ruleId: String) {
        val session = sessionManager.getSession(ruleId)
        
        session?.let { 
            viewModelScope.launch {
                if ((it as? SpoofingSession.Dhcp)?.isActive == true) {
                    stopDHCPSpoofing()
                }
                sessionManager.removeSession(it.id)
            }
        }
    }
}
