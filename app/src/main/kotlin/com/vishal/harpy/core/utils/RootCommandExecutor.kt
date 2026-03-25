package com.vishal.harpy.core.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of executing a root command
 */
data class RootCommandResult(
    val exitCode: Int,
    val output: List<String>,
    val error: String? = null
) {
    val isSuccess: Boolean get() = exitCode == 0
    val outputText: String get() = output.joinToString("\n")
}

/**
 * Executes shell commands with root privileges
 */
@Singleton
class RootCommandExecutor @Inject constructor() {
    companion object {
        private const val TAG = "RootCommandExecutor"
    }

    private var rootSession: Process? = null
    private var outputStream: java.io.OutputStream? = null
    private var inputStream: BufferedReader? = null
    private var errorStream: BufferedReader? = null
    private var isRootAvailable: Boolean? = null

    /**
     * Check if root access is available
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        isRootAvailable ?: run {
            Log.d(TAG, "=== Root Check Started ===")
            
            // Method 1: Check if su binary exists in common locations
            val suPaths = listOf(
                "/system/xbin/su",
                "/system/bin/su",
                "/sbin/su",
                "/system/su/bin/su",
                "/magisk/.core/bin/su",
                "/data/adb/magisk/su",
                "/data/adb/ksu/bin/su"
            )

            for (path in suPaths) {
                val exists = java.io.File(path).exists()
                Log.d(TAG, "Checking $path: ${if (exists) "EXISTS" else "not found"}")
                if (exists) {
                    Log.d(TAG, "✓ Root available: su binary found at $path")
                    isRootAvailable = true
                    return@withContext true
                }
            }

            // Method 2: Try executing su command
            try {
                Log.d(TAG, "Attempting su command execution...")
                val process = Runtime.getRuntime().exec("su")
                val outputStream = process.outputStream
                val inputStream = process.inputStream
                val errorStream = process.errorStream

                // Send test command
                Log.d(TAG, "Sending test command: echo root_ok")
                outputStream.write("echo root_ok\n".toByteArray())
                outputStream.write("exit\n".toByteArray())
                outputStream.flush()

                // Wait for process to complete
                val exitCode = process.waitFor()
                Log.d(TAG, "su process exitCode: $exitCode")

                // Read output
                val result = inputStream.bufferedReader().use { it.readText() }
                val errorResult = errorStream.bufferedReader().use { it.readText() }
                
                Log.d(TAG, "su stdout: $result")
                if (errorResult.isNotBlank()) {
                    Log.d(TAG, "su stderr: $errorResult")
                }

                isRootAvailable = result.contains("root_ok")
                if (isRootAvailable == true) {
                    Log.d(TAG, "✓ Root available: su command succeeded")
                } else {
                    Log.d(TAG, "✗ Root check: su command returned but no root")
                }
                isRootAvailable!!
            } catch (e: Exception) {
                Log.e(TAG, "✗ Root check failed: ${e.message}", e)
                isRootAvailable = false
                false
            }
        }
    }

    /**
     * Execute a single command with root
     */
    suspend fun execute(command: String): RootCommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing root command: $command")
            
            // Use su with shell pipe instead of -c flag (more compatible)
            val process = Runtime.getRuntime().exec("su")
            val outputStream = process.outputStream
            val inputStream = process.inputStream
            val errorStream = process.errorStream
            
            // Write command and exit
            outputStream.write("$command\n".toByteArray())
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
            
            // Read output and error
            val output = inputStream.bufferedReader().use { it.readLines() }
            val error = errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            
            val result = RootCommandResult(
                exitCode = exitCode,
                output = output,
                error = error.takeIf { it.isNotBlank() }
            )
            
            Log.d(TAG, "Command result: exitCode=$exitCode, outputLines=${output.size}, error=$error")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: ${e.message}")
            RootCommandResult(
                exitCode = -1,
                output = emptyList(),
                error = e.message
            )
        }
    }

    /**
     * Execute multiple commands in sequence
     */
    suspend fun execute(commands: List<String>): List<RootCommandResult> = withContext(Dispatchers.IO) {
        commands.map { execute(it) }
    }

    /**
     * Start a persistent root shell session
     */
    suspend fun startRootSession(): Boolean = withContext(Dispatchers.IO) {
        try {
            rootSession = Runtime.getRuntime().exec("su")
            outputStream = rootSession?.outputStream
            inputStream = BufferedReader(InputStreamReader(rootSession?.inputStream))
            errorStream = BufferedReader(InputStreamReader(rootSession?.errorStream))
            
            // Test if session is working
            outputStream?.write("echo session_started\n".toByteArray())
            outputStream?.flush()
            
            val result = inputStream?.readLine()
            result?.contains("session_started") == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start root session: ${e.message}")
            false
        }
    }

    /**
     * Execute command in active root session
     */
    suspend fun executeInSession(command: String): String? = withContext(Dispatchers.IO) {
        try {
            outputStream?.write("$command\n".toByteArray())
            outputStream?.flush()
            
            // Read output with timeout
            val result = buildString {
                var line: String?
                var emptyLines = 0
                while (inputStream?.ready() == true && emptyLines < 2) {
                    line = inputStream?.readLine()
                    if (line.isNullOrBlank()) {
                        emptyLines++
                    } else {
                        appendLine(line)
                        emptyLines = 0
                    }
                }
            }
            result.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Session command failed: ${e.message}")
            null
        }
    }

    /**
     * Close root session
     */
    fun closeSession() {
        try {
            outputStream?.write("exit\n".toByteArray())
            outputStream?.flush()
            rootSession?.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing session: ${e.message}")
        } finally {
            rootSession?.destroy()
            rootSession = null
            outputStream = null
            inputStream = null
            errorStream = null
        }
    }
}
