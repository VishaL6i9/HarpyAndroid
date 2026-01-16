package com.vishal.harpy.features.network_monitor.presentation.viewmodel

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
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val mapNetworkTopologyUseCase: MapNetworkTopologyUseCase
) : ViewModel() {
    
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
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun scanNetwork() {
        if (!_isRooted.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = scanNetworkUseCase()
                when (result) {
                    is NetworkResult.Success -> {
                        _networkDevices.value = result.data
                    }
                    is NetworkResult.Error -> {
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
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
                            // Update the device's blocked status
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
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
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
                            // Update the device's blocked status
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
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
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
                        _error.value = result.error.message
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}