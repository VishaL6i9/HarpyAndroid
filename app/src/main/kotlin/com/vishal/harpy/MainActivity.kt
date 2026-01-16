package com.vishal.harpy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    private lateinit var devicesList: ListView
    private lateinit var adapter: DeviceAdapter
    private val networkMonitorService = NetworkMonitorService()
    private val devices = mutableListOf<NetworkDevice>()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupClickListeners()
        checkPermissions()
        checkRootAccess()
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.status_text)
        scanButton = findViewById(R.id.scan_button)
        devicesList = findViewById(R.id.devices_list)

        adapter = DeviceAdapter(this, devices) { device ->
            toggleDeviceBlockStatus(device)
        }
        devicesList.adapter = adapter
    }

    private fun setupClickListeners() {
        scanButton.setOnClickListener {
            scanNetwork()
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.INTERNET)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_NETWORK_STATE)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun checkRootAccess() {
        if (networkMonitorService.isDeviceRooted()) {
            statusText.text = "Root access available - Full functionality enabled"
            scanButton.isEnabled = true
        } else {
            statusText.text = "Root access not available - Limited functionality"
            scanButton.isEnabled = false
            Toast.makeText(this, "Root access required for full functionality", Toast.LENGTH_LONG).show()
        }
    }

    private fun scanNetwork() {
        statusText.text = "Scanning network..."
        scanButton.isEnabled = false

        // Simulate network scanning (in a real app, this would be done in a background thread)
        val foundDevices = networkMonitorService.scanNetwork()

        devices.clear()
        devices.addAll(foundDevices)
        adapter.notifyDataSetChanged()

        statusText.text = "Found ${foundDevices.size} devices"
        scanButton.isEnabled = true
    }

    private fun toggleDeviceBlockStatus(device: NetworkDevice) {
        if (device.isBlocked) {
            // Unblock the device
            if (networkMonitorService.unblockDevice(device)) {
                device.isBlocked = false
                Toast.makeText(this, "Device unblocked: ${device.ipAddress}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to unblock device: ${device.ipAddress}", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Block the device
            if (networkMonitorService.blockDevice(device)) {
                device.isBlocked = true
                Toast.makeText(this, "Device blocked: ${device.ipAddress}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to block device: ${device.ipAddress}", Toast.LENGTH_SHORT).show()
            }
        }
        adapter.notifyDataSetChanged()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }

            if (!allGranted) {
                Toast.makeText(this, "Some permissions denied. App may not function properly.", Toast.LENGTH_LONG).show()
            }
        }
    }
}