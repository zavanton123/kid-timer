package com.tayragames.timer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TimerState(val endAt: Long, val totalMs: Long)

object TimerStore {
    private const val PREFS = "kid_timer"
    private const val KEY_END_AT = "end_at"
    private const val KEY_TOTAL_MS = "total_ms"
    private const val KEY_SAVED_VOLUME = "saved_volume"

    private val _state = MutableStateFlow<TimerState?>(null)
    val state: StateFlow<TimerState?> = _state.asStateFlow()

    // Media volume from before KidTimer muted it, or -1 when nothing was muted.
    private val _savedVolume = MutableStateFlow(-1)
    val savedVolume: StateFlow<Int> = _savedVolume.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = prefs(context)
            val endAt = prefs.getLong(KEY_END_AT, 0L)
            _state.value = if (endAt > 0L) TimerState(endAt, prefs.getLong(KEY_TOTAL_MS, 0L)) else null
            _savedVolume.value = prefs.getInt(KEY_SAVED_VOLUME, -1)
            loaded = true
        }
    }

    fun setTimer(context: Context, state: TimerState?) {
        load(context)
        prefs(context).edit {
            if (state == null) {
                remove(KEY_END_AT)
                remove(KEY_TOTAL_MS)
            } else {
                putLong(KEY_END_AT, state.endAt)
                putLong(KEY_TOTAL_MS, state.totalMs)
            }
        }
        _state.value = state
    }

    fun setSavedVolume(context: Context, volume: Int) {
        load(context)
        prefs(context).edit { putInt(KEY_SAVED_VOLUME, volume) }
        _savedVolume.value = volume
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
