package io.github.abhik9.zzztimer.sleep

import android.media.AudioManager
import android.media.AudioManager.ADJUST_LOWER
import android.media.AudioManager.STREAM_MUSIC
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.util.Log
import android.view.KeyEvent
import io.github.abhik9.zzztimer.core.EventLog
import io.github.abhik9.zzztimer.core.MediaAudio

/**
 * [MediaAudio] backed by the media stream of the [AudioManager].
 * Volume changes can be refused by the system (e.g. Do Not Disturb policies): they are logged and ignored.
 */
internal class AndroidMediaAudio(private val audio: AudioManager, private val log: EventLog) : MediaAudio {

    override val volume: Int get() = audio.getStreamVolume(STREAM_MUSIC)
    override val minVolume: Int get() = if (SDK_INT >= P) audio.getStreamMinVolume(STREAM_MUSIC) else 0
    override val isPlaying: Boolean get() = audio.isMusicActive
    override val isVolumeFixed: Boolean get() = audio.isVolumeFixed

    override fun lowerVolume() = guarded { audio.adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER, 0) }

    override fun restoreVolume(volume: Int) = guarded { audio.setStreamVolume(STREAM_MUSIC, volume, 0) }

    override fun pause() = guarded {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            Log.w(TAG, "Audio operation refused", e)
            log.record { "audio: operation refused: $e" }
        }
    }

    private companion object {
        const val TAG = "AndroidMediaAudio"
    }
}
