package com.vishal.harpy.core.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SpoofingState(
    val isDnsSpoofingActive: Boolean = false,
    val isDhcpSpoofingActive: Boolean = false
) {
    val activeModes: List<String>
        get() = buildList {
            if (isDnsSpoofingActive) add("DNS")
            if (isDhcpSpoofingActive) add("DHCP")
        }

    val statusText: String
        get() = when {
            activeModes.isEmpty() -> ""
            activeModes.size == 1 -> activeModes[0]
            else -> activeModes.joinToString(" + ")
        }

    val isAnySpoofingActive: Boolean
        get() = isDnsSpoofingActive || isDhcpSpoofingActive
}

@Singleton
class SpoofingStateManager @Inject constructor() {

    private val _spoofingState = MutableStateFlow(SpoofingState())
    val spoofingState: StateFlow<SpoofingState> = _spoofingState.asStateFlow()

    fun setDnsSpoofingActive(active: Boolean) {
        _spoofingState.value = _spoofingState.value.copy(isDnsSpoofingActive = active)
    }

    fun setDhcpSpoofingActive(active: Boolean) {
        _spoofingState.value = _spoofingState.value.copy(isDhcpSpoofingActive = active)
    }

    fun getCurrentState(): SpoofingState = _spoofingState.value
}
