package com.tayragames.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.delay

/**
 * Stops whatever another app is playing. Taking permanent audio focus makes well-behaved
 * players (YouTube, Spotify, most video apps) pause; the media key reaches the active media
 * session as a second route. Muting is the last resort for players that ignore both.
 */
object MediaStopper {
    private var focusRequest: AudioFocusRequest? = null

    /** Returns true when the media volume had to be muted. */
    suspend fun stopPlayback(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = request
        audio.requestAudioFocus(request)

        sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_PAUSE)
        delay(1_000)
        if (!audio.isMusicActive) return false

        sendMediaKey(audio, KeyEvent.KEYCODE_MEDIA_STOP)
        delay(700)
        if (!audio.isMusicActive) return false

        mute(context, audio)
        return true
    }

    fun restoreVolume(context: Context) {
        TimerStore.load(context)
        val saved = TimerStore.savedVolume.value
        if (saved < 0) return
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0) }
        }
        TimerStore.setSavedVolume(context, -1)
    }

    private fun mute(context: Context, audio: AudioManager) {
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (TimerStore.savedVolume.value < 0 && current > 0) {
            TimerStore.setSavedVolume(context, current)
        }
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
    }

    private fun sendMediaKey(audio: AudioManager, keyCode: Int) {
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0))
    }
}
