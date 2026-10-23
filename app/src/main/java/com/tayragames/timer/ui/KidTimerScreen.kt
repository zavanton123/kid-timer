package com.tayragames.timer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tayragames.timer.R
import com.tayragames.timer.TimerState
import com.tayragames.timer.ui.theme.KidTimerTheme
import kotlinx.coroutines.launch
import android.text.format.DateFormat
import java.util.Date

data class SetupStatus(
    val canLock: Boolean,
    val canNotify: Boolean,
    val needsNotifyPermission: Boolean,
    val exactAlarms: Boolean,
    val deviceSecure: Boolean,
)

class KidTimerActions(
    val onStart: (minutes: Int) -> Unit,
    val onExtend: (minutes: Int) -> Unit,
    val onCancel: () -> Unit,
    val onGoHome: () -> Unit,
    val onEnableLock: () -> Unit,
    val onDisableLock: () -> Unit,
    val onEnableNotifications: () -> Unit,
    val onEnableExactAlarms: () -> Unit,
    val onOpenSecuritySettings: () -> Unit,
    val onRestoreSound: () -> Unit,
)

private val PRESET_MINUTES = listOf(1, 2, 5, 10, 15, 20, 30, 45, 60, 90)
private const val MAX_MINUTES = 600

@Composable
fun KidTimerScreen(
    timer: TimerState?,
    now: Long,
    setup: SetupStatus,
    soundMuted: Boolean,
    actions: KidTimerActions,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (soundMuted) MutedCard(actions.onRestoreSound)
            if (timer != null) {
                RunningContent(timer, now, actions)
            } else {
                PickerContent(setup.canLock, actions.onStart)
            }
            SetupCard(setup, actions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerContent(canLock: Boolean, onStart: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(15) }
    Text(
        text = stringResource(R.string.title_setup),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PRESET_MINUTES.forEach { preset ->
            FilterChip(
                selected = minutes == preset,
                onClick = { minutes = preset },
                label = { Text(stringResource(R.string.minutes_short, preset)) },
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        OutlinedButton(onClick = { minutes = (minutes - 1).coerceAtLeast(1) }) {
            Text(stringResource(R.string.decrease), style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = stringResource(R.string.minutes_short, minutes),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(180.dp),
        )
        OutlinedButton(onClick = { minutes = (minutes + 1).coerceAtMost(MAX_MINUTES) }) {
            Text(stringResource(R.string.increase), style = MaterialTheme.typography.titleLarge)
        }
    }
    if (!canLock) {
        Text(
            text = stringResource(R.string.warning_no_lock),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Button(
        onClick = { onStart(minutes) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(stringResource(R.string.start_timer), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RunningContent(timer: TimerState, now: Long, actions: KidTimerActions) {
    val remaining = (timer.endAt - now).coerceIn(0L, timer.totalMs)
    val fraction = if (timer.totalMs > 0) remaining.toFloat() / timer.totalMs else 0f
    val lockTime = DateFormat.getTimeFormat(LocalContext.current).format(Date(timer.endAt))

    Text(
        text = stringResource(R.string.title_running),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.size(240.dp),
            strokeWidth = 14.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatRemaining(remaining),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(stringResource(R.string.time_left), style = MaterialTheme.typography.titleMedium)
        }
    }
    Text(
        text = stringResource(R.string.locks_at, lockTime),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(1, 5).forEach { extra ->
            OutlinedButton(onClick = { actions.onExtend(extra) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.add_minutes, extra))
            }
        }
    }
    FilledTonalButton(
        onClick = actions.onGoHome,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(stringResource(R.string.go_home))
    }
    Text(
        text = stringResource(R.string.running_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HoldToConfirmButton(text = stringResource(R.string.hold_to_cancel), onConfirm = actions.onCancel)
}

/** A button that only fires after being held, so a child tapping around cannot cancel the timer. */
@Composable
private fun HoldToConfirmButton(text: String, onConfirm: () -> Unit, holdMillis: Int = 1_500) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val confirm by rememberUpdatedState(onConfirm)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                        confirm()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    if (job.isActive) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f) }
                    }
                })
            },
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
            )
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun SetupCard(setup: SetupStatus, actions: KidTimerActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge)
            RequirementRow(
                title = stringResource(R.string.req_lock_title),
                description = stringResource(R.string.req_lock_desc),
                done = setup.canLock,
                actionLabel = stringResource(R.string.action_allow),
                onAction = actions.onEnableLock,
            )
            if (setup.needsNotifyPermission) {
                RequirementRow(
                    title = stringResource(R.string.req_notify_title),
                    description = stringResource(R.string.req_notify_desc),
                    done = setup.canNotify,
                    actionLabel = stringResource(R.string.action_allow),
                    onAction = actions.onEnableNotifications,
                )
            }
            if (!setup.exactAlarms) {
                RequirementRow(
                    title = stringResource(R.string.req_exact_title),
                    description = stringResource(R.string.req_exact_desc),
                    done = false,
                    actionLabel = stringResource(R.string.action_allow),
                    onAction = actions.onEnableExactAlarms,
                )
            }
            RequirementRow(
                title = stringResource(R.string.req_pin_title),
                description = stringResource(R.string.req_pin_desc),
                done = setup.deviceSecure,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = actions.onOpenSecuritySettings,
            )
            if (setup.canLock) {
                TextButton(onClick = actions.onDisableLock) {
                    Text(stringResource(R.string.remove_admin))
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(
    title: String,
    description: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (done) {
            Text(
                stringResource(R.string.status_done),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun MutedCard(onRestore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.muted_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.muted_desc), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onRestore) { Text(stringResource(R.string.restore_sound)) }
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private val previewActions = KidTimerActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
private val previewSetup = SetupStatus(
    canLock = false,
    canNotify = true,
    needsNotifyPermission = true,
    exactAlarms = true,
    deviceSecure = false,
)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PickerPreview() {
    KidTimerTheme { KidTimerScreen(null, 0L, previewSetup, soundMuted = false, actions = previewActions) }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RunningPreview() {
    KidTimerTheme {
        KidTimerScreen(
            timer = TimerState(endAt = 754_000L, totalMs = 900_000L),
            now = 0L,
            setup = previewSetup.copy(canLock = true, deviceSecure = true),
            soundMuted = true,
            actions = previewActions,
        )
    }
}
