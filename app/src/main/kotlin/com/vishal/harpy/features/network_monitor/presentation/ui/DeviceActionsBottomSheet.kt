package com.vishal.harpy.features.network_monitor.presentation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vishal.harpy.R
import com.vishal.harpy.core.utils.NetworkDevice

class DeviceActionsBottomSheet(
    private val device: NetworkDevice,
    private val onPinClick: (NetworkDevice) -> Unit,
    private val onEditNameClick: (NetworkDevice) -> Unit,
    private val onBlockClick: (NetworkDevice) -> Unit,
    private val onPingClick: (NetworkDevice) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_device_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader(view)
        setupActions(view)
    }

    private fun setupHeader(view: View) {
        val nameText = view.findViewById<TextView>(R.id.deviceHeaderName)
        val infoText = view.findViewById<TextView>(R.id.deviceHeaderInfo)
        val icon = view.findViewById<ImageView>(R.id.deviceHeaderIcon)

        nameText.text = device.getDisplayName()
        infoText.text = "${device.ipAddress} • ${device.macAddress}"

        when {
            device.isGateway -> {
                icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_active))
                infoText.append(" • Gateway")
            }
            device.isCurrentDevice -> {
                icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_info))
                infoText.append(" • You")
            }
            device.isBlocked -> {
                icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_blocked))
            }
        }
    }

    private fun setupActions(view: View) {
        val pinAction = view.findViewById<View>(R.id.actionPin)
        val pinIcon = view.findViewById<ImageView>(R.id.iconPin)
        val pinText = view.findViewById<TextView>(R.id.textPin)

        val btnBlock = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBlock)

        // Pin State
        if (device.isPinned) {
            pinText.text = "Unpin Device"
            pinIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_color))
        }

        pinAction.setOnClickListener {
            onPinClick(device)
            dismiss()
        }

        // Edit Name
        view.findViewById<View>(R.id.actionEditName).setOnClickListener {
            onEditNameClick(device)
            dismiss()
        }

        // Test Ping
        view.findViewById<View>(R.id.actionTestPing).setOnClickListener {
            onPingClick(device)
            dismiss()
        }

        // Block State
        if (device.isBlocked) {
            btnBlock.text = "Unblock Device"
            btnBlock.setIconResource(R.drawable.ic_ping)
            btnBlock.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.status_active)
        } else if (device.isGateway) {
            btnBlock.text = "Nuclear Option (Block All)"
            btnBlock.setIconResource(R.drawable.ic_danger)
            btnBlock.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.status_blocked)
        } else {
            // Default Block State (Explicitly set to ensure correct state if view is reused/recreated)
            btnBlock.text = "Target Blocking"
            btnBlock.setIconResource(R.drawable.ic_block)
            btnBlock.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.status_blocked)
        }

        btnBlock.setOnClickListener {
            onBlockClick(device)
            dismiss()
        }
    }

    companion object {
        const val TAG = "DeviceActionsBottomSheet"
    }
}
