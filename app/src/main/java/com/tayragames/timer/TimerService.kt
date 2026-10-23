package com.tayragames.timer

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the countdown in the foreground so the process survives while another app is on screen.
 * Being a foreground service is also what lets it take audio focus from the playing app.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var countdownJob: Job? = null
    private var timeUpJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TimerStore.load(this)
        when (intent?.action) {
            ACTION_START -> begin(
                TimerState(
                    endAt = intent.getLongExtra(EXTRA_END_AT, 0L),
                    totalMs = intent.getLongExtra(EXTRA_TOTAL_MS, 0L),
                )
            )
            ACTION_TIME_UP -> onTimeUp()
            ACTION_CANCEL -> cancelTimer()
            else -> resumeAfterRestart()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun begin(state: TimerState) {
        timeUpJob?.cancel()
        TimerStore.setTimer(this, state)
        if (!goForeground(Notifications.countdown(this, state.endAt))) return
        Alarms.schedule(this, state.endAt)
        countdownJob?.cancel()
        countdownJob = scope.launch {
            delay(state.endAt - System.currentTimeMillis())
            onTimeUp()
        }
    }

    private fun onTimeUp() {
        if (timeUpJob?.isActive == true) return
        countdownJob?.cancel()
        Alarms.cancel(this)
        val wasRunning = TimerStore.state.value != null
        TimerStore.setTimer(this, null)
        // The alarm starts this service with startForegroundService, which demands this call.
        val foreground = goForeground(Notifications.stopping(this))
        if (!wasRunning || !foreground) {
            stopService()
            return
        }
        timeUpJob = scope.launch {
            val muted = MediaStopper.stopPlayback(this@TimerService)
            val locked = DeviceLock.lock(this@TimerService)
            Notifications.showTimesUp(this@TimerService, locked, muted)
            stopService()
        }
    }

    private fun cancelTimer() {
        countdownJob?.cancel()
        Alarms.cancel(this)
        TimerStore.setTimer(this, null)
        stopService()
    }

    private fun resumeAfterRestart() {
        val state = TimerStore.state.value
        when {
            state == null -> stopService()
            state.endAt > System.currentTimeMillis() -> begin(state)
            else -> onTimeUp()
        }
    }

    private fun goForeground(notification: Notification): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifications.ID_FOREGROUND, notification, type)
        true
    } catch (_: IllegalStateException) {
        // Android refuses foreground starts from the background in some restart paths.
        false
    }

    private fun stopService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val ACTION_START = "com.tayragames.timer.START"
        const val ACTION_TIME_UP = "com.tayragames.timer.TIME_UP"
        private const val ACTION_CANCEL = "com.tayragames.timer.CANCEL"
        private const val EXTRA_END_AT = "end_at"
        private const val EXTRA_TOTAL_MS = "total_ms"

        fun start(context: Context, durationMs: Long) {
            MediaStopper.restoreVolume(context)
            startUntil(context, TimerState(System.currentTimeMillis() + durationMs, durationMs))
        }

        fun extend(context: Context, extraMs: Long) {
            val state = TimerStore.state.value ?: return
            startUntil(context, TimerState(state.endAt + extraMs, state.totalMs + extraMs))
        }

        fun cancel(context: Context) {
            TimerStore.setTimer(context, null)
            context.startService(Intent(context, TimerService::class.java).setAction(ACTION_CANCEL))
        }

        private fun startUntil(context: Context, state: TimerState) {
            TimerStore.setTimer(context, state)
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_END_AT, state.endAt)
                .putExtra(EXTRA_TOTAL_MS, state.totalMs)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
