package com.islandx.app.domain

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState

/** Lets the island UI control whatever media session the notification listener found. */
object MediaBridge {
    @Volatile
    var controller: MediaController? = null

    fun playPause() {
        val c = controller ?: return
        runCatching {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
                c.transportControls.pause()
            } else {
                c.transportControls.play()
            }
        }
    }

    fun next() {
        runCatching { controller?.transportControls?.skipToNext() }
    }

    fun previous() {
        runCatching { controller?.transportControls?.skipToPrevious() }
    }

    /** Seek to an absolute position. Some apps may not expose seek support. */
    fun seekTo(positionMs: Long) {
        runCatching { controller?.transportControls?.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    /** Jump forward/backward, clamped to the known track duration when available. */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val current = (c.playbackState?.position ?: 0L).coerceAtLeast(0L)
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L }
        val target = (current + deltaMs).coerceAtLeast(0L).let { value ->
            if (duration != null) value.coerceAtMost(duration) else value
        }
        seekTo(target)
    }
}
