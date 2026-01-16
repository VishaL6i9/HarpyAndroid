package com.vishal.harpy

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import com.vishal.harpy.R

class DeviceAdapter(
    private val context: Context,
    private val devices: MutableList<NetworkDevice>,
    private val onBlockClickListener: (NetworkDevice) -> Unit
) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = devices.size

    override fun getItem(position: Int): NetworkDevice = devices[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            view = inflater.inflate(R.layout.device_item, parent, false)
            viewHolder = ViewHolder(
                view.findViewById(R.id.ip_address),
                view.findViewById(R.id.mac_address),
                view.findViewById(R.id.hostname),
                view.findViewById(R.id.block_button)
            )
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val device = devices[position]
        viewHolder.ipAddress.text = device.ipAddress
        viewHolder.macAddress.text = device.macAddress
        viewHolder.hostname.text = device.hostname ?: "Unknown"

        // Set button text based on block status
        viewHolder.blockButton.text = if (device.isBlocked) "Unblock" else "Block"
        viewHolder.blockButton.setOnClickListener {
            onBlockClickListener(device)
        }

        return view
    }

    fun updateDevices(newDevices: List<NetworkDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    private class ViewHolder(
        val ipAddress: TextView,
        val macAddress: TextView,
        val hostname: TextView,
        val blockButton: Button
    )
}