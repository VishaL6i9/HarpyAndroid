package com.vishal.harpy.features.network_monitor.presentation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vishal.harpy.R
import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.features.network_monitor.presentation.ui.SettingsFragment
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NetworkMonitorFragment : Fragment() {

    private var _binding: View? = null
    private val binding get() = _binding!!

    private val viewModel: NetworkMonitorViewModel by viewModels()

    private lateinit var adapter: NetworkDeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflater.inflate(R.layout.fragment_network_monitor, container, false)
        return binding
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        val scanButton = binding.findViewById<Button>(R.id.scanButton)
        scanButton.setOnClickListener {
            viewModel.scanNetwork()
        }

        val debugButton = binding.findViewById<android.widget.ImageButton>(R.id.debugButton)
        debugButton.setOnLongClickListener {
            showDebugMenu()
            true
        }

        val settingsButton = binding.findViewById<android.widget.ImageButton>(R.id.settingsButton)
        settingsButton.setOnClickListener { navigateToSettings() }

        // Setup filter buttons
        val filterIPv4 = binding.findViewById<com.google.android.material.button.MaterialButton>(R.id.filterIPv4)
        val filterIPv6 = binding.findViewById<com.google.android.material.button.MaterialButton>(R.id.filterIPv6)

        filterIPv4.setOnClickListener {
            viewModel.toggleIPv4Filter()
        }

        filterIPv6.setOnClickListener {
            viewModel.toggleIPv6Filter()
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = binding.findViewById<RecyclerView>(R.id.devicesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = NetworkDeviceAdapter(
            onBlockClick = { device -> showDeviceActions(device) },
            onUnblockClick = { device -> viewModel.unblockDevice(device) },
            onPinClick = { device -> viewModel.toggleDevicePin(device) },
            onEditNameClick = { device -> showEditNameDialog(device) },
            onLongPress = { device, _ -> showDeviceActions(device) }
        )
        recyclerView.adapter = adapter
    }

    private fun showDeviceActions(device: NetworkDevice) {
        DeviceActionsBottomSheet(
            device = device,
            onPinClick = { viewModel.toggleDevicePin(it) },
            onEditNameClick = { showEditNameDialog(it) },
            onBlockClick = { 
                if (it.isBlocked) {
                    viewModel.unblockDevice(it)
                } else if (it.isGateway) {
                    showNuclearConfirmation(it)
                } else {
                    viewModel.blockDevice(it)
                }
            },
            onPingClick = { viewModel.testPing(it) }
        ).show(childFragmentManager, DeviceActionsBottomSheet.TAG)
    }

    private fun showNuclearConfirmation(device: NetworkDevice) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("⚠️ NUCLEAR OPTION ⚠️")
            .setMessage("Are you sure? Blocking the Gateway will disconnect EVERY device on this WiFi network from the internet.")
            .setPositiveButton("I AM SURE") { _, _ ->
                showFinalNuclearWarning(device)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showFinalNuclearWarning(device: NetworkDevice) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🛑 EXTREME CAUTION 🛑")
            .setMessage("This action will cause network-wide disruption. You will have to manually unblock the gateway to restore service. Proceed?")
            .setPositiveButton("ACTIVATE NUCLEAR") { _, _ ->
                viewModel.blockDevice(device)
                android.widget.Toast.makeText(requireContext(), "NUCLEAR OPTION ACTIVATED", android.widget.Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Abort", null)
            .create()
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredDevices.collect { devices ->
                    adapter.submitList(devices)
                    updateDeviceCount(devices.size)
                    updateEmptyState(devices.isEmpty())
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoading ->
                    updateLoadingState(isLoading)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { error ->
                    error?.let {
                        showErrorDialog(it)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filterIPv4.collect { isChecked ->
                    val filterIPv4 = binding.findViewById<com.google.android.material.button.MaterialButton>(R.id.filterIPv4)
                    filterIPv4.isChecked = isChecked
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filterIPv6.collect { isChecked ->
                    val filterIPv6 = binding.findViewById<com.google.android.material.button.MaterialButton>(R.id.filterIPv6)
                    filterIPv6.isChecked = isChecked
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scanSuccess.collect { success ->
                    if (success) {
                        val deviceCount = viewModel.filteredDevices.value.size
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Scan complete: $deviceCount device${if (deviceCount != 1) "s" else ""} found",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        // Reset the success flag
                        viewModel.resetScanSuccess()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.testPingResult.collect { result ->
                    result?.let { (ip, responded) ->
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Ping test for $ip: ${if (responded) "REACHABLE (Verified)" else "NO RESPONSE (May be offline)"}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        viewModel.resetPingResult()
                    }
                }
            }
        }
    }

    private fun updateLoadingState(isLoading: Boolean) {
        val progressSection = binding.findViewById<LinearLayout>(R.id.progressSection)
        val scanButton = binding.findViewById<Button>(R.id.scanButton)
        
        if (isLoading) {
            progressSection.visibility = View.VISIBLE
            scanButton.isEnabled = false
        } else {
            progressSection.visibility = View.GONE
            scanButton.isEnabled = true
        }
    }

    private fun updateDeviceCount(count: Int) {
        val deviceCountSection = binding.findViewById<LinearLayout>(R.id.deviceCountSection)
        val filterSection = binding.findViewById<LinearLayout>(R.id.filterSection)
        val deviceCountText = binding.findViewById<TextView>(R.id.deviceCount)
        
        // Show device count and filter section if we have scanned (even if filtered results are empty)
        val hasScanned = viewModel.networkDevices.value.isNotEmpty()
        
        if (hasScanned) {
            deviceCountSection.visibility = View.VISIBLE
            filterSection.visibility = View.VISIBLE
            deviceCountText.text = count.toString()
        } else {
            deviceCountSection.visibility = View.GONE
            filterSection.visibility = View.GONE
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        val emptyState = binding.findViewById<LinearLayout>(R.id.emptyState)
        val recyclerView = binding.findViewById<RecyclerView>(R.id.devicesRecyclerView)
        
        // Only show empty state if we have no scanned devices at all
        val hasScanned = viewModel.networkDevices.value.isNotEmpty()
        
        if (isEmpty && !hasScanned) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showErrorDialog(errorMessage: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage(errorMessage)
            .setPositiveButton("OK") { _, _ -> }
            .setNegativeButton("View Details") { _, _ ->
                showErrorDetailsDialog()
            }
            .create()
        dialog.show()
    }

    private fun showErrorDetailsDialog() {
        val errorDetails = viewModel.getErrorDetails()
        val scrollView = android.widget.ScrollView(requireContext()).apply {
            addView(android.widget.TextView(requireContext()).apply {
                text = errorDetails
                setPadding(16, 16, 16, 16)
                textSize = 12f
                setTextIsSelectable(true)
            })
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Error Details")
            .setView(scrollView)
            .setPositiveButton("Copy") { _, _ ->
                copyToClipboard(errorDetails)
            }
            .setNegativeButton("Close") { _, _ -> }
            .create()
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Error Details", text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(requireContext(), "Error details copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun navigateToSettings() {
        val settingsFragment = SettingsFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.container, settingsFragment)
            .addToBackStack("settings")
            .commit()
    }

    private fun showEditNameDialog(device: NetworkDevice) {
        val editText = android.widget.EditText(requireContext()).apply {
            setText(device.deviceName ?: "")
            hint = "Enter device name (e.g., My Laptop, Guest Phone)"
            setSingleLine()
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Set Device Name")
            .setMessage("Device: ${device.ipAddress}")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim().takeIf { it.isNotEmpty() }
                viewModel.setDeviceName(device, newName)
                android.widget.Toast.makeText(
                    requireContext(),
                    "Device name set to: ${newName ?: "default"}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .setNeutralButton("Clear") { _, _ ->
                viewModel.setDeviceName(device, null)
                android.widget.Toast.makeText(
                    requireContext(),
                    "Device name cleared",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .create()
            .show()
    }

    private fun showDebugMenu() {
        val debugOptions = arrayOf(
            "DNS Spoofing Test",
            "Start DNS Spoofing",
            "Stop DNS Spoofing",
            "Add DNS Rule",
            "Remove DNS Rule",
            "Check DNS Status",
            "DHCP Spoofing Test",
            "Start DHCP Spoofing",
            "Stop DHCP Spoofing",
            "Check DHCP Status"
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔧 Debug Menu 🔧")
            .setItems(debugOptions) { _, which ->
                when (which) {
                    0 -> performDNSSpoofingTest()
                    1 -> startDNSSpoofing()
                    2 -> stopDNSSpoofing()
                    3 -> addDNSRule()
                    4 -> removeDNSRule()
                    5 -> checkDNSStatus()
                    6 -> performDHCPSpoofingTest()
                    7 -> startDHCPSpoofing()
                    8 -> stopDHCPSpoofing()
                    9 -> checkDHCPSpoofingStatus()
                }
            }
            .setNegativeButton("Close", null)
            .create()
            .show()
    }

    private fun performDNSSpoofingTest() {
        // Test DNS spoofing functionality using root helper
        android.widget.Toast.makeText(
            requireContext(),
            "DNS spoofing test initiated via root helper",
            android.widget.Toast.LENGTH_LONG
        ).show()

        // Log the test initiation
        com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "DNS Spoofing Test initiated")

        // Actually start DNS spoofing for test purposes
        viewModel.startDNSSpoofing("example.com", "8.8.8.8", "wlan0")

        // Log the expected behavior
        com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "Expected: Root helper would start DNS spoofing for test domains")
    }

    private fun performDHCPSpoofingTest() {
        // Test DHCP spoofing functionality using root helper
        android.widget.Toast.makeText(
            requireContext(),
            "DHCP spoofing test initiated via root helper",
            android.widget.Toast.LENGTH_LONG
        ).show()

        // Log the test initiation
        com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "DHCP Spoofing Test initiated")

        // Actually start DHCP spoofing for test purposes with default values
        viewModel.startDHCPSpoofing(
            interfaceName = "wlan0",
            targetMacs = arrayOf("aa:bb:cc:dd:ee:ff"),
            spoofedIPs = arrayOf("192.168.1.100"),
            gatewayIPs = arrayOf("192.168.1.1"),
            subnetMasks = arrayOf("255.255.255.0"),
            dnsServers = arrayOf("8.8.8.8")
        )

        // Log the expected behavior
        com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "Expected: Root helper would start DHCP spoofing for test devices")
    }

    private fun startDNSSpoofing() {
        // Get domains and IPs from user input
        val domainInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter domain to spoof"
            setText("example.com")
        }

        val ipInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter spoofed IP"
            setText("192.168.1.100")
        }

        val interfaceInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter network interface"
            setText("wlan0")
        }

        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(domainInput)
            addView(ipInput)
            addView(interfaceInput)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Start DNS Spoofing")
            .setView(layout)
            .setPositiveButton("Start") { _, _ ->
                val domain = domainInput.text.toString().trim()
                val spoofedIP = ipInput.text.toString().trim()
                val interfaceName = interfaceInput.text.toString().trim()

                if (domain.isEmpty() || spoofedIP.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Domain and spoofed IP are required",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                // Log the DNS spoofing start
                com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "Starting DNS spoofing: $domain -> $spoofedIP on interface $interfaceName")

                // Execute DNS spoofing via ViewModel
                viewModel.startDNSSpoofing(domain, spoofedIP, interfaceName)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun stopDNSSpoofing() {
        // Get domain to stop spoofing for
        val domainInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter domain to stop spoofing for"
            setText("example.com")
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Stop DNS Spoofing")
            .setView(domainInput)
            .setPositiveButton("Stop") { _, _ ->
                val domain = domainInput.text.toString().trim()

                if (domain.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Domain is required",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                // Log the stop command
                com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "Stopping DNS spoofing for domain: $domain")

                // Execute stop via ViewModel
                viewModel.stopDNSSpoofing(domain)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun addDNSRule() {
        val domainInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter domain to spoof"
            setText("example.com")
        }

        val ipInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter spoofed IP"
            setText("192.168.1.100")
        }

        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(domainInput)
            addView(ipInput)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add DNS Rule")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val domain = domainInput.text.toString().trim()
                val ip = ipInput.text.toString().trim()

                if (domain.isEmpty() || ip.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Both domain and IP are required",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                // Log the rule addition
                com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "DNS rule added: $domain -> $ip (would be applied via root helper)")

                android.widget.Toast.makeText(
                    requireContext(),
                    "DNS rule added: $domain -> $ip (would be applied via root helper)",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun removeDNSRule() {
        val domainInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter domain to remove"
            setText("example.com")
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Remove DNS Rule")
            .setView(domainInput)
            .setPositiveButton("Remove") { _, _ ->
                val domain = domainInput.text.toString().trim()

                if (domain.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Domain is required",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                // Log the rule removal
                com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "DNS rule removed for: $domain (would be handled by root helper)")

                android.widget.Toast.makeText(
                    requireContext(),
                    "DNS rule removed for: $domain (would be handled by root helper)",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun checkDNSStatus() {
        // Get domain to check status for
        val domainInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter domain to check status for"
            setText("example.com")
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Check DNS Spoofing Status")
            .setView(domainInput)
            .setPositiveButton("Check") { _, _ ->
                val domain = domainInput.text.toString().trim()

                if (domain.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Domain is required",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                // Log the status check
                com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "Checking DNS spoofing status for domain: $domain")

                val isActive = viewModel.isDNSSpoofingActive(domain)
                val statusMessage = "DNS Spoofing Status for $domain: ${if (isActive) "ACTIVE" else "INACTIVE"}"

                android.widget.Toast.makeText(
                    requireContext(),
                    statusMessage,
                    android.widget.Toast.LENGTH_LONG
                ).show()

                com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", statusMessage)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun startDHCPSpoofing() {
        // Get DHCP spoofing parameters from user input
        val targetMacInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter target MAC address (e.g., aa:bb:cc:dd:ee:ff)"
            setText("aa:bb:cc:dd:ee:ff")
        }

        val spoofedIpInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter spoofed IP address (e.g., 192.168.1.100)"
            setText("192.168.1.100")
        }

        val gatewayIpInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter gateway IP (e.g., 192.168.1.1)"
            setText("192.168.1.1")
        }

        val dnsServerInput = android.widget.EditText(requireContext()).apply {
            hint = "Enter DNS server (e.g., 8.8.8.8)"
            setText("8.8.8.8")
        }

        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(targetMacInput)
            addView(spoofedIpInput)
            addView(gatewayIpInput)
            addView(dnsServerInput)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Start DHCP Spoofing")
            .setView(layout)
            .setPositiveButton("Start") { _, _ ->
                val targetMac = targetMacInput.text.toString().trim()
                val spoofedIp = spoofedIpInput.text.toString().trim()
                val gatewayIp = gatewayIpInput.text.toString().trim()
                val dnsServer = dnsServerInput.text.toString().trim()

                if (targetMac.isEmpty() || spoofedIp.isEmpty() || gatewayIp.isEmpty() || dnsServer.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "All fields are required",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                // Log the DHCP spoofing start
                com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "Starting DHCP spoofing: $targetMac -> $spoofedIp")

                // Execute DHCP spoofing via ViewModel
                viewModel.startDHCPSpoofing(
                    interfaceName = "wlan0",
                    targetMacs = arrayOf(targetMac),
                    spoofedIPs = arrayOf(spoofedIp),
                    gatewayIPs = arrayOf(gatewayIp),
                    subnetMasks = arrayOf("255.255.255.0"),
                    dnsServers = arrayOf(dnsServer)
                )
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun stopDHCPSpoofing() {
        // Log the stop command
        com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "Stopping DHCP spoofing command issued")

        // Execute stop via ViewModel
        viewModel.stopDHCPSpoofing()

        android.widget.Toast.makeText(
            requireContext(),
            "DHCP spoofing stop command sent",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    private fun checkDHCPSpoofingStatus() {
        // Log the status check
        com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", "Checking DHCP spoofing status")

        val isActive = viewModel.isDHCPSpoofingActive()
        val statusMessage = "DHCP Spoofing Status: ${if (isActive) "ACTIVE" else "INACTIVE"}"

        android.widget.Toast.makeText(
            requireContext(),
            statusMessage,
            android.widget.Toast.LENGTH_LONG
        ).show()

        com.vishal.harpy.core.utils.LogUtils.d("DebugMenu", statusMessage)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}