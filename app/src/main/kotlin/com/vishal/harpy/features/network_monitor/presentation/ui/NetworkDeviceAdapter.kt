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
    private val onUnblockClick: (NetworkDevice) -> Unit,
    private val onPinClick: (NetworkDevice) -> Unit,
    private val onEditNameClick: (NetworkDevice) -> Unit
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
            val vendor = view.findViewById<TextView>(R.id.vendor)
            val blockedStatus = view.findViewById<TextView>(R.id.blockedStatus)
            val blockButton = view.findViewById<Button>(R.id.blockButton)
            val unblockButton = view.findViewById<Button>(R.id.unblockButton)
            val pinButton = view.findViewById<Button>(R.id.pinButton)
            val editNameButton = view.findViewById<Button>(R.id.editNameButton)

            ipAddress.text = item.ipAddress
            macAddress.text = item.macAddress
            hostname.text = item.hostname ?: "Unknown"
            deviceType.text = item.deviceType ?: "Unknown"
            vendor.text = item.getDisplayName()

            // Pin button
            pinButton.text = if (item.isPinned) "📌 Pinned" else "📍 Pin"
            pinButton.setOnClickListener { onPinClick(item) }

            // Edit name button
            editNameButton.setOnClickListener { onEditNameClick(item) }

            if (item.isBlocked) {
                blockedStatus.visibility = View.VISIBLE
                blockButton.visibility = View.GONE
                unblockButton.visibility = View.VISIBLE
                unblockButton.setOnClickListener { onUnblockClick(item) }
            } else {
                blockedStatus.visibility = View.GONE
                blockButton.visibility = View.VISIBLE
                unblockButton.visibility = View.GONE
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