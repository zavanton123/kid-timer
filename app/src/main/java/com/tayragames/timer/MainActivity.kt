package com.tayragames.timer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tayragames.timer.ui.KidTimerActions
import com.tayragames.timer.ui.KidTimerScreen
import com.tayragames.timer.ui.SetupStatus
import com.tayragames.timer.ui.theme.KidTimerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var setupVersion by mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { setupVersion++ }

    private val enableAdmin =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { setupVersion++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TimerStore.load(this)
        Notifications.createChannels(this)
        setContent {
            KidTimerTheme { KidTimerRoute() }
        }
    }

    @Composable
    private fun KidTimerRoute() {
        val timer by TimerStore.state.collectAsStateWithLifecycle()
        val savedVolume by TimerStore.savedVolume.collectAsStateWithLifecycle()
        var setup by remember { mutableStateOf(readSetup()) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

        LifecycleResumeEffect(setupVersion) {
            setup = readSetup()
            clearStaleTimer()
            onPauseOrDispose { }
        }
        LaunchedEffect(timer) {
            while (timer != null) {
                now = System.currentTimeMillis()
                delay(250)
            }
        }

        KidTimerScreen(
            timer = timer,
            now = now,
            setup = setup,
            soundMuted = savedVolume >= 0,
            actions = remember { actions() },
        )
    }

    private fun actions() = KidTimerActions(
        onStart = { minutes -> TimerService.start(this, minutes * 60_000L) },
        onExtend = { minutes -> TimerService.extend(this, minutes * 60_000L) },
        onCancel = { TimerService.cancel(this) },
        onGoHome = {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        onEnableLock = { enableAdmin.launch(DeviceLock.enableIntent(this)) },
        onDisableLock = {
            DeviceLock.disable(this)
            setupVersion++
        },
        onEnableNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ||
                    !wasNotificationPermissionAsked()
                ) {
                    markNotificationPermissionAsked()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    )
                }
            }
        },
        onEnableExactAlarms = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri())
                )
            }
        },
        onOpenSecuritySettings = { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
        onRestoreSound = { MediaStopper.restoreVolume(this) },
    )

    private fun readSetup() = SetupStatus(
        canLock = DeviceLock.isEnabled(this),
        canNotify = Notifications.hasPermission(this),
        needsNotifyPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        exactAlarms = Alarms.canScheduleExact(this),
        deviceSecure = DeviceLock.isDeviceSecure(this),
    )

    // A reboot drops the alarm and the service, leaving an end time in the past.
    private fun clearStaleTimer() {
        val state = TimerStore.state.value ?: return
        if (state.endAt < System.currentTimeMillis() - 10_000L) TimerStore.setTimer(this, null)
    }

    private fun wasNotificationPermissionAsked() =
        getPreferences(MODE_PRIVATE).getBoolean(KEY_NOTIFY_ASKED, false)

    private fun markNotificationPermissionAsked() =
        getPreferences(MODE_PRIVATE).edit { putBoolean(KEY_NOTIFY_ASKED, true) }

    private companion object {
        const val KEY_NOTIFY_ASKED = "notify_asked"
    }
}
