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
 *
 * Never throws: the session belongs to another app, which may misbehave or die at any time, and rewinding must not get
 * in the way of pausing.
 */
internal class SessionPlayingMedia private constructor(
    private val controller: MediaController,
    private val log: DiagnosticsLog,
) : PlayingMedia {

    companion object {
        /** The session playing on this device, `null` without Notification access or when none is playing. */
        fun find(context: Context, log: DiagnosticsLog): SessionPlayingMedia? = guarded(log, "finding the session") {
            if (!MediaAccessService.isGranted(context)) return null
            // Throws SecurityException when the access has just been revoked.
            val sessions =
                context.getSystemService(MediaSessionManager::class.java).getActiveSessions(MediaAccessService.component(context))
            // Sorted by priority: the first one playing is the one heard.
            val playing = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            log.record { "media: ${sessions.size} session(s), playing: ${playing?.packageName}" }
            playing?.let { SessionPlayingMedia(it, log) }
        }
    }

    override fun current(): PlaybackPoint? = guarded(log, "reading the position") {
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
        PlaybackPoint(item = item(), position = position.coerceAtLeast(0).milliseconds)
    }

    /** An opaque key of what is playing: the diagnostics never contain what the user listens to. */
    private fun item(): String? {
        val metadata = controller.metadata ?: return null
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        return id?.hashCode()?.toUInt()?.toString(16)
    }

    override fun seekTo(position: Duration) {
        guarded(log, "seeking") { controller.transportControls.seekTo(position.inWholeMilliseconds) }
    }
}

/** Runs [block], or logs its failure and returns `null`. */
private inline fun <T> guarded(log: DiagnosticsLog, operation: String, block: () -> T): T? = try {
    block()
} catch (e: RuntimeException) {
    log.warn("media: $operation failed", e)
    null
}
