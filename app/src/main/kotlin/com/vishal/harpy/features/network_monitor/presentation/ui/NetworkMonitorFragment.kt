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
        debugButton.setOnClickListener { showDebugMenu(it) }
    }

    private fun setupRecyclerView() {
        val recyclerView = binding.findViewById<RecyclerView>(R.id.devicesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = NetworkDeviceAdapter(
            onBlockClick = { device -> viewModel.blockDevice(device) },
            onUnblockClick = { device -> viewModel.unblockDevice(device) },
            onPinClick = { device -> viewModel.toggleDevicePin(device) },
            onEditNameClick = { device -> showEditNameDialog(device) },
            onLongPress = { device, view -> showContextMenu(device, view) }
        )
        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.networkDevices.collect { devices ->
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
        val deviceCountText = binding.findViewById<TextView>(R.id.deviceCount)
        
        if (count > 0) {
            deviceCountSection.visibility = View.VISIBLE
            deviceCountText.text = count.toString()
        } else {
            deviceCountSection.visibility = View.GONE
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        val emptyState = binding.findViewById<LinearLayout>(R.id.emptyState)
        val recyclerView = binding.findViewById<RecyclerView>(R.id.devicesRecyclerView)
        
        if (isEmpty) {
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

    private fun showContextMenu(device: NetworkDevice, view: View) {
        androidx.appcompat.widget.PopupMenu(requireContext(), view).apply {
            menu.add(0, 1, 0, if (device.isPinned) "Unpin" else "Pin")
            menu.add(0, 2, 1, "Set Name")
            menu.add(0, 3, 2, if (device.isBlocked) "Unblock" else "Block")
            
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        viewModel.toggleDevicePin(device)
                        android.widget.Toast.makeText(
                            requireContext(),
                            if (device.isPinned) "Device unpinned" else "Device pinned",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        true
                    }
                    2 -> {
                        showEditNameDialog(device)
                        true
                    }
                    3 -> {
                        if (device.isBlocked) {
                            viewModel.unblockDevice(device)
                        } else {
                            viewModel.blockDevice(device)
                        }
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showDebugMenu(view: View) {
        androidx.appcompat.widget.PopupMenu(requireContext(), view).apply {
            menu.add(0, 1, 0, "Clear all custom names")
            
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
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
                        true
                    }
                    else -> false
                }
            }
            show()
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}