package com.vishal.harpy.core.utils

import android.content.Context
import android.util.Log
import com.vishal.harpy.R

/**
 * Utility class for handling and displaying root-related errors
 */
object RootErrorMapper {

    private const val TAG = "RootErrorMapper"

    /**
     * Maps a RootError to a user-friendly error message
     */
    fun mapToUserMessage(context: Context, error: RootError): String {
        return when (error) {
            is RootError.RootAccessDeniedError -> {
                context.getString(R.string.error_root_access_denied)
            }
            is RootError.RootCheckFailedError -> {
                context.getString(R.string.error_root_check_failed)
            }
            is RootError.RootCommandExecutionError -> {
                context.getString(R.string.error_root_command_execution)
            }
            is RootError.RootPermissionError -> {
                context.getString(R.string.error_root_permission)
            }
            is RootError.RootTimeoutError -> {
                context.getString(R.string.error_root_timeout)
            }
            is RootError.UnknownRootError -> {
                context.getString(R.string.error_root_unknown)
            }
        }
    }

    /**
     * Get detailed error information including stack trace
     */
    fun getDetailedErrorInfo(error: RootError): String {
        return error.getDetailedReport()
    }

    /**
     * Get just the stack trace from an error
     */
    fun getStackTrace(error: RootError): String {
        return error.getStackTrace()
    }

    /**
     * Format error for logging with full details
     */
    fun formatForLogging(error: RootError): String {
        val sb = StringBuilder()
        sb.append("=== Root Error Report ===\n")
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

    /**
     * Log error with full stack trace for debugging
     */
    fun logErrorWithStackTrace(message: String, error: RootError) {
        Log.e(TAG, message)
        Log.e(TAG, formatForLogging(error))
    }
}
