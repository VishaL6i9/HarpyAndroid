package com.vishal.harpy.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.vishal.harpy.R
import com.vishal.harpy.core.ui.PermissionsActivity
import com.vishal.harpy.core.utils.PermissionChecker
import com.vishal.harpy.features.network_monitor.presentation.ui.NetworkMonitorFragment
import com.vishal.harpy.features.dns.presentation.ui.DNSSpoofingFragment
import com.vishal.harpy.features.dhcp.presentation.ui.DHCPSpoofingFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if all required permissions are granted
        if (!PermissionChecker.areAllPermissionsGranted(this)) {
            // If not all permissions are granted, show the permissions activity
            val intent = Intent(this, PermissionsActivity::class.java)
            startActivity(intent)
            finish() // Finish this activity so the user returns here after granting permissions
            return
        }

        setContentView(R.layout.activity_main)

        enableImmersiveMode()

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            // Load the Network Monitor fragment by default
            loadFragment(NetworkMonitorFragment())
            bottomNavigationView.selectedItemId = R.id.nav_network_monitor
        }

        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_network_monitor -> {
                    loadFragment(NetworkMonitorFragment())
                    true
                }
                R.id.nav_dns_spoofing -> {
                    loadFragment(DNSSpoofingFragment())
                    true
                }
                R.id.nav_dhcp_spoofing -> {
                    loadFragment(DHCPSpoofingFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                // Hide status bars and navigation bars
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())

                // Respect cutout areas by allowing content to extend into them
                window.attributes.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

                // Set behavior for system bars
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            // Enable immersive sticky mode
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )

            // For older versions, set the layout cutout mode to handle notches properly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}