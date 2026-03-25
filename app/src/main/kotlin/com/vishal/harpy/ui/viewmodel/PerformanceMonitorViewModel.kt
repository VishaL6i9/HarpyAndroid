package com.vishal.harpy.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.harpy.core.utils.NetworkConnection
import com.vishal.harpy.core.utils.PerformanceMonitor
import com.vishal.harpy.core.utils.ProcessInfo
import com.vishal.harpy.core.utils.SystemPerformanceMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for performance monitoring with ROOT access
 */
@HiltViewModel
class PerformanceMonitorViewModel @Inject constructor(
    private val performanceMonitor: PerformanceMonitor
) : ViewModel() {

    companion object {
        private const val TAG = "PerfMonitorVM"
    }

    private val _metrics = MutableStateFlow<SystemPerformanceMetrics?>(null)
    val metrics: StateFlow<SystemPerformanceMetrics?> = _metrics.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _processes = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val processes: StateFlow<List<ProcessInfo>> = _processes.asStateFlow()

    private val _networkConnections = MutableStateFlow<List<NetworkConnection>>(emptyList())
    val networkConnections: StateFlow<List<NetworkConnection>> = _networkConnections.asStateFlow()

    private var monitoringJob: Job? = null
    private var isUpdating = false

    init {
        Log.d(TAG, "ViewModel init started")
        checkRootAvailability()
    }

    /**
     * Check if root access is available
     */
    private fun checkRootAvailability() {
        Log.d(TAG, "Checking root availability...")
        viewModelScope.launch {
            _isRootAvailable.value = performanceMonitor.isRootAvailable()
            Log.d(TAG, "Root available: ${_isRootAvailable.value}")
        }
    }

    /**
     * Start continuous performance monitoring
     */
    fun startMonitoring(updateIntervalMs: Long = 3000L) {
        Log.d(TAG, "startMonitoring called, interval=${updateIntervalMs}ms, isMonitoring=${_isMonitoring.value}")
        
        if (monitoringJob?.isActive == true) {
            Log.d(TAG, "Monitoring already active, skipping")
            return
        }

        _isMonitoring.value = true
        _error.value = null

        Log.d(TAG, "Starting monitoring job")
        monitoringJob = viewModelScope.launch {
            Log.d(TAG, "Monitoring loop started")
            while (isActive) {
                updateMetrics()
                delay(updateIntervalMs)
            }
        }
    }

    /**
     * Stop continuous performance monitoring
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _isMonitoring.value = false
    }

    /**
     * Get a single performance metrics snapshot
     */
    fun refreshMetrics() {
        viewModelScope.launch {
            updateMetrics()
        }
    }

    /**
     * Get detailed process list
     */
    fun refreshProcesses() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val allProcesses = performanceMonitor.getAllProcesses()
                _processes.value = allProcesses.sortedByDescending { it.memoryVmRss }
            } catch (e: Exception) {
                _error.value = "Failed to get processes: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Get network connections
     */
    fun refreshNetworkConnections() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _networkConnections.value = performanceMonitor.getSystemPerformanceMetrics()
                    .networkConnections
            } catch (e: Exception) {
                _error.value = "Failed to get network connections: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Kill a process (requires root)
     */
    suspend fun killProcess(pid: Int, signal: String = "TERM"): Boolean {
        val result = performanceMonitor.killProcess(pid, signal)
        if (result.isSuccess) {
            refreshProcesses()
        } else {
            _error.value = "Failed to kill process $pid: ${result.error}"
        }
        return result.isSuccess
    }

    /**
     * Set process OOM score (requires root)
     */
    suspend fun setProcessOomScore(pid: Int, score: Int): Boolean {
        val result = performanceMonitor.setOomScoreAdj(pid, score)
        if (!result.isSuccess) {
            _error.value = "Failed to set OOM score: ${result.error}"
        }
        return result.isSuccess
    }

    /**
     * Update current metrics
     */
    private suspend fun updateMetrics() {
        // Prevent concurrent updates
        if (isUpdating) {
            Log.d(TAG, "updateMetrics: Already updating, skipping")
            return
        }
        
        isUpdating = true
        Log.d(TAG, "updateMetrics called")
        
        try {
            Log.d(TAG, "Fetching system performance metrics...")
            val currentMetrics = performanceMonitor.getSystemPerformanceMetrics()
            _metrics.value = currentMetrics
            _processes.value = currentMetrics.topProcesses
            _networkConnections.value = currentMetrics.networkConnections
            _error.value = null
            Log.d(TAG, "Metrics updated successfully")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected when monitoring is stopped - don't log as error
            Log.d(TAG, "updateMetrics: Cancelled (monitoring stopped)")
            isUpdating = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get metrics: ${e.message}", e)
            _error.value = "Failed to get metrics: ${e.message}"
            isUpdating = false
        } finally {
            if (isUpdating) {
                isUpdating = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}
