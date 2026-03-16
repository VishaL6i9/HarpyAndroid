package com.vishal.harpy.core.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
        Log.d(TAG, "NotificationService started")
        
        // Handle notification cleared action
        if (intent?.action == ACTION_NOTIFICATION_CLEARED) {
            Log.d(TAG, "Notification was cleared, reposting")
            repostNotification()
            return START_STICKY
        }
        
        try {
            val notification = notificationManager.buildServiceNotification()
            
            // Post notification without using foreground service
            // This keeps it out of the status bar but visible in drawer
            notifManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification posted (non-foreground)")
            
            // Observe spoofing state changes and update notification
            observeSpoofingStateChanges()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification", e)
            stopSelf()
            return START_NOT_STICKY
        }
        
        return START_STICKY
    }

    private fun repostNotification() {
        try {
            val notification = notificationManager.buildServiceNotification(spoofingStateManager.getCurrentState())
            notifManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification reposted after being cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error reposting notification", e)
        }
    }

    private fun observeSpoofingStateChanges() {
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
