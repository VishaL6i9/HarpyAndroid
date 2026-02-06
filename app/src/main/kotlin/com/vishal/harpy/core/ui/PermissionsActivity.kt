package com.vishal.harpy.core.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vishal.harpy.R
import com.vishal.harpy.core.utils.PermissionChecker
import com.vishal.harpy.core.utils.PermissionInfo

class PermissionsActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
    }

    private lateinit var permissionsContainer: LinearLayout
    private lateinit var requestPermissionsButton: Button
    private lateinit var manageStoragePermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        permissionsContainer = findViewById(R.id.permissionsContainer)
        requestPermissionsButton = findViewById(R.id.requestPermissionsButton)

        // Register the activity result launcher for managing external storage permission
        manageStoragePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            loadPermissionsList()
            updateRequestButton()

            // If all permissions are now granted, navigate to main activity
            if (PermissionChecker.areAllPermissionsGranted(this)) {
                navigateToMain()
            }
        }

        loadPermissionsList()
        updateRequestButton()

        requestPermissionsButton.setOnClickListener {
            requestMissingPermissions()
        }
    }

    private fun loadPermissionsList() {
        permissionsContainer.removeAllViews()
        val permissions = PermissionChecker.getAllPermissions(this)

        for (permission in permissions) {
            val itemView = layoutInflater.inflate(R.layout.item_permission, permissionsContainer, false)

            val checkBox: CheckBox = itemView.findViewById(R.id.permissionCheckBox)
            val nameText: TextView = itemView.findViewById(R.id.permissionName)
            val descText: TextView = itemView.findViewById(R.id.permissionDescription)

            nameText.text = permission.name
            descText.text = permission.description
            checkBox.isChecked = permission.isGranted

            // Change appearance based on grant status and warning type
            when {
                permission.isWarning -> {
                    checkBox.setButtonDrawable(R.drawable.ic_info)
                    nameText.setTextColor(ContextCompat.getColor(this, R.color.text_warning))
                }
                permission.isGranted -> {
                    checkBox.setButtonDrawable(R.drawable.ic_check_circle)
                    nameText.setTextColor(ContextCompat.getColor(this, R.color.text_success))
                }
                else -> {
                    checkBox.setButtonDrawable(R.drawable.ic_cancel)
                    nameText.setTextColor(ContextCompat.getColor(this, R.color.text_error))
                }
            }

            permissionsContainer.addView(itemView)
        }
    }

    private fun updateRequestButton() {
        val missingPermissions = PermissionChecker.getMissingPermissions(this)
        requestPermissionsButton.isEnabled = missingPermissions.isNotEmpty()
        requestPermissionsButton.text = if (missingPermissions.isNotEmpty()) {
            "Grant ${missingPermissions.size} Missing Permission${if (missingPermissions.size > 1) "s" else ""}"
        } else {
            "All Permissions Granted"
        }
    }

    private fun requestMissingPermissions() {
        val missingPermissions = PermissionChecker.getMissingPermissions(this)
        if (missingPermissions.isEmpty()) {
            navigateToMain()
            return
        }

        // Check if MANAGE_EXTERNAL_STORAGE permission is missing (Android 11+)
        val manageStoragePermission = missingPermissions.find {
            it.permission == android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
        }

        if (manageStoragePermission != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For MANAGE_EXTERNAL_STORAGE, we need to open the system settings
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            manageStoragePermissionLauncher.launch(intent)
        } else {
            // For other permissions, use the normal request flow
            val permissionsToRequest = missingPermissions.filter {
                it.permission != android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
            }.map { it.permission }.toTypedArray()

            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            loadPermissionsList()
            updateRequestButton()

            // If all permissions are now granted, navigate to main activity
            if (PermissionChecker.areAllPermissionsGranted(this)) {
                navigateToMain()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, com.vishal.harpy.main.MainActivityCompose::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}