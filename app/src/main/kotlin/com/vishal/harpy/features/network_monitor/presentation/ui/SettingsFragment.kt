package com.vishal.harpy.features.network_monitor.presentation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.vishal.harpy.R
import com.vishal.harpy.features.network_monitor.presentation.viewmodel.NetworkMonitorViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: View? = null
    private val binding get() = _binding!!
    private val viewModel: NetworkMonitorViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflater.inflate(R.layout.fragment_settings, container, false)
        
        // Apply right-to-left slide animation
        ViewCompat.setOnApplyWindowInsetsListener(_binding!!) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val mandatorySystemGestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            
            // Combine system bars and mandatory gestures to ensure proper spacing
            view.setPadding(
                systemBars.left, 
                systemBars.top, 
                systemBars.right, 
                Math.max(systemBars.bottom, mandatorySystemGestures.bottom)
            )
            insets
        }
        
        return binding
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply right-to-left entry animation
        animateSlideFromRight()

        val backButton = binding.findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            // Apply right-to-left exit animation before navigating back
            animateSlideToRight {
                requireActivity().supportFragmentManager.popBackStack()
            }
        }

        // Handle About item click
        val aboutItem = binding.findViewById<LinearLayout>(R.id.aboutItem)
        aboutItem.setOnClickListener {
            // For now, just show a toast - later we can implement navigation to an About screen
            android.widget.Toast.makeText(context, "About clicked", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Handle Clear All Device Names item click
        val clearNamesItem = binding.findViewById<LinearLayout>(R.id.clearNamesItem)
        clearNamesItem.setOnClickListener {
            showClearNamesConfirmation()
        }
    }

    private fun showClearNamesConfirmation() {
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
    }

    private fun animateSlideFromRight() {
        val animation = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 1.0f, // Start from right (100% of parent width)
            Animation.RELATIVE_TO_PARENT, 0.0f, // End at normal position (0% of parent width)
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f
        )
        animation.duration = 300 // 300ms duration
        binding.startAnimation(animation)
    }

    private fun animateSlideToRight(onComplete: () -> Unit) {
        val animation = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0.0f, // Start from normal position
            Animation.RELATIVE_TO_PARENT, 1.0f, // End at right (100% of parent width)
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f
        )
        animation.duration = 300 // 300ms duration
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationRepeat(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                onComplete()
            }
        })
        binding.startAnimation(animation)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}