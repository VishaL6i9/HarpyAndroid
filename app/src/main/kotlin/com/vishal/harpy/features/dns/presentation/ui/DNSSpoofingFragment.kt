package com.vishal.harpy.features.dns.presentation.ui

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

class DNSSpoofingFragment : Fragment() {

    private val viewModel: NetworkMonitorViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dns_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find buttons by ID
        val startDnsSpoofButton = view.findViewById<Button>(R.id.startDnsSpoofButton)
        val stopDnsSpoofButton = view.findViewById<Button>(R.id.stopDnsSpoofButton)
        val addDnsRuleButton = view.findViewById<Button>(R.id.addDnsRuleButton)
        val removeDnsRuleButton = view.findViewById<Button>(R.id.removeDnsRuleButton)
        val checkDnsStatusButton = view.findViewById<Button>(R.id.checkDnsStatusButton)

        // Set button click listeners
        startDnsSpoofButton.setOnClickListener {
            showStartDNSSpoofDialog()
        }

        stopDnsSpoofButton.setOnClickListener {
            showStopDNSSpoofDialog()
        }

        addDnsRuleButton.setOnClickListener {
            showAddDNSRuleDialog()
        }

        removeDnsRuleButton.setOnClickListener {
            showRemoveDNSRuleDialog()
        }

        checkDnsStatusButton.setOnClickListener {
            showCheckDNSStatusDialog()
        }
    }

    private fun showStartDNSSpoofDialog() {
        val domainInput = EditText(requireContext()).apply {
            hint = "Enter domain to spoof (e.g., example.com)"
            setText("example.com")
        }

        val ipInput = EditText(requireContext()).apply {
            hint = "Enter spoofed IP (e.g., 8.8.8.8)"
            setText("8.8.8.8")
        }

        val interfaceInput = EditText(requireContext()).apply {
            hint = "Enter network interface (e.g., wlan0)"
            setText("wlan0")
        }

        val dialogLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(domainInput)
            addView(ipInput)
            addView(interfaceInput)
            setPadding(32, 32, 32, 32)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Start DNS Spoofing")
            .setView(dialogLayout)
            .setPositiveButton("Start") { _, _ ->
                val domain = domainInput.text.toString().trim()
                val ip = ipInput.text.toString().trim()
                val interfaceName = interfaceInput.text.toString().trim()

                if (domain.isEmpty() || ip.isEmpty()) {
                    Toast.makeText(requireContext(), "Domain and IP are required", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                viewModel.startDNSSpoofing(domain, ip, interfaceName)
                Toast.makeText(requireContext(), "Starting DNS spoofing for $domain -> $ip", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showStopDNSSpoofDialog() {
        val domainInput = EditText(requireContext()).apply {
            hint = "Enter domain to stop spoofing for"
            setText("example.com")
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Stop DNS Spoofing")
            .setView(domainInput)
            .setPositiveButton("Stop") { _, _ ->
                val domain = domainInput.text.toString().trim()
                if (domain.isEmpty()) {
                    Toast.makeText(requireContext(), "Domain is required", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                viewModel.stopDNSSpoofing(domain)
                Toast.makeText(requireContext(), "DNS spoofing stop command sent for $domain", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showAddDNSRuleDialog() {
        val domainInput = EditText(requireContext()).apply {
            hint = "Enter domain to spoof (e.g., example.com)"
            setText("test.example.com")
        }

        val ipInput = EditText(requireContext()).apply {
            hint = "Enter spoofed IP (e.g., 8.8.8.8)"
            setText("8.8.8.8")
        }

        val dialogLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(domainInput)
            addView(ipInput)
            setPadding(32, 32, 32, 32)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add DNS Rule")
            .setView(dialogLayout)
            .setPositiveButton("Add") { _, _ ->
                val domain = domainInput.text.toString().trim()
                val ip = ipInput.text.toString().trim()

                if (domain.isEmpty() || ip.isEmpty()) {
                    Toast.makeText(requireContext(), "Domain and IP are required", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                // In a real implementation, this would add the rule to the DNS spoofing system
                Toast.makeText(requireContext(), "DNS rule added: $domain -> $ip", Toast.LENGTH_LONG).show()
                LogUtils.d("DNSSettings", "DNS rule added: $domain -> $ip")
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showRemoveDNSRuleDialog() {
        val domainInput = EditText(requireContext()).apply {
            hint = "Enter domain to remove from spoofing rules"
            setText("example.com")
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Remove DNS Rule")
            .setView(domainInput)
            .setPositiveButton("Remove") { _, _ ->
                val domain = domainInput.text.toString().trim()

                if (domain.isEmpty()) {
                    Toast.makeText(requireContext(), "Domain is required", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                // In a real implementation, this would remove the rule from the DNS spoofing system
                Toast.makeText(requireContext(), "DNS rule removed for: $domain", Toast.LENGTH_LONG).show()
                LogUtils.d("DNSSettings", "DNS rule removed for: $domain")
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showCheckDNSStatusDialog() {
        val domainInput = EditText(requireContext()).apply {
            hint = "Enter domain to check status for"
            setText("example.com")
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Check DNS Status")
            .setView(domainInput)
            .setPositiveButton("Check") { _, _ ->
                val domain = domainInput.text.toString().trim()

                if (domain.isEmpty()) {
                    Toast.makeText(requireContext(), "Domain is required", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val isActive = viewModel.isDNSSpoofingActive(domain)
                val status = if (isActive) "ACTIVE" else "INACTIVE"
                val message = "DNS spoofing for $domain: $status"

                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                LogUtils.d("DNSSettings", message)
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }
}