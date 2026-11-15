package com.islandx.app.service

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import com.islandx.app.IslandXApp
import com.islandx.app.domain.EventAction
import com.islandx.app.domain.IslandEvent
import com.islandx.app.domain.MediaBridge
import com.islandx.app.domain.PriorityEngine

/**
 * Needs "Notification access". It feeds the island with:
 *  - normal notifications (+ progress / navigation ongoing ones)
 *  - calls (CATEGORY_CALL notifications, with their Answer/Decline buttons)
 *  - the active media session (title, art, play/pause...)
 */
class IslandNotificationListener : NotificationListenerService() {

    private val settings get() = (application as IslandXApp).settings.state.value
    private var controller: MediaController? = null
    private lateinit var msm: MediaSessionManager
    private lateinit var component: ComponentName
    private var sessionListenerAdded = false
    private val iconCache = HashMap<String, Bitmap?>()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { attach(it) }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onSessionDestroyed() {
            controller = null
            MediaBridge.controller = null
            PriorityEngine.remove("media")
        }
    }

    override fun onListenerConnected() {
        msm = getSystemService(MediaSessionManager::class.java)
        component = ComponentName(this, IslandNotificationListener::class.java)
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component)
            sessionListenerAdded = true
            attach(msm.getActiveSessions(component))
        } catch (_: SecurityException) {
        }
        try {
            activeNotifications?.forEach { handle(it, initial = true) }
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        if (sessionListenerAdded) {
            try { msm.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
            sessionListenerAdded = false
        }
        controller?.unregisterCallback(mediaCallback)
        controller = null
        MediaBridge.controller = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        handle(sbn, initial = false)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        PriorityEngine.remove(keyOf(sbn))
        if (sbn.notification.category == Notification.CATEGORY_CALL) PriorityEngine.remove("call")
    }

    private fun keyOf(sbn: StatusBarNotification) = "${sbn.packageName}:${sbn.id}:${sbn.tag}"

    // ---------------------------------------------------------------- notifications

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        val s = settings
        val n = sbn.notification
        val pkg = sbn.packageName
        if (pkg == packageName || pkg in s.blockedApps) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val ex = n.extras
        if (ex.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("MediaStyle")) return

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0

        // ---- calls
        if (n.category == Notification.CATEGORY_CALL) {
            if (!s.showCalls) return
            val type = ex.getInt("android.callType", 0) // 1 = incoming, 2 = ongoing (Android 12+)
            val incoming = type == 1 || (type == 0 && n.fullScreenIntent != null)
            val actions = (n.actions ?: emptyArray()).map {
                EventAction(it.title?.toString().orEmpty(), it.actionIntent)
            }.take(3)
            PriorityEngine.post(
                IslandEvent.Call(
                    caller = title.ifBlank { "Call" },
                    incoming = incoming,
                    actions = actions,
                    open = n.contentIntent,
                    icon = iconFor(n, pkg)
                )
            )
            return
        }

        if (!s.showNotifications) return

        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val progress =
            if (max > 0 && !indeterminate) ex.getInt(Notification.EXTRA_PROGRESS, 0) * 100 / max else -1
        val persistent = ongoing && (progress >= 0 || n.category == "navigation")
        if (ongoing && !persistent) return // ignore sticky system/app notifications
        if (initial && !persistent) return // don't replay old notifications on connect

        if (!persistent) {
            // skip silent / low-importance notifications
            try {
                val r = Ranking()
                if (currentRanking.getRanking(sbn.key, r) &&
                    r.importance < NotificationManager.IMPORTANCE_DEFAULT
                ) return
            } catch (_: Exception) {
            }
        }

        PriorityEngine.post(
            IslandEvent.Notif(
                key = keyOf(sbn),
                appName = labelFor(pkg),
                title = title,
                text = text,
                icon = iconFor(n, pkg),
                progress = progress,
                open = n.contentIntent,
                prio = if (persistent) 40 else 70
            ),
            ttlMs = if (persistent) 0L else 6000L
        )
    }

    private fun iconFor(n: Notification, pkg: String): Bitmap? {
        val large = n.getLargeIcon()
        if (large == null) {
            iconCache[pkg]?.let { return it }
        }
        var bmp: Bitmap? = null
        try {
            bmp = large?.loadDrawable(this)?.toBitmap(96, 96)
        } catch (_: Exception) {
        }
        if (bmp == null) {
            try {
                bmp = packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
            } catch (_: Exception) {
            }
        }
        if (bmp == null) {
            try {
                bmp = n.smallIcon?.loadDrawable(this)?.toBitmap(96, 96)
            } catch (_: Exception) {
            }
        }
        if (large == null) iconCache[pkg] = bmp
        return bmp
    }

    private fun labelFor(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    // ---------------------------------------------------------------- media

    private fun attach(list: List<MediaController>?) {
        val pick = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (pick?.sessionToken == controller?.sessionToken) {
            publishMedia()
            return
        }
        controller?.unregisterCallback(mediaCallback)
        controller = pick
        MediaBridge.controller = pick
        pick?.registerCallback(mediaCallback)
        publishMedia()
    }

    private fun publishMedia() {
        val c = controller
        val st = c?.playbackState
        val md = c?.metadata
        if (c == null || st == null || md == null ||
            !settings.showMedia || c.packageName in settings.blockedApps
        ) {
            PriorityEngine.remove("media")
            return
        }
        val playing = st.state == PlaybackState.STATE_PLAYING
        val paused = st.state == PlaybackState.STATE_PAUSED
        if (!playing && !paused) {
            PriorityEngine.remove("media")
            return
        }
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        PriorityEngine.post(
            IslandEvent.Media(
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Unknown",
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
                art = art?.limit(256),
                playing = playing,
                positionMs = st.position,
                updatedAt = if (st.lastPositionUpdateTime > 0) st.lastPositionUpdateTime
                else SystemClock.elapsedRealtime(),
                speed = st.playbackSpeed,
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
            ),
            ttlMs = if (playing) 0L else 30_000L // paused media disappears after 30s
        )
    }

    private fun Bitmap.limit(max: Int): Bitmap {
        val m = maxOf(width, height)
        if (m <= max) return this
        val f = max.toFloat() / m
        return Bitmap.createScaledBitmap(
            this, (width * f).toInt().coerceAtLeast(1), (height * f).toInt().coerceAtLeast(1), true
        )
    }
}
