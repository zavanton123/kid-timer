package com.tayragames.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Backup trigger for the time-up event in case the service's own countdown gets delayed or killed. */
object Alarms {
    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context).canScheduleExactAlarms()

    fun schedule(context: Context, endAt: Long) {
        val manager = alarmManager(context)
        val operation = timeUpIntent(context)
        if (canScheduleExact(context)) {
            val info = AlarmManager.AlarmClockInfo(endAt, Notifications.openAppIntent(context))
            manager.setAlarmClock(info, operation)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, operation)
        }
    }

    fun cancel(context: Context) {
        alarmManager(context).cancel(timeUpIntent(context))
    }

    private fun timeUpIntent(context: Context): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            0,
            Intent(context, TimerService::class.java).setAction(TimerService.ACTION_TIME_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
