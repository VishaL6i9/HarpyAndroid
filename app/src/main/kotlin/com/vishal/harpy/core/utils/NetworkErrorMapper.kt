package com.vishal.harpy.core.utils

import android.content.Context
import com.vishal.harpy.R

/**
 * Utility class for handling and displaying network errors
 */
object NetworkErrorMapper {

    /**
     * Maps a NetworkError to a user-friendly error message
     */
    fun mapToUserMessage(context: Context, error: NetworkError): String {
        return when (error) {
            is NetworkError.DeviceNotRootedError -> {
                context.getString(R.string.error_device_not_rooted)
            }
            is NetworkError.NetworkScanError -> {
                context.getString(R.string.error_network_scan)
            }
            is NetworkError.BlockDeviceError -> {
                context.getString(R.string.error_block_device)
            }
            is NetworkError.UnblockDeviceError -> {
                context.getString(R.string.error_unblock_device)
            }
            is NetworkError.NetworkAccessError -> {
                context.getString(R.string.error_network_access)
            }
            is NetworkError.CommandExecutionError -> {
                context.getString(R.string.error_command_execution)
            }
            is NetworkError.InvalidIpAddressError -> {
                context.getString(R.string.error_invalid_ip_address, error.ipAddress)
            }
            is NetworkError.InvalidMacAddressError -> {
                context.getString(R.string.error_invalid_mac_address, error.macAddress)
            }
            is NetworkError.UnknownError -> {
                context.getString(R.string.error_unknown)
            }
        }
    }

    /**
     * Get detailed error information including stack trace
     */
    fun getDetailedErrorInfo(error: NetworkError): String {
        return error.getDetailedReport()
    }

    /**
     * Get just the stack trace from an error
     */
    fun getStackTrace(error: NetworkError): String {
        return error.getStackTrace()
    }

    /**
     * Format error for logging with full details
     */
    fun formatForLogging(error: NetworkError): String {
        val sb = StringBuilder()
        sb.append("=== Network Error Report ===\n")
        sb.append("Type: ${error.javaClass.simpleName}\n")
        sb.append("Message: ${error.message}\n")
        val cause = error.errorCause
        if (cause != null) {
            sb.append("Cause Type: ${cause.javaClass.simpleName}\n")
            sb.append("Cause Message: ${cause.message}\n")
            sb.append("\nStack Trace:\n")
            sb.append(error.getStackTrace())
        }
        sb.append("\n=== End Report ===")
        return sb.toString()
    }
}
