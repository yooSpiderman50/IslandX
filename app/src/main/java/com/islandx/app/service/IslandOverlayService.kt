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
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.islandx.app.IslandXApp
import com.islandx.app.R
import com.islandx.app.data.IslandSettings
import com.islandx.app.domain.IslandEvent
import com.islandx.app.domain.PriorityEngine
import com.islandx.app.ui.MainActivity
import com.islandx.app.ui.overlay.IslandOverlay
import com.islandx.app.ui.theme.IslandXTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Lifecycle/SavedState/ViewModelStore owner so a ComposeView can live inside a Service window. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun start() {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

class IslandOverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var composeView: ComposeView? = null
    private val owner = OverlayOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo get() = (application as IslandXApp).settings
    private var receiverRegistered = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!repo.state.value.showBattery) return
            val bm = this@IslandOverlayService.getSystemService(BatteryManager::class.java)
            val rawPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val pct = rawPct.coerceIn(0, 100)
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
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        owner.start()
        addOverlay()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
        }
        ContextCompat.registerReceiver(this, powerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true

        scope.launch { repo.state.collect { applySettings(it) } }
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

    private fun addOverlay() {
        // FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCH_MODAL: touches outside the island pass through to the app below.
        // WRAP_CONTENT: the window is exactly as big as the island, so nothing else is blocked.
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            title = "IslandX"
            // Without this the window is pushed below the camera cutout instead of covering it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val settings by repo.state.collectAsState()
                val events by PriorityEngine.events.collectAsState()
                IslandXTheme {
                    IslandOverlay(settings = settings, events = events)
                }
            }
        }
        composeView = view
        applyPosition(repo.state.value)
        wm.addView(view, params)
    }

    private fun applyPosition(s: IslandSettings) {
        val d = resources.displayMetrics.density
        // Keep the expanded island inside the visible display area. The expanded card can
        // be wider than the compact pill, so clamp offsets using its maximum width.
        val screenWidthDp = resources.configuration.screenWidthDp.coerceAtLeast(1)
        val expandedWidthDp = minOf((screenWidthDp - 24).coerceAtLeast(1), 380)
        val maxOffsetXDp = ((screenWidthDp - expandedWidthDp) / 2).coerceAtLeast(0)
        val safeX = s.offsetX.coerceIn(-maxOffsetXDp, maxOffsetXDp)
        val screenHeightDp = resources.configuration.screenHeightDp.coerceAtLeast(1)
        // The overlay window is already allowed into the display cutout above.
        // Do not add an extra status-bar clearance here: it pushes the island down
        // from the top edge and can make it sit below the camera cutout. offsetY is
        // the user's complete vertical adjustment, in dp.
        val safeY = s.offsetY.coerceIn(0, (screenHeightDp - 80).coerceAtLeast(0))
        params.x = (safeX * d).toInt()
        params.y = (safeY * d).toInt()
    }

    private fun applySettings(s: IslandSettings) {
        if (!s.enabled) {
            stopSelf()
            return
        }
        if (!s.showBattery) PriorityEngine.remove("battery")
        applyPosition(s)
        val v = composeView ?: return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        v.visibility = if (landscape && s.hideInLandscape) View.GONE else View.VISIBLE
        if (v.isAttachedToWindow) wm.updateViewLayout(v, params)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (composeView != null) applySettings(repo.state.value)
    }

    override fun onDestroy() {
        scope.cancel()
        if (receiverRegistered) {
            try { unregisterReceiver(powerReceiver) } catch (_: Exception) {}
        }
        if (::wm.isInitialized) {
            composeView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        }
        composeView = null
        owner.stop()
        super.onDestroy()
    }
}
