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
}

/**
 * Extension function to handle NetworkResult in UI
 */
suspend fun <T> NetworkResult<T>.onSuccess(block: suspend (T) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Success) {
        block(data)
    }
    return this
}

suspend fun <T> NetworkResult<T>.onError(block: suspend (NetworkError) -> Unit): NetworkResult<T> {
    if (this is NetworkResult.Error) {
        block(error)
    }
    return this
}