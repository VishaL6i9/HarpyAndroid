package com.vishal.harpy.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vishal.harpy.R
import com.vishal.harpy.features.network_monitor.presentation.ui.NetworkMonitorFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, NetworkMonitorFragment())
                .commitNow()
        }
    }
}