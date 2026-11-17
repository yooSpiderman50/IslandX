package com.islandx.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.islandx.app.IslandXApp
import com.islandx.app.data.IslandSettings
import com.islandx.app.domain.PriorityEngine
import com.islandx.app.ui.overlay.IslandOverlay
import com.islandx.app.ui.theme.IslandXTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private class AccessibilityOverlayOwner :
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
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

/**
 * Hosts the existing Compose island as a real accessibility overlay.
 * The user must explicitly enable this service in Android Settings.
 */
class IslandAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private val owner = AccessibilityOverlayOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var islandView: ComposeView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var ownerStarted = false

    private val repo get() = (application as IslandXApp).settings

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        owner.start()
        ownerStarted = true
        scope.launch {
            repo.state.collect { settings -> updateIsland(settings) }
        }
        sendOverlayModeChanged()
    }

    private fun updateIsland(settings: IslandSettings) {
        if (!settings.enabled) {
            removeIsland()
            return
        }
        if (islandView == null) addIsland()
        val view = islandView ?: return
        updatePosition(settings)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        view.visibility = if (landscape && settings.hideInLandscape) android.view.View.GONE
            else android.view.View.VISIBLE
        val params = windowParams ?: return
        if (view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: IllegalArgumentException) {
                // The service may be disconnecting while settings update.
            }
        }
    }

    private fun addIsland() {
        if (islandView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            title = "IslandX Accessibility Overlay"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        windowParams = params
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
        islandView = view
        updatePosition(repo.state.value)
        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            islandView = null
            windowParams = null
            return
        }
    }

    private fun updatePosition(settings: IslandSettings) {
        val params = windowParams ?: return
        val density = resources.displayMetrics.density
        val screenWidthDp = resources.configuration.screenWidthDp.coerceAtLeast(1)
        val expandedWidthDp = minOf((screenWidthDp - 24).coerceAtLeast(1), 380)
        val maxOffsetXDp = ((screenWidthDp - expandedWidthDp) / 2).coerceAtLeast(0)
        val safeX = settings.offsetX.coerceIn(-maxOffsetXDp, maxOffsetXDp)
        val screenHeightDp = resources.configuration.screenHeightDp.coerceAtLeast(1)
        val safeY = settings.offsetY.coerceIn(0, (screenHeightDp - 80).coerceAtLeast(0))
        params.x = (safeX * density).toInt()
        params.y = (safeY * density).toInt()
    }

    private fun removeIsland() {
        val view = islandView ?: return
        try {
            if (view.isAttachedToWindow) windowManager.removeView(view)
        } catch (_: Exception) {
        }
        islandView = null
        windowParams = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (islandView == null && repo.state.value.enabled) addIsland()
        updateIsland(repo.state.value)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeIsland()
        scope.cancel()
        if (ownerStarted) owner.stop()
        sendOverlayModeChanged()
        super.onDestroy()
    }

    private fun sendOverlayModeChanged() {
        sendBroadcast(Intent(ACTION_OVERLAY_MODE_CHANGED).setPackage(packageName))
    }

    companion object {
        const val ACTION_OVERLAY_MODE_CHANGED =
            "com.islandx.app.action.ACCESSIBILITY_OVERLAY_MODE_CHANGED"

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, IslandAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(':').any { entry ->
                val component = ComponentName.unflattenFromString(entry)
                component != null && component == expected
            }
        }
    }
}
