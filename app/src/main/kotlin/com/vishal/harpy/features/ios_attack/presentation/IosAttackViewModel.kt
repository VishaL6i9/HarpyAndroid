package com.vishal.harpy.features.ios_attack.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vishal.harpy.core.utils.AppSettings
import com.vishal.harpy.core.utils.NetworkResult
import com.vishal.harpy.core.utils.SettingsRepository
import com.vishal.harpy.features.ios_attack.domain.IosAttackConfig
import com.vishal.harpy.features.ios_attack.domain.IosAttackRepository
import com.vishal.harpy.features.ios_attack.domain.IosAttackType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IosAttackUiState(
    val isDhcpSelfGatewayActive: Boolean = false,
    val isDnsNullifyActive: Boolean = false,
    val isIcmpRedirectActive: Boolean = false,
    val isTcpRstActive: Boolean = false,
    val targetMac: String = "",
    val targetIp: String = "",
    val routerIp: String = "",
    val routerMac: String = "",
    val interfaceName: String = "wlan0",
    val errorMessage: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class IosAttackViewModel @Inject constructor(
    private val attackRepository: IosAttackRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IosAttackUiState())
    val uiState: StateFlow<IosAttackUiState> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val appSettings: StateFlow<AppSettings> = settingsRepository.settings

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    interfaceName = settings.networkInterface,
                    routerIp = settings.networkInterface.let {
                        // Auto-detect gateway from settings context
                        "192.168.1.1"
                    }
                )
            }
        }
    }

    fun updateTargetMac(mac: String) {
        _uiState.value = _uiState.value.copy(targetMac = mac)
    }

    fun updateTargetIp(ip: String) {
        _uiState.value = _uiState.value.copy(targetIp = ip)
    }

    fun updateRouterIp(ip: String) {
        _uiState.value = _uiState.value.copy(routerIp = ip)
    }

    fun updateRouterMac(mac: String) {
        _uiState.value = _uiState.value.copy(routerMac = mac)
    }

    fun updateInterface(iface: String) {
        _uiState.value = _uiState.value.copy(interfaceName = iface)
    }

    fun refreshState() {
        _uiState.value = _uiState.value.copy(
            isDhcpSelfGatewayActive = attackRepository.isAttackActive(IosAttackType.DHCP_SELF_GATEWAY),
            isDnsNullifyActive = attackRepository.isAttackActive(IosAttackType.DNS_NULLIFY),
            isIcmpRedirectActive = attackRepository.isAttackActive(IosAttackType.ICMP_REDIRECT),
            isTcpRstActive = attackRepository.isAttackActive(IosAttackType.TCP_RST)
        )
    }

    fun startDhcpSelfGateway() {
        val state = _uiState.value
        if (state.targetMac.isBlank() || state.routerIp.isBlank() || state.routerMac.isBlank()) {
            _error.value = "Target MAC, Router IP, and Router MAC are required"
            return
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val config = IosAttackConfig(
                attackType = IosAttackType.DHCP_SELF_GATEWAY,
                targetMac = state.targetMac,
                targetIp = state.targetIp,
                routerIp = state.routerIp,
                routerMac = state.routerMac,
                interfaceName = state.interfaceName
            )
            when (val result = attackRepository.startDhcpSelfGateway(config)) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isDhcpSelfGatewayActive = true,
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }

    fun startDnsNullification() {
        val state = _uiState.value
        if (state.targetMac.isBlank() || state.routerIp.isBlank() || state.routerMac.isBlank()) {
            _error.value = "Target MAC, Router IP, and Router MAC are required"
            return
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val config = IosAttackConfig(
                attackType = IosAttackType.DNS_NULLIFY,
                targetMac = state.targetMac,
                targetIp = state.targetIp,
                routerIp = state.routerIp,
                routerMac = state.routerMac,
                interfaceName = state.interfaceName
            )
            when (val result = attackRepository.startDnsNullification(config)) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isDnsNullifyActive = true,
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }

    fun startIcmpRedirect() {
        val state = _uiState.value
        if (state.targetMac.isBlank() || state.targetIp.isBlank() || state.routerIp.isBlank() || state.routerMac.isBlank()) {
            _error.value = "Target MAC, Target IP, Router IP, and Router MAC are required"
            return
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val config = IosAttackConfig(
                attackType = IosAttackType.ICMP_REDIRECT,
                targetMac = state.targetMac,
                targetIp = state.targetIp,
                routerIp = state.routerIp,
                routerMac = state.routerMac,
                interfaceName = state.interfaceName,
                redirectAll = true
            )
            when (val result = attackRepository.startIcmpRedirect(config)) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isIcmpRedirectActive = true,
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }

    fun startTcpRst() {
        val state = _uiState.value
        if (state.targetMac.isBlank() || state.targetIp.isBlank()) {
            _error.value = "Target MAC and Target IP are required"
            return
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val config = IosAttackConfig(
                attackType = IosAttackType.TCP_RST,
                targetMac = state.targetMac,
                targetIp = state.targetIp,
                routerIp = state.routerIp,
                routerMac = state.routerMac,
                interfaceName = state.interfaceName
            )
            when (val result = attackRepository.startTcpRst(config)) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isTcpRstActive = true,
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.error.message
                    )
                }
            }
        }
    }

    fun stopDhcpSelfGateway() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            attackRepository.stopAttack(IosAttackType.DHCP_SELF_GATEWAY)
            _uiState.value = _uiState.value.copy(
                isDhcpSelfGatewayActive = false,
                isLoading = false
            )
        }
    }

    fun stopDnsNullification() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            attackRepository.stopAttack(IosAttackType.DNS_NULLIFY)
            _uiState.value = _uiState.value.copy(
                isDnsNullifyActive = false,
                isLoading = false
            )
        }
    }

    fun stopIcmpRedirect() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            attackRepository.stopAttack(IosAttackType.ICMP_REDIRECT)
            _uiState.value = _uiState.value.copy(
                isIcmpRedirectActive = false,
                isLoading = false
            )
        }
    }

    fun stopTcpRst() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            attackRepository.stopAttack(IosAttackType.TCP_RST)
            _uiState.value = _uiState.value.copy(
                isTcpRstActive = false,
                isLoading = false
            )
        }
    }

    fun autoFillRouter(ip: String, mac: String) {
        val state = _uiState.value
        val updated = state.copy(
            routerIp = if (state.routerIp.isBlank() || state.routerIp == "192.168.1.1") ip else state.routerIp,
            routerMac = if (state.routerMac.isBlank()) mac else state.routerMac
        )
        if (updated != state) {
            _uiState.value = updated
        }
    }

    fun autoFillTargetInfo(targetIp: String, targetMac: String) {
        val state = _uiState.value
        val updated = state.copy(
            targetIp = if (state.targetIp.isBlank()) targetIp else state.targetIp,
            targetMac = if (state.targetMac.isBlank()) targetMac else state.targetMac
        )
        if (updated != state) {
            _uiState.value = updated
        }
    }

    fun clearError() {
        _error.value = null
    }
}
