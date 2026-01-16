package com.vishal.harpy.features.network_monitor.presentation.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vishal.harpy.core.utils.NetworkDevice
import com.vishal.harpy.databinding.ItemNetworkDeviceBinding

class NetworkDeviceAdapter(
    private val onBlockClick: (NetworkDevice) -> Unit,
    private val onUnblockClick: (NetworkDevice) -> Unit
) : ListAdapter<NetworkDevice, NetworkDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemNetworkDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class DeviceViewHolder(
        private val binding: ItemNetworkDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: NetworkDevice) {
            binding.apply {
                ipAddress.text = item.ipAddress
                macAddress.text = item.macAddress
                hostname.text = item.hostname ?: "Unknown"
                deviceType.text = item.deviceType ?: "Unknown"
                
                if (item.isBlocked) {
                    blockButton.text = "Unblock"
                    blockButton.setOnClickListener { onUnblockClick(item) }
                } else {
                    blockButton.text = "Block"
                    blockButton.setOnClickListener { onBlockClick(item) }
                }
            }
        }
    }
    
    companion object {
        val DeviceDiffCallback = object : DiffUtil.ItemCallback<NetworkDevice>() {
            override fun areItemsTheSame(oldItem: NetworkDevice, newItem: NetworkDevice): Boolean {
                return oldItem.ipAddress == newItem.ipAddress
            }
            
            override fun areContentsTheSame(oldItem: NetworkDevice, newItem: NetworkDevice): Boolean {
                return oldItem == newItem
            }
        }
    }
}