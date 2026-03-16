package com.vishal.harpy.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vishal.harpy.R
import com.vishal.harpy.core.state.SpoofingState
import com.vishal.harpy.main.MainActivityCompose
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HarpyNotificationManager @Inject constructor(
    private val context: Context
) {

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // NotificationChannel only available on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationService.CHANNEL_ID,
                "Harpy Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Harpy network monitoring service"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildServiceNotification(spoofingState: SpoofingState = SpoofingState()): Notification {
        val mainActivityIntent = Intent(context, MainActivityCompose::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val killServiceIntent = Intent(context, ServiceKillReceiver::class.java).apply {
            action = NotificationService.ACTION_KILL_SERVICE
        }

        // Intent to handle notification deletion/clearing
        val notificationClearedIntent = Intent(context, NotificationService::class.java).apply {
            action = NotificationService.ACTION_NOTIFICATION_CLEARED
        }

        // Build PendingIntents based on SDK version
        val mainActivityPendingIntent = buildActivityPendingIntent(mainActivityIntent, 0)
        val killServicePendingIntent = buildBroadcastPendingIntent(killServiceIntent, 1)
        val notificationClearedPendingIntent = buildServicePendingIntent(notificationClearedIntent, 2)

        // Build title with spoofing status
        val title = buildTitle(spoofingState)
        val bodyText = "Tap to notification to open Harpy"

        // Build notification based on SDK version
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> buildNotificationForS(title, bodyText, mainActivityPendingIntent, killServicePendingIntent, notificationClearedPendingIntent)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> buildNotificationForO(title, bodyText, mainActivityPendingIntent, killServicePendingIntent, notificationClearedPendingIntent)
            else -> buildNotificationForN(title, bodyText, mainActivityPendingIntent, killServicePendingIntent, notificationClearedPendingIntent)
        }
    }

    private fun buildTitle(spoofingState: SpoofingState): String {
        return if (spoofingState.statusText.isNotEmpty()) {
            "Harpy Active • ${spoofingState.statusText}"
        } else {
            "Harpy Active"
        }
    }

    private fun buildActivityPendingIntent(intent: Intent, requestCode: Int): PendingIntent {
        val flags = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
            else -> {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    private fun buildBroadcastPendingIntent(intent: Intent, requestCode: Int): PendingIntent {
        val flags = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
            else -> {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun buildServicePendingIntent(intent: Intent, requestCode: Int): PendingIntent {
        val flags = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
            else -> {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        }
        return PendingIntent.getService(context, requestCode, intent, flags)
    }

    // API 31+ (S - Android 12+)
    private fun buildNotificationForS(
        title: String,
        bodyText: String,
        mainActivityPendingIntent: PendingIntent,
        killServicePendingIntent: PendingIntent,
        notificationClearedPendingIntent: PendingIntent
    ): Notification {
        return NotificationCompat.Builder(context, NotificationService.CHANNEL_ID)
            .setContentTitle(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(mainActivityPendingIntent)
            .addAction(0, "Kill Service", killServicePendingIntent)
            .setDeleteIntent(notificationClearedPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // API 26-30 (O-R - Android 8-11)
    private fun buildNotificationForO(
        title: String,
        bodyText: String,
        mainActivityPendingIntent: PendingIntent,
        killServicePendingIntent: PendingIntent,
        notificationClearedPendingIntent: PendingIntent
    ): Notification {
        return NotificationCompat.Builder(context, NotificationService.CHANNEL_ID)
            .setContentTitle(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(mainActivityPendingIntent)
            .addAction(0, "Kill Service", killServicePendingIntent)
            .setDeleteIntent(notificationClearedPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // API 24-25 (N - Android 7)
    private fun buildNotificationForN(
        title: String,
        bodyText: String,
        mainActivityPendingIntent: PendingIntent,
        killServicePendingIntent: PendingIntent,
        notificationClearedPendingIntent: PendingIntent
    ): Notification {
        return NotificationCompat.Builder(context, NotificationService.CHANNEL_ID)
            .setContentTitle(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(mainActivityPendingIntent)
            .addAction(0, "Kill Service", killServicePendingIntent)
            .setDeleteIntent(notificationClearedPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .build()
    }
}
