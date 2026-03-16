package com.vishal.harpy.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ServiceKillReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == NotificationService.ACTION_KILL_SERVICE) {
            Log.d(TAG, "Kill service action received")
            
            context?.let {
                val serviceIntent = Intent(it, NotificationService::class.java)
                it.stopService(serviceIntent)
                Log.d(TAG, "Notification service stopped gracefully")
            }
        }
    }

    companion object {
        private const val TAG = "ServiceKillReceiver"
    }
}
