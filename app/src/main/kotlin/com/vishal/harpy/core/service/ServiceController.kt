package com.vishal.harpy.core.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceController @Inject constructor(
    private val context: Context
) {

    fun startNotificationService() {
        try {
            val intent = Intent(context, NotificationService::class.java)
            ContextCompat.startForegroundService(context, intent)
            Log.d(TAG, "Notification service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting notification service", e)
        }
    }

    fun stopNotificationService() {
        try {
            val intent = Intent(context, NotificationService::class.java)
            context.stopService(intent)
            Log.d(TAG, "Notification service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping notification service", e)
        }
    }

    fun isServiceRunning(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any {
            it.service.className == NotificationService::class.java.name
        }
    }

    companion object {
        private const val TAG = "ServiceController"
    }
}
