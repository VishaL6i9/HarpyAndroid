package com.vishal.harpy.core.utils

/**
 * Sealed class representing different types of network errors
 */
sealed class NetworkError(val message: String, val cause: Throwable? = null) {
    data class DeviceNotRootedError(override val cause: Throwable? = null) :
        NetworkError("Device is not rooted", cause)
    
    data class NetworkScanError(override val cause: Throwable? = null) :
        NetworkError("Error scanning network", cause)
    
    data class BlockDeviceError(override val cause: Throwable? = null) :
        NetworkError("Error blocking device", cause)
    
    data class UnblockDeviceError(override val cause: Throwable? = null) :
        NetworkError("Error unblocking device", cause)
    
    data class NetworkAccessError(override val cause: Throwable? = null) :
        NetworkError("Network access error", cause)
    
    data class CommandExecutionError(override val cause: Throwable? = null) :
        NetworkError("Error executing network command", cause)
    
    data class InvalidIpAddressError(val ipAddress: String) :
        NetworkError("Invalid IP address: $ipAddress")
    
    data class InvalidMacAddressError(val macAddress: String) :
        NetworkError("Invalid MAC address: $macAddress")
    
    data class UnknownError(override val cause: Throwable? = null) :
        NetworkError("Unknown error occurred", cause)
}

/**
 * Result wrapper for network operations that can fail
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error<T>(val error: NetworkError) : NetworkResult<T>()
    
    companion object {
        fun <T> success(data: T): NetworkResult<T> = Success(data)
        fun <T> error(error: NetworkError): NetworkResult<T> = Error(error)
    }
}

/**
 * Extension function to convert exceptions to NetworkError
 */
fun Throwable.toNetworkError(): NetworkError {
    return when (this) {
        is SecurityException -> NetworkError.NetworkAccessError(this)
        is IllegalArgumentException -> NetworkError.UnknownError(this)
        else -> NetworkError.UnknownError(this)
    }
}