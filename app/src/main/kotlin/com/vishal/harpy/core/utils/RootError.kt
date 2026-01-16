package com.vishal.harpy.core.utils

/**
 * Sealed class representing different types of root access errors
 */
sealed class RootError(open val message: String, open val errorCause: Throwable? = null) {

    data class RootAccessDeniedError(override val errorCause: Throwable? = null) :
        RootError("Root access denied or device not rooted", errorCause)

    data class RootCheckFailedError(override val errorCause: Throwable? = null) :
        RootError("Failed to check root status", errorCause)

    data class RootCommandExecutionError(override val errorCause: Throwable? = null) :
        RootError("Error executing root command", errorCause)

    data class RootPermissionError(override val errorCause: Throwable? = null) :
        RootError("Insufficient root permissions", errorCause)

    data class RootTimeoutError(override val errorCause: Throwable? = null) :
        RootError("Root command execution timeout", errorCause)

    data class UnknownRootError(override val errorCause: Throwable? = null) :
        RootError("Unknown root-related error", errorCause)

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
        sb.append("Root Error: $message\n")
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
 * Result wrapper for root operations that can fail
 */
sealed class RootResult<out T> {
    data class Success<T>(val data: T) : RootResult<T>()
    data class Error<T>(val error: RootError) : RootResult<T>()

    companion object {
        fun <T> success(data: T): RootResult<T> = Success(data)
        fun <T> error(error: RootError): RootResult<T> = Error(error)
    }
}

/**
 * Extension functions for handling RootResult in a functional style
 */
suspend fun <T> RootResult<T>.onSuccess(block: suspend (T) -> Unit): RootResult<T> {
    if (this is RootResult.Success) {
        block(data)
    }
    return this
}

suspend fun <T> RootResult<T>.onError(block: suspend (RootError) -> Unit): RootResult<T> {
    if (this is RootResult.Error) {
        block(error)
    }
    return this
}
