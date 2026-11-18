package com.islandx.app.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
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
import com.islandx.app.data.IslandSettings
import com.islandx.app.domain.PriorityEngine
import com.islandx.app.ui.overlay.IslandOverlay
import com.islandx.app.ui.theme.IslandXTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle / SavedState / ViewModelStore owner so a ComposeView can live inside a Service window.
 *
 * FIX: the old private copies called `registry.currentState = DESTROYED` even when the owner was
 * never started (e.g. the service called stopSelf() because the overlay permission was missing).
 * Moving a LifecycleRegistry from INITIALIZED straight to DESTROYED throws IllegalStateException,
 * so the service crashed in onDestroy(). start()/stop() are now idempotent and stop() is a no-op
 * when start() never ran. It also removes the duplicated copy that lived in each service.
 */
internal class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private var started = false

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun start() {
        if (started) return
        started = true
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        if (!started) return
        started = false
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

/**
 * Owns exactly one island window: the ComposeView, its WindowManager.LayoutParams, positioning,
 * glass blur and landscape visibility. Both the TYPE_APPLICATION_OVERLAY service and the
 * TYPE_ACCESSIBILITY_OVERLAY service use it, so the logic exists once.
 *
 * Threading: call everything from the main thread.
 */
internal class OverlayWindow(
    private val context: Context,
    private val windowType: Int,
    private val windowTitle: String,
    private val settings: StateFlow<IslandSettings>
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val owner = OverlayLifecycleOwner()
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    // Last values pushed to the window manager. updateViewLayout() is a binder call, so we skip
    // it when nothing that affects the window itself (position / blur / visibility) changed.
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE
    private var lastBlur = -1
    private var lastVisibility = -1

    val isShowing: Boolean get() = view != null

    /** Adds the window if it is not already on screen. Safe to call repeatedly. */
    fun show() {
        if (view != null) return
        owner.start()

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            // NOT_FOCUSABLE + NOT_TOUCH_MODAL: touches outside the island reach the app below.
            // WRAP_CONTENT: the window is exactly as big as the island, so nothing else is blocked.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            title = windowTitle
            // Without this the window is pushed below the camera cutout instead of covering it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val cv = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val s by settings.collectAsState()
                val events by PriorityEngine.events.collectAsState()
                IslandXTheme { IslandOverlay(settings = s, events = events) }
            }
        }

        params = lp
        view = cv
        lastX = Int.MIN_VALUE; lastY = Int.MIN_VALUE; lastBlur = -1; lastVisibility = -1
        // Fill position / blur into the params BEFORE addView so the island never flashes at 0,0.
        applyToParams(settings.value, cv, lp)
        try {
            wm.addView(cv, lp)
        } catch (e: Exception) {
            // Permission revoked, display not ready, service tearing down...
            view = null
            params = null
        }
    }

    /** Pushes new settings to the live window. No-op when the window is not shown. */
    fun update(s: IslandSettings) {
        val cv = view ?: return
        val lp = params ?: return
        val changed = applyToParams(s, cv, lp)
        if (changed && cv.isAttachedToWindow) {
            try {
                wm.updateViewLayout(cv, lp)
            } catch (_: IllegalArgumentException) {
                // View already removed (service is disconnecting).
            }
        }
    }

    /** Removes the window but keeps the owner alive so show() can be called again. */
    fun hide() {
        val cv = view ?: return
        try {
            cv.disposeComposition()
            if (cv.isAttachedToWindow) wm.removeView(cv)
        } catch (_: Exception) {
        }
        view = null
        params = null
    }

    /** Final teardown. The window object cannot be reused afterwards. */
    fun release() {
        hide()
        owner.stop()
    }

    /** @return true when anything that needs updateViewLayout() changed. */
    private fun applyToParams(s: IslandSettings, cv: View, lp: WindowManager.LayoutParams): Boolean {
        val res = context.resources
        val d = res.displayMetrics.density
        val screenWidthDp = res.configuration.screenWidthDp.coerceAtLeast(1)
        val screenHeightDp = res.configuration.screenHeightDp.coerceAtLeast(1)

        // The expanded card can be wider than the pill, so clamp X with the expanded width to keep
        // it on screen. (Known limitation: cameras far from the centre cannot be reached, see report.)
        val expandedWidthDp = minOf((screenWidthDp - 24).coerceAtLeast(1), 380)
        val maxOffsetXDp = ((screenWidthDp - expandedWidthDp) / 2).coerceAtLeast(0)
        val x = (s.offsetX.coerceIn(-maxOffsetXDp, maxOffsetXDp) * d).toInt()
        val y = (s.offsetY.coerceIn(0, (screenHeightDp - 80).coerceAtLeast(0)) * d).toInt()

        // Blur-behind only for the Glass style and only if the device allows cross-window blur
        // (it is off in battery-saver mode, on low-end GPUs and for some OEM builds).
        var blur = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            s.backgroundStyle == 0 && wm.isCrossWindowBlurEnabled
        ) blur = 32

        val landscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val visibility = if (landscape && s.hideInLandscape) View.GONE else View.VISIBLE

        var changed = false
        if (x != lastX || y != lastY) {
            lp.x = x; lp.y = y
            lastX = x; lastY = y
            changed = true
        }
        if (blur != lastBlur) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                lp.blurBehindRadius = blur
                lp.flags = if (blur > 0) lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                else lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            }
            lastBlur = blur
            changed = true
        }
        if (visibility != lastVisibility) {
            cv.visibility = visibility
            lastVisibility = visibility
            changed = true
        }
        return changed
    }
}
