package com.islandx.app.domain

import android.media.session.MediaController
import android.media.session.PlaybackState

/** Lets the island UI control whatever media session the notification listener found. */
object MediaBridge {
    @Volatile
    var controller: MediaController? = null

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }
}
