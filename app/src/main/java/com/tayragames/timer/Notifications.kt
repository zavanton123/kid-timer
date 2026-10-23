package com.tayragames.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Date

object Notifications {
    private const val CHANNEL_COUNTDOWN = "countdown"
    private const val CHANNEL_TIMES_UP = "times_up"
    const val ID_FOREGROUND = 1
    private const val ID_TIMES_UP = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COUNTDOWN,
                context.getString(R.string.channel_countdown),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TIMES_UP,
                context.getString(R.string.channel_times_up),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun countdown(context: Context, endAt: Long): Notification {
        val time = DateFormat.getTimeFormat(context).format(Date(endAt))
        return NotificationCompat.Builder(context, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.notif_countdown_title))
            .setContentText(context.getString(R.string.locks_at, time))
            .setWhen(endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openAppIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun stopping(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.notif_stopping))
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(openAppIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun showTimesUp(context: Context, locked: Boolean, muted: Boolean) {
        if (!hasPermission(context)) return
        val text = buildString {
            append(
                context.getString(
                    if (locked) R.string.notif_times_up_locked else R.string.notif_times_up_not_locked
                )
            )
            if (muted) append(' ').append(context.getString(R.string.notif_times_up_muted))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_TIMES_UP)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.notif_times_up_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_TIMES_UP, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
