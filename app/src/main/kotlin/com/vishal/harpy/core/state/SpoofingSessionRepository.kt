package com.vishal.harpy.core.state

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.vishal.harpy.core.utils.SpoofingSession
import com.vishal.harpy.core.utils.DhcpSpoofingRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpoofingSessionRepository @Inject constructor(
    private val context: Context
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SpoofingSessionRepo"
        private const val PREFS_NAME = "spoofing_sessions"
        private const val KEY_DNS_SESSIONS = "dns_sessions"
        private const val KEY_DHCP_SESSIONS = "dhcp_sessions"
    }

    suspend fun saveSessions(sessions: List<SpoofingSession>) = withContext(Dispatchers.IO) {
        try {
            val dnsSessions = sessions.filterIsInstance<SpoofingSession.Dns>()
            val dhcpSessions = sessions.filterIsInstance<SpoofingSession.Dhcp>()

            val dnsJson = JSONArray().apply {
                dnsSessions.forEach { session ->
                    put(JSONObject().apply {
                        put("id", session.id)
                        put("domain", session.domain)
                        put("spoofedIP", session.spoofedIP)
                        put("interfaceName", session.interfaceName)
                        put("isActive", session.isActive)
                    })
                }
            }

            val dhcpJson = JSONArray().apply {
                dhcpSessions.forEach { session ->
                    put(JSONObject().apply {
                        put("id", session.id)
                        put("interfaceName", session.interfaceName)
                        put("isActive", session.isActive)
                        put("rules", JSONArray().apply {
                            session.rules.forEach { rule ->
                                put(JSONObject().apply {
                                    put("targetMac", rule.targetMac)
                                    put("spoofedIP", rule.spoofedIP)
                                    put("gatewayIP", rule.gatewayIP)
                                    put("subnetMask", rule.subnetMask)
                                    put("dnsServer", rule.dnsServer)
                                })
                            }
                        })
                    })
                }
            }

            sharedPreferences.edit().apply {
                putString(KEY_DNS_SESSIONS, dnsJson.toString())
                putString(KEY_DHCP_SESSIONS, dhcpJson.toString())
                apply()
            }

            Log.d(TAG, "Saved ${dnsSessions.size} DNS and ${dhcpSessions.size} DHCP sessions")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving sessions: ${e.message}", e)
        }
    }

    suspend fun loadSessions(): List<SpoofingSession> = withContext(Dispatchers.IO) {
        return@withContext try {
            val sessions = mutableListOf<SpoofingSession>()

            // Load DNS sessions
            val dnsJson = sharedPreferences.getString(KEY_DNS_SESSIONS, null)
            if (!dnsJson.isNullOrEmpty()) {
                val dnsArray = JSONArray(dnsJson)
                for (i in 0 until dnsArray.length()) {
                    val obj = dnsArray.getJSONObject(i)
                    sessions.add(
                        SpoofingSession.Dns(
                            id = obj.getString("id"),
                            domain = obj.getString("domain"),
                            spoofedIP = obj.getString("spoofedIP"),
                            interfaceName = obj.getString("interfaceName"),
                            isActive = obj.getBoolean("isActive"),
                            startTime = null
                        )
                    )
                }
            }

            // Load DHCP sessions
            val dhcpJson = sharedPreferences.getString(KEY_DHCP_SESSIONS, null)
            if (!dhcpJson.isNullOrEmpty()) {
                val dhcpArray = JSONArray(dhcpJson)
                for (i in 0 until dhcpArray.length()) {
                    val obj = dhcpArray.getJSONObject(i)
                    val rulesArray = obj.getJSONArray("rules")
                    val rules = mutableListOf<DhcpSpoofingRule>()
                    for (j in 0 until rulesArray.length()) {
                        val ruleObj = rulesArray.getJSONObject(j)
                        rules.add(
                            DhcpSpoofingRule(
                                targetMac = ruleObj.getString("targetMac"),
                                spoofedIP = ruleObj.getString("spoofedIP"),
                                gatewayIP = ruleObj.getString("gatewayIP"),
                                subnetMask = ruleObj.getString("subnetMask"),
                                dnsServer = ruleObj.getString("dnsServer")
                            )
                        )
                    }
                    sessions.add(
                        SpoofingSession.Dhcp(
                            id = obj.getString("id"),
                            interfaceName = obj.getString("interfaceName"),
                            rules = rules,
                            isActive = obj.getBoolean("isActive"),
                            startTime = null
                        )
                    )
                }
            }

            Log.d(TAG, "Loaded ${sessions.size} sessions")
            sessions
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sessions: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun clearSessions() = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.edit().apply {
                remove(KEY_DNS_SESSIONS)
                remove(KEY_DHCP_SESSIONS)
                apply()
            }
            Log.d(TAG, "Cleared all saved sessions")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing sessions: ${e.message}", e)
        }
    }
}
