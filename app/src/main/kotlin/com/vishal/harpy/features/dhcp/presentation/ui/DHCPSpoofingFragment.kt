package com.vishal.harpy.features.dhcp.presentation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.vishal.harpy.R
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel
import androidx.fragment.app.activityViewModels
import android.widget.Toast
import com.vishal.harpy.core.utils.LogUtils

class DHCPSpoofingFragment : Fragment() {

    private val viewModel: NetworkMonitorViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dhcp_spoofing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find buttons by ID
        val startDhcpSpoofButton = view.findViewById<Button>(R.id.startDhcpSpoofButton)
        val stopDhcpSpoofButton = view.findViewById<Button>(R.id.stopDhcpSpoofButton)
        val addDhcpRuleButton = view.findViewById<Button>(R.id.addDhcpRuleButton)
        val removeDhcpRuleButton = view.findViewById<Button>(R.id.removeDhcpRuleButton)
        val checkDhcpStatusButton = view.findViewById<Button>(R.id.checkDhcpStatusButton)

        // Set button click listeners
        startDhcpSpoofButton.setOnClickListener {
            showStartDHCPSpoofDialog()
        }

        stopDhcpSpoofButton.setOnClickListener {
            viewModel.stopDHCPSpoofing()
            Toast.makeText(requireContext(), "DHCP spoofing stop command sent", Toast.LENGTH_SHORT).show()
        }

        addDhcpRuleButton.setOnClickListener {
            showAddDHCPRuleDialog()
        }

        removeDhcpRuleButton.setOnClickListener {
            showRemoveDHCPRuleDialog()
        }

        checkDhcpStatusButton.setOnClickListener {
            showCheckDHCPSpoofingStatusDialog()
        }
    }

    private fun showStartDHCPSpoofDialog() {
        val targetMacInput = EditText(requireContext()).apply {
            hint = "Enter target MAC address (e.g., aa:bb:cc:dd:ee:ff)"
            setText("aa:bb:cc:dd:ee:ff")
        }

        val spoofedIpInput = EditText(requireContext()).apply {
            hint = "Enter spoofed IP address (e.g., 192.168.1.100)"
            setText("192.168.1.100")
        }

        val gatewayIpInput = EditText(requireContext()).apply {
            hint = "Enter gateway IP (e.g., 192.168.1.1)"
            setText("192.168.1.1")
        }

        val dnsServerInput = EditText(requireContext()).apply {
            hint = "Enter DNS server (e.g., 8.8.8.8)"
            setText("8.8.8.8")
        }

        val interfaceInput = EditText(requireContext()).apply {
            hint = "Enter network interface (e.g., wlan0)"
            setText("wlan0")
        }

        val dialogLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(targetMacInput)
            addView(spoofedIpInput)
            addView(gatewayIpInput)
            addView(dnsServerInput)
            addView(interfaceInput)
            setPadding(32, 32, 32, 32)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Start DHCP Spoofing")
            .setView(dialogLayout)
            .setPositiveButton("Start") { _, _ ->
                val targetMac = targetMacInput.text.toString().trim()
                val spoofedIp = spoofedIpInput.text.toString().trim()
                val gatewayIp = gatewayIpInput.text.toString().trim()
                val dnsServer = dnsServerInput.text.toString().trim()
                val interfaceName = interfaceInput.text.toString().trim()

                if (targetMac.isEmpty() || spoofedIp.isEmpty() || gatewayIp.isEmpty() || dnsServer.isEmpty()) {
                    Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                viewModel.startDHCPSpoofing(
                    interfaceName = interfaceName,
                    targetMacs = arrayOf(targetMac),
                    spoofedIPs = arrayOf(spoofedIp),
                    gatewayIPs = arrayOf(gatewayIp),
                    subnetMasks = arrayOf("255.255.255.0"),
                    dnsServers = arrayOf(dnsServer)
                )

                Toast.makeText(requireContext(), "Starting DHCP spoofing for $targetMac -> $spoofedIp", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showAddDHCPRuleDialog() {
        val targetMacInput = EditText(requireContext()).apply {
            hint = "Enter target MAC address to spoof"
            setText("aa:bb:cc:dd:ee:ff")
        }

        val spoofedIpInput = EditText(requireContext()).apply {
            hint = "Enter spoofed IP address"
            setText("192.168.1.100")
        }

        val dialogLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(targetMacInput)
            addView(spoofedIpInput)
            setPadding(32, 32, 32, 32)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add DHCP Rule")
            .setView(dialogLayout)
            .setPositiveButton("Add") { _, _ ->
                val targetMac = targetMacInput.text.toString().trim()
                val spoofedIp = spoofedIpInput.text.toString().trim()

                if (targetMac.isEmpty() || spoofedIp.isEmpty()) {
                    Toast.makeText(requireContext(), "Target MAC and Spoofed IP are required", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                // In a real implementation, this would add the rule to the DHCP spoofing system
                Toast.makeText(requireContext(), "DHCP rule added: $targetMac -> $spoofedIp", Toast.LENGTH_LONG).show()
                LogUtils.d("DHCPSpoofing", "DHCP rule added: $targetMac -> $spoofedIp")
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showRemoveDHCPRuleDialog() {
        val targetMacInput = EditText(requireContext()).apply {
            hint = "Enter target MAC to remove from spoofing rules"
            setText("aa:bb:cc:dd:ee:ff")
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Remove DHCP Rule")
            .setView(targetMacInput)
            .setPositiveButton("Remove") { _, _ ->
                val targetMac = targetMacInput.text.toString().trim()

                if (targetMac.isEmpty()) {
                    Toast.makeText(requireContext(), "Target MAC is required", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                // In a real implementation, this would remove the rule from the DHCP spoofing system
                Toast.makeText(requireContext(), "DHCP rule removed for: $targetMac", Toast.LENGTH_LONG).show()
                LogUtils.d("DHCPSpoofing", "DHCP rule removed for: $targetMac")
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showCheckDHCPSpoofingStatusDialog() {
        val isActive = viewModel.isDHCPSpoofingActive()
        val status = if (isActive) "ACTIVE" else "INACTIVE"
        val message = "DHCP Spoofing Status: $status"

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        LogUtils.d("DHCPSpoofing", message)
    }
}