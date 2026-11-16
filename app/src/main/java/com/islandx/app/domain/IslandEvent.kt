package com.islandx.app.domain

import android.app.PendingIntent
import android.graphics.Bitmap

data class EventAction(val title: String, val intent: PendingIntent?)

/**
 * Priority: Call 100 > Battery 80 > fresh notification 70 > Timer 65 > Media 60 > ongoing notification 40.
 * `stamp` is not part of equals(), it is only used to order events of the same priority (newest first).
 */
sealed class IslandEvent(val id: String, val priority: Int) {
    val stamp: Long = System.currentTimeMillis()

    data class Call(
        val caller: String,
        val incoming: Boolean,
        val actions: List<EventAction>,
        val open: PendingIntent?,
        val icon: Bitmap?,
        val packageName: String = ""
    ) : IslandEvent("call", 100)

    data class Battery(val percent: Int, val charging: Boolean) : IslandEvent("battery", 80)

    data class Notif(
        val key: String,
        val appName: String,
        val title: String,
        val text: String,
        val icon: Bitmap?,
        val progress: Int, // -1 = no progress, otherwise 0..100
        val open: PendingIntent?,
        val prio: Int
    ) : IslandEvent(key, prio)

    data class Timer(
        val seconds: Long,
        val running: Boolean,
        val countdown: Boolean,
        val finished: Boolean
    ) : IslandEvent("timer", 65)

    data class Media(
        val title: String,
        val artist: String,
        val art: Bitmap?,
        val playing: Boolean,
        val positionMs: Long,
        val updatedAt: Long, // SystemClock.elapsedRealtime() based
        val speed: Float,
        val durationMs: Long
    ) : IslandEvent("media", 60)
}

fun IslandEvent.openIntent(): PendingIntent? = when (this) {
    is IslandEvent.Call -> open
    is IslandEvent.Notif -> open
    else -> null
}
