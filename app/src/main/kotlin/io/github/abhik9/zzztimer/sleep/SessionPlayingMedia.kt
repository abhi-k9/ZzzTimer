package io.github.abhik9.zzztimer.sleep

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import io.github.abhik9.zzztimer.core.PlaybackPoint
import io.github.abhik9.zzztimer.core.PlayingMedia
import io.github.abhik9.zzztimer.diagnostics.DiagnosticsLog
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * [PlayingMedia] backed by the session of the app playing, found through the Notification access of [MediaAccessService].
 */
internal class SessionPlayingMedia private constructor(private val controller: MediaController) : PlayingMedia {

    companion object {
        /**
         * The session playing on this device, `null` without Notification access or when none is playing. Never throws:
         * rewinding must not get in the way of pausing.
         */
        fun find(context: Context, log: DiagnosticsLog): SessionPlayingMedia? {
            val sessions = try {
                if (!MediaAccessService.isGranted(context)) return null
                context.getSystemService(MediaSessionManager::class.java).getActiveSessions(MediaAccessService.component(context))
            } catch (e: RuntimeException) {
                // SecurityException when the access has just been revoked.
                log.warn("media: no access to the sessions", e)
                return null
            }
            // Sorted by priority: the first one playing is the one heard.
            val playing = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            log.record { "media: ${sessions.size} session(s), playing: ${playing?.packageName}" }
            return playing?.let(::SessionPlayingMedia)
        }
    }

    override fun current(): PlaybackPoint? {
        val state = controller.playbackState ?: return null
        // Players that can't seek say so, and so do live streams, which have no position.
        if ((state.actions and PlaybackState.ACTION_SEEK_TO) == 0L || state.position == PlaybackState.PLAYBACK_POSITION_UNKNOWN) return null
        // The position is only reported on changes: extrapolate it, at the playback speed, while playing.
        val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        val position = if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            state.position + (elapsed * state.playbackSpeed).toLong()
        } else {
            state.position
        }
        return PlaybackPoint(item = item(), position = position.coerceAtLeast(0).milliseconds)
    }

    /** An opaque key of what is playing: the diagnostics never contain what the user listens to. */
    private fun item(): String? {
        val metadata = controller.metadata ?: return null
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        return id?.hashCode()?.toUInt()?.toString(16)
    }

    override fun seekTo(position: Duration) = controller.transportControls.seekTo(position.inWholeMilliseconds)
}
