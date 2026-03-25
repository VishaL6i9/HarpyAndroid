package com.vishal.harpy.core.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.vishal.harpy.core.state.SpoofingStateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationService : Service() {

    @Inject
    lateinit var notificationManager: HarpyNotificationManager

    @Inject
    lateinit var spoofingStateManager: SpoofingStateManager

    private var serviceJob: Job? = null
    private lateinit var notifManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationService created")
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "NotificationService started with action: ${intent?.action}")
        
        // Start foreground as early as possible to avoid ForegroundServiceDidNotStartInTimeException
        try {
            val notification = notificationManager.buildServiceNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "NotificationService moved to foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            // If we failed to start foreground, we must stop the service to avoid the crash
            // though the crash might happen anyway if startForegroundService was called.
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Handle notification cleared action
        if (intent?.action == ACTION_NOTIFICATION_CLEARED) {
            Log.d(TAG, "Notification was cleared, updating notification")
            updateNotification()
            return START_STICKY
        }
        
        // Start observing state changes
        if (serviceJob == null || serviceJob?.isActive == false) {
            observeSpoofingStateChanges()
        }
        
        return START_STICKY
    }

    private fun updateNotification() {
        try {
            val notification = notificationManager.buildServiceNotification(spoofingStateManager.getCurrentState())
            notifManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    private fun observeSpoofingStateChanges() {
        serviceJob?.cancel()
        serviceJob = CoroutineScope(Dispatchers.Main).launch {
            spoofingStateManager.spoofingState.collect { state ->
                Log.d(TAG, "Spoofing state changed: DNS=${state.isDnsSpoofingActive}, DHCP=${state.isDhcpSpoofingActive}")
                
                // Update notification with new spoofing state
                val updatedNotification = notificationManager.buildServiceNotification(state)
                notifManager.notify(NOTIFICATION_ID, updatedNotification)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "NotificationService destroyed")
        serviceJob?.cancel()
        notifManager.cancel(NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "NotificationService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "harpy_service_channel"
        const val ACTION_KILL_SERVICE = "com.vishal.harpy.KILL_SERVICE"
        const val ACTION_NOTIFICATION_CLEARED = "com.vishal.harpy.NOTIFICATION_CLEARED"
    }
}
