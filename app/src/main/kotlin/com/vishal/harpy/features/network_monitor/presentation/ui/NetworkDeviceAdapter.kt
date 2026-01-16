package com.vishal.harpy.features.network_monitor.presentation.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vishal.harpy.R
import com.vishal.harpy.core.utils.NetworkDevice

class NetworkDeviceAdapter(
    private val onBlockClick: (NetworkDevice) -> Unit,
    private val onUnblockClick: (NetworkDevice) -> Unit
) : ListAdapter<NetworkDevice, NetworkDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_network_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val view: View
    ) : RecyclerView.ViewHolder(view) {

        fun bind(item: NetworkDevice) {
            val ipAddress = view.findViewById<TextView>(R.id.ipAddress)
            val macAddress = view.findViewById<TextView>(R.id.macAddress)
            val hostname = view.findViewById<TextView>(R.id.hostname)
            val deviceType = view.findViewById<TextView>(R.id.deviceType)
            val blockButton = view.findViewById<Button>(R.id.blockButton)

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