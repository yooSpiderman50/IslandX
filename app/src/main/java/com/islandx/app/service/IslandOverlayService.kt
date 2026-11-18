package com.islandx.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.islandx.app.IslandXApp
import com.islandx.app.R
import com.islandx.app.data.IslandSettings
import com.islandx.app.domain.IslandEvent
import com.islandx.app.domain.PriorityEngine
import com.islandx.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the regular "Display over other apps" island and listens for
 * power events. When the accessibility overlay service is connected it hides its own window so
 * only one island is ever visible.
 *
 * Fixes compared with 1.2.8:
 *  - no more UninitializedPropertyAccessException: the old code touched `params` (lateinit) from
 *    applyGlassBlur() even when the accessibility service was active and addOverlay() never ran;
 *  - no more IllegalStateException in onDestroy() when the service stopped itself because the
 *    overlay permission was missing (lifecycle owner was never started);
 *  - the first settings are read from DataStore before the window is created, so the island does
 *    not flash at the default position / style;
 *  - "accessibility is active" now means the service is really CONNECTED, not just listed in
 *    Settings.Secure (which made the island vanish if the service was enabled but not running).
 */
class IslandOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repo get() = (application as IslandXApp).settings
    private var window: OverlayWindow? = null
    private var powerReceiverRegistered = false
    private var modeReceiverRegistered = false

    private val accessibilityModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            applySettings(repo.state.value)
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!repo.state.value.showBattery) return
            val bm = getSystemService(BatteryManager::class.java) ?: return
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED ->
                    PriorityEngine.post(IslandEvent.Battery(pct, true), 5000L)
                Intent.ACTION_POWER_DISCONNECTED ->
                    PriorityEngine.remove("battery")
                Intent.ACTION_BATTERY_LOW ->
                    PriorityEngine.post(IslandEvent.Battery(pct, false), 6000L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        // Must be called within a few seconds of startForegroundService(), so do it first.
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        window = OverlayWindow(
            context = this,
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowTitle = "IslandX",
            settings = repo.state
        )

        ContextCompat.registerReceiver(
            this, accessibilityModeReceiver,
            IntentFilter(IslandAccessibilityService.ACTION_OVERLAY_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        modeReceiverRegistered = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
        }
        ContextCompat.registerReceiver(this, powerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        powerReceiverRegistered = true

        // repo.flow emits the REAL stored values first (repo.state starts with defaults), so the
        // window is created once, at the right place and with the right style.
        scope.launch { repo.flow.collect { applySettings(it) } }
    }

    private fun startAsForeground() {
        val channelId = "island_service"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "IslandX running", NotificationManager.IMPORTANCE_MIN)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_island)
            .setContentTitle("IslandX is running")
            .setContentText("Tap to open settings")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, n)
        }
    }

    private fun applySettings(s: IslandSettings) {
        val w = window ?: return
        if (!s.enabled) {
            w.hide()
            stopSelf()
            return
        }
        if (!s.showBattery) PriorityEngine.remove("battery")
        // Never show two copies of the island. A connected accessibility service owns the window.
        if (IslandAccessibilityService.isActive) {
            w.hide()
            return
        }
        w.show()
        w.update(s)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (window?.isShowing == true) window?.update(repo.state.value)
    }

    override fun onDestroy() {
        scope.cancel()
        if (powerReceiverRegistered) {
            try { unregisterReceiver(powerReceiver) } catch (_: Exception) {}
        }
        if (modeReceiverRegistered) {
            try { unregisterReceiver(accessibilityModeReceiver) } catch (_: Exception) {}
        }
        window?.release()
        window = null
        super.onDestroy()
    }
}
