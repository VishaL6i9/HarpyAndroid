package com.vishal.harpy.features.network_monitor.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.harpy.features.network_monitor.domain.usecases.ScanNetworkUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.IsDeviceRootedUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.BlockDeviceUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.UnblockDeviceUseCase
import com.vishal.harpy.features.network_monitor.domain.usecases.MapNetworkTopologyUseCase
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
    
    private val _filterIPv4 = MutableStateFlow(true)
    val filterIPv4: StateFlow<Boolean> = _filterIPv4.asStateFlow()
    
    private val _filterIPv6 = MutableStateFlow(false)
    val filterIPv6: StateFlow<Boolean> = _filterIPv6.asStateFlow()
    
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
                            val preference = devicePreferenceRepository.getDevicePreference(device.macAddress)
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
        _filterIPv4.value = !_filterIPv4.value
        applyFilters()
    }

    /**
     * Toggle IPv6 filter
     */
    fun toggleIPv6Filter() {
        _filterIPv6.value = !_filterIPv6.value
        applyFilters()
    }

    /**
     * Apply current filters to the device list
     */
    private fun applyFilters() {
        val filtered = _networkDevices.value.filter { device ->
            val isIPv4 = device.ipAddress.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))
            val isIPv6 = !isIPv4
            
            (isIPv4 && _filterIPv4.value) || (isIPv6 && _filterIPv6.value)
        }
        _filteredDevices.value = filtered
    }