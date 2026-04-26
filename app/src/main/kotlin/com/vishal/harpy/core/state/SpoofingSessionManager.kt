package com.vishal.harpy.core.state

import android.util.Log
import com.vishal.harpy.core.utils.SpoofingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "SpoofingSessionManager"

@Singleton
class SpoofingSessionManager @Inject constructor(
    private val sessionRepository: SpoofingSessionRepository
) {

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _sessions = MutableStateFlow<List<SpoofingSession>>(emptyList())
    val sessions: StateFlow<List<SpoofingSession>> = _sessions.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<SpoofingSession>>(emptyList())
    val activeSessions: StateFlow<List<SpoofingSession>> = _activeSessions.asStateFlow()

    val sessionStats: StateFlow<SessionStats> = _sessions.map { 
        calculateStats(it)
    }.stateIn(
        scope = managerScope,
        started = SharingStarted.Eagerly,
        initialValue = calculateStats(emptyList())
    )

    init {
        // Load sessions on initialization
        managerScope.launch {
            val loadedSessions = sessionRepository.loadSessions()
            _sessions.value = loadedSessions
            updateActiveSessions()
            Log.d(TAG, "Loaded ${loadedSessions.size} sessions from storage")
        }
    }

    fun addSession(session: SpoofingSession) {
        val updated = _sessions.value.toMutableList()
        updated.add(session)
        _sessions.value = updated
        updateActiveSessions()
        persistSessions()
        Log.d(TAG, "Added session: ${session.id}")
    }

    fun updateSession(session: SpoofingSession) {
        val updated = _sessions.value.toMutableList()
        val index = updated.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            updated[index] = session
            _sessions.value = updated
            updateActiveSessions()
            persistSessions()
            Log.d(TAG, "Updated session: ${session.id}")
        }
    }

    fun removeSession(sessionId: String) {
        val updated = _sessions.value.filter { it.id != sessionId }
        _sessions.value = updated
        updateActiveSessions()
        persistSessions()
        Log.d(TAG, "Removed session: $sessionId")
    }

    fun getSession(sessionId: String): SpoofingSession? {
        return _sessions.value.find { it.id == sessionId }
    }

    fun getActiveSessions(): List<SpoofingSession> {
        return _sessions.value.filter { it.isActive }
    }

    fun getDnsSessions(): List<SpoofingSession.Dns> {
        return _sessions.value.filterIsInstance<SpoofingSession.Dns>()
    }

    fun getDhcpSessions(): List<SpoofingSession.Dhcp> {
        return _sessions.value.filterIsInstance<SpoofingSession.Dhcp>()
    }

    fun clearAllSessions() {
        _sessions.value = emptyList()
        updateActiveSessions()
        persistSessions()
        Log.d(TAG, "Cleared all sessions")
    }

    private fun updateActiveSessions() {
        _activeSessions.value = _sessions.value.filter { it.isActive }
    }

    private fun persistSessions() {
        managerScope.launch {
            sessionRepository.saveSessions(_sessions.value)
        }
    }

    fun getSessionStats(): SessionStats {
        return calculateStats(_sessions.value)
    }

    private fun calculateStats(all: List<SpoofingSession>): SessionStats {
        val active = all.filter { it.isActive }
        val dns = all.filterIsInstance<SpoofingSession.Dns>()
        val dhcp = all.filterIsInstance<SpoofingSession.Dhcp>()

        return SessionStats(
            totalSessions = all.size,
            activeSessions = active.size,
            dnsSessions = dns.size,
            dhcpSessions = dhcp.size,
            activeDnsSessions = dns.count { it.isActive },
            activeDhcpSessions = dhcp.count { it.isActive }
        )
    }
}

data class SessionStats(
    val totalSessions: Int,
    val activeSessions: Int,
    val dnsSessions: Int,
    val dhcpSessions: Int,
    val activeDnsSessions: Int,
    val activeDhcpSessions: Int
)
