package com.vishal.harpy.features.network_monitor.presentation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vishal.harpy.databinding.FragmentNetworkMonitorBinding
import com.vishal.harpy.features.network_monitor.di.NetworkMonitorModule
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel
import kotlinx.coroutines.launch

class NetworkMonitorFragment : Fragment() {
    
    private var _binding: FragmentNetworkMonitorBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: NetworkMonitorViewModel by viewModels {
        // Factory would be provided via DI
        NetworkMonitorModule.provideViewModelFactory()
    }
    
    private lateinit var adapter: NetworkDeviceAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetworkMonitorBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
        
        binding.scanButton.setOnClickListener {
            viewModel.scanNetwork()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = NetworkDeviceAdapter(
            onBlockClick = { device -> viewModel.blockDevice(device) },
            onUnblockClick = { device -> viewModel.unblockDevice(device) }
        )
        binding.devicesRecyclerView.adapter = adapter
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.networkDevices.collect { devices ->
                    adapter.submitList(devices)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoading ->
                    binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { error ->
                    error?.let {
                        // Show error message
                    }
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}