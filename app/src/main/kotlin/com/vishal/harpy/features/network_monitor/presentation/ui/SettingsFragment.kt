package com.vishal.harpy.features.network_monitor.presentation.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.vishal.harpy.R
import com.vishal.harpy.core.utils.LogUtils
import com.vishal.harpy.core.utils.VibrationUtils
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: View? = null
    private val binding get() = _binding!!
    private val viewModel: NetworkMonitorViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflater.inflate(R.layout.fragment_settings, container, false)

        // Apply right-to-left slide animation
        ViewCompat.setOnApplyWindowInsetsListener(_binding!!) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val mandatorySystemGestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())

            // Combine system bars and mandatory gestures to ensure proper spacing
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                Math.max(systemBars.bottom, mandatorySystemGestures.bottom)
            )
            insets
        }

        return binding
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply right-to-left entry animation
        animateSlideFromRight()

        val backButton = binding.findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            // Apply right-to-left exit animation before navigating back
            animateSlideToRight {
                requireActivity().supportFragmentManager.popBackStack()
            }
        }

        // Handle About item click
        val aboutItem = binding.findViewById<LinearLayout>(R.id.aboutItem)
        aboutItem.setOnClickListener {
            showAboutDialog()
        }

        // Handle Clear All Device Names item click
        val clearNamesItem = binding.findViewById<LinearLayout>(R.id.clearNamesItem)
        clearNamesItem.setOnClickListener {
            showClearNamesConfirmation()
        }

        // Handle Unblock All Devices item click
        val unblockAllItem = binding.findViewById<LinearLayout>(R.id.unblockAllItem)
        unblockAllItem.setOnClickListener {
            showUnblockAllConfirmation()
        }

        // Handle Logging Settings item click and long press to clear logs (3 second hold)
        val loggingSettingsItem = binding.findViewById<LinearLayout>(R.id.loggingSettingsItem)
        var pressStartTime = 0L
        var isLongPressHandled = false
        var delayedRunnable: Runnable? = null
        
        loggingSettingsItem.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    pressStartTime = System.currentTimeMillis()
                    isLongPressHandled = false
                    
                    // Remove any previous delayed runnable
                    if (delayedRunnable != null) {
                        v.removeCallbacks(delayedRunnable!!)
                    }
                    
                    delayedRunnable = Runnable {
                        if (System.currentTimeMillis() - pressStartTime >= 3000 && !isLongPressHandled) {
                            isLongPressHandled = true
                            // Vibrate on successful 3-second hold
                            VibrationUtils.vibrate(requireContext(), 100)
                            clearLogs()
                        }
                    }
                    v.postDelayed(delayedRunnable!!, 3000)
                    true // Consume the event
                }
                android.view.MotionEvent.ACTION_UP -> {
                    // Remove the delayed runnable if still pending
                    if (delayedRunnable != null) {
                        loggingSettingsItem.removeCallbacks(delayedRunnable!!)
                    }
                    
                    if (System.currentTimeMillis() - pressStartTime < 3000 && !isLongPressHandled) {
                        // Normal click - show logging settings
                        showLoggingSettingsDialog()
                    }
                    true // Consume the event
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Remove the delayed runnable if still pending
                    if (delayedRunnable != null) {
                        loggingSettingsItem.removeCallbacks(delayedRunnable!!)
                    }
                    true // Consume the event
                }
                else -> false
            }
        }
    }

    private fun showClearNamesConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear all custom names?")
            .setMessage("This will remove all custom device names you've set.")
            .setPositiveButton("Clear") { _, _ ->
                viewModel.clearAllDeviceNames()
                android.widget.Toast.makeText(
                    requireContext(),
                    "All custom names cleared",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .create()
            .show()
    }

    private fun showAboutDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about, null)
        
        // Set version from BuildConfig
        val versionText = dialogView.findViewById<android.widget.TextView>(R.id.versionText)
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            versionText.text = "Version ${packageInfo.versionName}"
        } catch (e: Exception) {
            versionText.text = "Version 1.0.0"
        }
        
        // GitHub button click handler
        val githubButton = dialogView.findViewById<android.widget.Button>(R.id.githubButton)
        githubButton.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://github.com/VishaL6i9/HarpyAndroid")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "No browser found",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        // Email button click handler
        val emailButton = dialogView.findViewById<android.widget.Button>(R.id.emailButton)
        emailButton.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:vishalkandakatla@gmail.com")
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Harpy Android - Feedback")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "No email app found",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        // Auto Update Switch
        val autoUpdateSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.autoUpdateSwitch)
        val autoUpdateLayout = dialogView.findViewById<android.widget.LinearLayout>(R.id.autoUpdateLayout)
        
        // TODO: Load saved auto-update preference from SharedPreferences
        autoUpdateSwitch.isChecked = false
        
        autoUpdateLayout.setOnClickListener {
            autoUpdateSwitch.isChecked = !autoUpdateSwitch.isChecked
        }
        
        autoUpdateSwitch.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Save auto-update preference to SharedPreferences
            // TODO: If enabled, schedule periodic update checks
            android.widget.Toast.makeText(
                requireContext(),
                if (isChecked) "Auto-update enabled" else "Auto-update disabled",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        
        // Check for Updates Button
        val checkUpdatesButton = dialogView.findViewById<android.widget.Button>(R.id.checkUpdatesButton)
        checkUpdatesButton.setOnClickListener {
            // TODO: Implement update checking logic
            // TODO: Check GitHub releases API for latest version
            // TODO: Compare with current version
            // TODO: Show update dialog if newer version available
            android.widget.Toast.makeText(
                requireContext(),
                "Update check not implemented yet",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        
        // Ko-fi donation
        val kofiLayout = dialogView.findViewById<android.widget.LinearLayout>(R.id.kofiLayout)
        kofiLayout.setOnClickListener {
            // TODO: Add your Ko-fi URL here
            android.widget.Toast.makeText(
                requireContext(),
                "Ko-fi link not configured",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        
        // PayPal donation
        val paypalLayout = dialogView.findViewById<android.widget.LinearLayout>(R.id.paypalLayout)
        paypalLayout.setOnClickListener {
            // TODO: Add your PayPal URL here
            android.widget.Toast.makeText(
                requireContext(),
                "PayPal link not configured",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        
        // UPI donation - Copy to clipboard
        val upiLayout = dialogView.findViewById<android.widget.LinearLayout>(R.id.upiLayout)
        upiLayout.setOnClickListener {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("UPI ID", "vishal6i9@slc")
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(
                requireContext(),
                "UPI ID copied to clipboard",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()
        
        dialog.show()
    }

    private fun showUnblockAllConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Unblock all devices?")
            .setMessage("This will remove all active device blocks and restore network access for all blocked devices.")
            .setPositiveButton("Unblock All") { _, _ ->
                viewModel.unblockAllDevices()
                android.widget.Toast.makeText(
                    requireContext(),
                    "All devices unblocked",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .create()
            .show()
    }

    private fun showLoggingSettingsDialog() {
        // Debug Mode ON = rotation disabled (single file)
        // Debug Mode OFF = rotation enabled (multiple files)
        val isDebugModeEnabled = !LogUtils.isLogRotationEnabled()

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_logging_settings, null)
        val debugModeToggle = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.verboseLoggingSwitch)
        val descriptionText = dialogView.findViewById<android.widget.TextView>(R.id.descriptionText)
        val deleteLogsButton = dialogView.findViewById<android.widget.Button>(R.id.deleteLogsButton)
        debugModeToggle.isChecked = isDebugModeEnabled
        
        // Set initial description
        updateDescriptionText(descriptionText, isDebugModeEnabled)

        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Logging Settings")
            .setView(dialogView)
            .setPositiveButton("Export Logs") { _, _ ->
                exportLogs()
            }
            .setNegativeButton("Close") { dialog, _ ->
                // Update log rotation setting when closing
                LogUtils.setLogRotationEnabled(requireContext(), debugModeToggle.isChecked)
                dialog.dismiss()
            }

        val dialog = builder.create()
        dialog.show()

        // Update the log rotation setting and description when the switch is toggled
        debugModeToggle.setOnCheckedChangeListener { _, isChecked ->
            LogUtils.setLogRotationEnabled(requireContext(), isChecked)
            updateDescriptionText(descriptionText, isChecked)
        }

        // Handle delete logs button
        deleteLogsButton.setOnClickListener {
            showDeleteLogsConfirmation(dialog)
        }
    }

    private fun updateDescriptionText(textView: android.widget.TextView, isDebugModeEnabled: Boolean) {
        textView.text = if (isDebugModeEnabled) {
            "Logs will continue to grow in a single file"
        } else {
            "Automatically rotate logs when they exceed 5MB"
        }
    }

    private fun showDeleteLogsConfirmation(parentDialog: androidx.appcompat.app.AlertDialog) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete All Logs?")
            .setMessage("This will permanently delete all log files. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteAllLogs()
                parentDialog.dismiss()
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .create()
            .show()
    }

    private fun deleteAllLogs() {
        Thread {
            try {
                val logsDir = LogUtils.getLogsDirectory(requireContext())
                if (logsDir != null && logsDir.exists()) {
                    val deletedCount = logsDir.listFiles()?.count { it.delete() } ?: 0
                    requireActivity().runOnUiThread {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Deleted $deletedCount log file${if (deletedCount != 1) "s" else ""}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    requireActivity().runOnUiThread {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "No log files found",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Error deleting logs: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun exportLogs() {
        // Request permission to write to external storage if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, check MANAGE_EXTERNAL_STORAGE permission
            if (!Environment.isExternalStorageManager()) {
                // Open settings to allow user to grant permission
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)

                android.widget.Toast.makeText(
                    requireContext(),
                    "Please grant \"Manage all files\" permission in Settings to export logs to public directory",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }
        } else {
            // For older versions, check WRITE_EXTERNAL_STORAGE permission
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001
                )
                return
            }
        }

        // Export logs to file
        Thread {
            try {
                val logFilePath = LogUtils.dumpLogsToFile(requireContext())
                requireActivity().runOnUiThread {
                    if (logFilePath != null) {
                        showExportSuccessDialog(logFilePath)
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Logs exported successfully to: $logFilePath",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Failed to export logs - check permissions",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Error exporting logs: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun showExportSuccessDialog(filePath: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Logs Exported Successfully")
            .setMessage("Logs saved to: $filePath")
            .setPositiveButton("Share") { _, _ ->
                shareLogFile(filePath)
            }
            .setNegativeButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun shareLogFile(filePath: String) {
        try {
            val file = File(filePath)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share logs via"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                requireContext(),
                "Unable to share logs: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun clearLogs() {
        Thread {
            try {
                // Clear the entire log buffer
                val process = Runtime.getRuntime().exec("logcat -c")
                val exitCode = process.waitFor()

                requireActivity().runOnUiThread {
                    if (exitCode == 0) {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Log buffer cleared successfully",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Failed to clear log buffer - exit code: $exitCode",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: SecurityException) {
                requireActivity().runOnUiThread {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Permission denied: Cannot clear logs. This feature may not be available on all devices.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Error clearing logs: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun animateSlideFromRight() {
        val animation = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 1.0f, // Start from right (100% of parent width)
            Animation.RELATIVE_TO_PARENT, 0.0f, // End at normal position (0% of parent width)
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f
        )
        animation.duration = 300 // 300ms duration
        binding.startAnimation(animation)
    }

    private fun animateSlideToRight(onComplete: () -> Unit) {
        val animation = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0.0f, // Start from normal position
            Animation.RELATIVE_TO_PARENT, 1.0f, // End at right (100% of parent width)
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f
        )
        animation.duration = 300 // 300ms duration
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationRepeat(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                onComplete()
            }
        })
        binding.startAnimation(animation)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
