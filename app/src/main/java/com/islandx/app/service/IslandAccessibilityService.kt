package com.islandx.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.islandx.app.IslandXApp
import com.islandx.app.data.IslandSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the Compose island as a real TYPE_ACCESSIBILITY_OVERLAY window.
 * The user must explicitly enable this service in Android Settings.
 *
 * Fixes compared with 1.2.8:
 *  - onServiceConnected() can only start the lifecycle owner / collector once (the old code
 *    would throw "SavedStateRegistry was already restored" on a second connect);
 *  - exposes [isActive] (really connected) so the overlay service can decide reliably;
 *  - unbind/destroy clear [isActive] BEFORE broadcasting, so the fallback overlay takes over
 *    instead of seeing a stale "enabled" entry in Settings.Secure and staying hidden;
 *  - duplicate owner/blur/position code now lives in OverlayWindow.
 */
class IslandAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repo get() = (application as IslandXApp).settings
    private var window: OverlayWindow? = null
    private var collectJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (window == null) {
            window = OverlayWindow(
                context = this,
                windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                windowTitle = "IslandX Accessibility Overlay",
                settings = repo.state
            )
        }
        isActive = true
        collectJob?.cancel()
        // repo.flow gives the real stored values first (repo.state starts with defaults).
        collectJob = scope.launch { repo.flow.collect { updateIsland(it) } }
        sendOverlayModeChanged()
    }

    private fun updateIsland(s: IslandSettings) {
        val w = window ?: return
        if (!s.enabled) {
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown(cancelScope = false) // the same instance may be re-bound, keep the scope usable
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown(cancelScope = true)
        super.onDestroy()
    }

    private fun teardown(cancelScope: Boolean) {
        val wasActive = isActive
        isActive = false
        collectJob?.cancel()
        collectJob = null
        window?.release()
        window = null
        if (cancelScope) scope.cancel()
        if (wasActive) sendOverlayModeChanged()
    }

    private fun sendOverlayModeChanged() {
        sendBroadcast(Intent(ACTION_OVERLAY_MODE_CHANGED).setPackage(packageName))
    }

    companion object {
        const val ACTION_OVERLAY_MODE_CHANGED =
            "com.islandx.app.action.ACCESSIBILITY_OVERLAY_MODE_CHANGED"

        /** True only while the service is actually connected and owns the island window. */
        @Volatile
        var isActive: Boolean = false
            private set

        /** True when the user enabled the service in Settings (used only for the permission row). */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, IslandAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(':').any { entry ->
                ComponentName.unflattenFromString(entry) == expected
            }
        }
    }
}
