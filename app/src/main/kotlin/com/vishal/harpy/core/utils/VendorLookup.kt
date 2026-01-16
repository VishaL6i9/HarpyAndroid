package com.vishal.harpy.core.utils

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object VendorLookup {
    private const val TAG = "VendorLookup"
    
    // Cached OUI database (MAC prefix -> Vendor)
    private val ouiCache = mutableMapOf<String, String>()
    private var isInitialized = false
    private var context: Context? = null
    
    // Fallback hardcoded common vendors for quick lookup
    private val commonVendors = mapOf(
        "00:50:43" to "Siemens",
        "00:50:C2" to "IEEE Registration Authority",
        "00:60:2F" to "Hewlett Packard",
        "00:A0:C9" to "Intel Corporation",
        "00:E0:4C" to "Realtek Semiconductor Corp.",
        "08:00:27" to "Oracle VirtualBox",
        "1C:69:7A" to "AcSiP Technology Corp.",
        "24:4B:03" to "Samsung Electronics Co., Ltd",
        "28:C6:3F" to "Apple, Inc.",
        "38:4F:F0" to "Samsung Electronics Co., Ltd",
        "40:B0:FA" to "LG Electronics (Mobile Communications)",
        "44:D9:E7" to "Ubiquiti Networks Inc.",
        "5C:F9:DD" to "Dell Inc.",
        "6C:EC:5A" to "Hon Hai Precision Ind. Co.,Ltd.",
        "78:4F:43" to "Apple, Inc.",
        "80:A5:89" to "AzureWave Technology Inc.",
        "8C:1F:64" to "Intel Corporate",
        "9C:93:4E" to "ASUSTek Computer, Inc.",
        "AC:DE:48" to "Intel Corporate",
        "B8:27:EB" to "Raspberry Pi Foundation",
        "BC:5F:F4" to "Dell Inc.",
        "C8:60:00" to "Apple, Inc.",
        "D8:3B:BF" to "Samsung Electronics Co., Ltd",
        "DC:A6:32" to "Raspberry Pi Trading Ltd",
        "E4:5D:52" to "Intel Corporate",
        "EC:26:CA" to "TP-Link Technologies Co., Ltd.",
        "F0:18:98" to "Apple, Inc.",
        "F4:8C:50" to "Intel Corporate"
    )
    
    /**
     * Initialize the vendor lookup with application context
     * Should be called once during app startup
     */
    fun initialize(appContext: Context) {
        context = appContext
        Log.d(TAG, "VendorLookup initialized")
    }
    
    /**
     * Look up vendor name by MAC address OUI (first 3 octets)
     * Uses cached results, common vendors, and local OUI database
     * Also tries 4-octet (MA-M) and 5-octet (MA-S) lookups for more specific matches
     */
    fun getVendor(macAddress: String): String? {
        if (macAddress.length < 8) return null
        
        val oui = macAddress.substring(0, 8).uppercase()
        
        // Check cache first
        if (ouiCache.containsKey(oui)) {
            return ouiCache[oui]
        }
        
        // Check common vendors
        val vendor = commonVendors[oui]
        if (vendor != null) {
            ouiCache[oui] = vendor
            return vendor
        }
        
        // Try to query local OUI database with multiple prefix lengths
        // First try 5-octet (MA-S), then 4-octet (MA-M), then 3-octet (OUI)
        var lookedUpVendor = queryLocalOuiDatabase(macAddress.substring(0, 14).uppercase()) // 5 octets
        if (lookedUpVendor != null) {
            ouiCache[oui] = lookedUpVendor
            return lookedUpVendor
        }
        
        lookedUpVendor = queryLocalOuiDatabase(macAddress.substring(0, 11).uppercase()) // 4 octets
        if (lookedUpVendor != null) {
            ouiCache[oui] = lookedUpVendor
            return lookedUpVendor
        }
        
        lookedUpVendor = queryLocalOuiDatabase(oui) // 3 octets
        if (lookedUpVendor != null) {
            ouiCache[oui] = lookedUpVendor
            return lookedUpVendor
        }
        
        return null
    }
    
    /**
     * Query the local OUI database file for vendor information
     * Parses the OUI database format: XX-XX-XX (hex) vendor name
     */
    private fun queryLocalOuiDatabase(oui: String): String? {
        return try {
            if (context == null) {
                Log.w(TAG, "Context not initialized, cannot query OUI database")
                return null
            }
            
            val formattedOui = oui.replace(":", "-").uppercase()
            Log.d(TAG, "Querying local OUI database for $formattedOui")
            
            val inputStream = context!!.assets.open("oui.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            var foundVendor: String? = null
            
            reader.useLines { lines ->
                for (line in lines) {
                    // OUI database format: XX-XX-XX (hex) vendor name
                    if (line.startsWith(formattedOui)) {
                        // Extract vendor name after the hex and whitespace
                        val parts = line.split(Regex("\\s+"), limit = 2)
                        if (parts.size >= 2) {
                            foundVendor = parts[1].trim()
                            Log.d(TAG, "Found vendor for $formattedOui: $foundVendor")
                            return@useLines
                        }
                    }
                }
            }
            
            foundVendor
        } catch (e: Exception) {
            Log.d(TAG, "Failed to query local OUI database: ${e.message}")
            null
        }
    }
    
    /**
     * Add a custom vendor mapping (useful for local network devices)
     */
    fun addCustomVendor(macPrefix: String, vendorName: String) {
        val oui = macPrefix.substring(0, 8).uppercase()
        ouiCache[oui] = vendorName
        Log.d(TAG, "Added custom vendor mapping: $oui -> $vendorName")
    }
    
    /**
     * Clear the cache
     */
    fun clearCache() {
        ouiCache.clear()
        Log.d(TAG, "Vendor cache cleared")
    }
}
