package com.vishal.harpy.features.spoofing.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.harpy.core.state.SpoofingSessionManager
import com.vishal.harpy.core.state.SessionStats
import com.vishal.harpy.core.utils.SpoofingSession
import com.vishal.harpy.core.utils.DhcpSpoofingRule
import com.vishal.harpy.core.utils.NetworkResult
import com.vishal.harpy.features.dns.domain.usecases.StartDnsSpoofingUseCase
import com.vishal.harpy.features.dns.domain.usecases.StopDnsSpoofingUseCase
import com.vishal.harpy.features.dns.domain.usecases.IsDnsSpoofingActiveUseCase
import com.vishal.harpy.features.dhcp.domain.usecases.StartDhcpSpoofingUseCase
import com.vishal.harpy.features.dhcp.domain.usecases.StopDhcpSpoofingUseCase
import com.vishal.harpy.features.dhcp.domain.usecases.IsDhcpSpoofingActiveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class SpoofingManagementViewModel @Inject constructor(
    private val sessionManager: SpoofingSessionManager,
    private val startDnsSpoofingUseCase: StartDnsSpoofingUseCase,
    private val stopDnsSpoofingUseCase: StopDnsSpoofingUseCase,
    private val isDnsSpoofingActiveUseCase: IsDnsSpoofingActiveUseCase,
    private val startDhcpSpoofingUseCase: StartDhcpSpoofingUseCase,
    private val stopDhcpSpoofingUseCase: StopDhcpSpoofingUseCase,
    private val isDhcpSpoofingActiveUseCase: IsDhcpSpoofingActiveUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val sessions = sessionManager.sessions
    val activeSessions = sessionManager.activeSessions

    val sessionStats: StateFlow<SessionStats> = sessionManager.sessionStats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        // No manual stats update needed, observed via flow
    }

    fun startDnsSpoofing(domain: String, spoofedIP: String, interfaceName: String = "wlan0") {
        viewModelScope.launch {
            _isLoading.value = true
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
                        interfaceName = interfaceName,
                        isActive = false,
                        startTime = null
                    )
                    sessionManager.addSession(session)
                }

                val result = startDnsSpoofingUseCase(domain, spoofedIP, interfaceName)

                when (result) {
                    is NetworkResult.Success -> {
                        val sessionToUpdate = sessionManager.sessions.value
                            .filterIsInstance<SpoofingSession.Dns>()
                            .find { it.domain == domain }

                        sessionToUpdate?.let {
                            val activeSession = it.copy(
                                isActive = true,
                                startTime = LocalDateTime.now()
                            )
                            sessionManager.updateSession(activeSession)
                        }
                        _successMessage.value = "DNS spoofing started for $domain"
                    }
                    is NetworkResult.Error -> {
                        _error.value = "Failed to start DNS spoofing: ${result.error.message}"
                    }
                }
            } catch (e: Exception) {
                _error.value = "DNS spoofing error: ${e.message}"
            }

            _isLoading.value = false
        }
    }

    fun stopDnsSpoofing(sessionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val session = sessionManager.getSession(sessionId) as? SpoofingSession.Dns
            if (session != null) {
                val result = stopDnsSpoofingUseCase(session.domain)

                when (result) {
                    is NetworkResult.Success -> {
                        sessionManager.updateSession(session.copy(isActive = false))
                        _successMessage.value = "DNS spoofing stopped for ${session.domain}"
                    }
                    is NetworkResult.Error -> {
                        _error.value = "Failed to stop DNS spoofing: ${result.error.message}"
                    }
                }
            }

            _isLoading.value = false
        }
    }

    fun startDhcpSpoofing(
        rules: List<DhcpSpoofingRule>,
        interfaceName: String = "wlan0"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val session = SpoofingSession.Dhcp(
                interfaceName = interfaceName,
                rules = rules,
                isActive = false,
                startTime = null
            )

            sessionManager.addSession(session)

            val result = startDhcpSpoofingUseCase(
                interfaceName = interfaceName,
                targetMacs = rules.map { it.targetMac }.toTypedArray(),
                spoofedIPs = rules.map { it.spoofedIP }.toTypedArray(),
                gatewayIPs = rules.map { it.gatewayIP }.toTypedArray(),
                subnetMasks = rules.map { it.subnetMask }.toTypedArray(),
                dnsServers = rules.map { it.dnsServer }.toTypedArray()
            )

            when (result) {
                is NetworkResult.Success -> {
                    val activeSession = session.copy(
                        isActive = true,
                        startTime = LocalDateTime.now()
                    )
                    sessionManager.updateSession(activeSession)
                    _successMessage.value = "DHCP spoofing started for ${rules.size} device(s)"
                }
                is NetworkResult.Error -> {
                    _error.value = "Failed to start DHCP spoofing: ${result.error.message}"
                    sessionManager.removeSession(session.id)
                }
            }

            _isLoading.value = false
        }
    }

    fun stopDhcpSpoofing(sessionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val session = sessionManager.getSession(sessionId) as? SpoofingSession.Dhcp
            if (session != null) {
                val result = stopDhcpSpoofingUseCase()

                when (result) {
                    is NetworkResult.Success -> {
                        sessionManager.updateSession(session.copy(isActive = false))
                        _successMessage.value = "DHCP spoofing stopped"
                    }
                    is NetworkResult.Error -> {
                        _error.value = "Failed to stop DHCP spoofing: ${result.error.message}"
                    }
                }
            }

            _isLoading.value = false
        }
    }

    fun stopAllSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val activeSessions = sessionManager.getActiveSessions()
            var successCount = 0
            var failureCount = 0

            for (session in activeSessions) {
                when (session) {
                    is SpoofingSession.Dns -> {
                        val result = stopDnsSpoofingUseCase(session.domain)
                        if (result is NetworkResult.Success) {
                            successCount++
                            sessionManager.removeSession(session.id)
                        } else {
                            failureCount++
                        }
                    }
                    is SpoofingSession.Dhcp -> {
                        val result = stopDhcpSpoofingUseCase()
                        if (result is NetworkResult.Success) {
                            successCount++
                            sessionManager.removeSession(session.id)
                        } else {
                            failureCount++
                        }
                    }
                }
            }

            if (failureCount == 0) {
                _successMessage.value = "All sessions stopped ($successCount)"
            } else {
                _error.value = "Stopped $successCount sessions, $failureCount failed"
            }

            _isLoading.value = false
        }
    }

    fun clearAllSessions() {
        sessionManager.clearAllSessions()
    }

    fun removeSession(sessionId: String) {
        sessionManager.removeSession(sessionId)
    }

    // Reactive flow replaces manual updateStats

    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }
}
