package com.vishal.harpy.core.utils

/**
 * Sealed class representing different types of network errors
 */
sealed class NetworkError(open val message: String, open val errorCause: Throwable? = null) {

    data class DeviceNotRootedError(override val errorCause: Throwable? = null) :
        NetworkError("Device is not rooted", errorCause)

    data class NetworkScanError(override val errorCause: Throwable? = null) :
        NetworkError("Error scanning network", errorCause)

    data class BlockDeviceError(override val errorCause: Throwable? = null) :
        NetworkError("Error blocking device", errorCause)

    data class UnblockDeviceError(override val errorCause: Throwable? = null) :
        NetworkError("Error unblocking device", errorCause)

    data class NetworkAccessError(override val errorCause: Throwable? = null) :
        NetworkError("Network access error", errorCause)

    data class CommandExecutionError(override val errorCause: Throwable? = null) :
        NetworkError("Error executing network command", errorCause)

    data class InvalidIpAddressError(val ipAddress: String) :
        NetworkError("Invalid IP address: $ipAddress")

    data class InvalidMacAddressError(val macAddress: String) :
        NetworkError("Invalid MAC address: $macAddress")

    data class NativeLibraryError(override val errorCause: Throwable? = null) :
        NetworkError("Native library/helper error", errorCause)

    data class UnknownError(override val errorCause: Throwable? = null) :
        NetworkError("Unknown error occurred", errorCause)

    /**
     * Get the full stack trace as a formatted string
     */
    fun getStackTrace(): String {
        return errorCause?.stackTraceToString() ?: "No stack trace available"
    }

    /**
     * Get a detailed error report including message and stack trace
     */
    fun getDetailedReport(): String {
        val sb = StringBuilder()
        sb.append("Error: $message\n")
        val cause = errorCause
        if (cause != null) {
            sb.append("Cause: ${cause.javaClass.simpleName}\n")
            sb.append("Message: ${cause.message}\n")
            sb.append("Stack Trace:\n")
            sb.append(getStackTrace())
        }
        return sb.toString()
    }
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
 * Extension functions for handling NetworkResult in a functional style
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