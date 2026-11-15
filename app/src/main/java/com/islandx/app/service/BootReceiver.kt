package com.islandx.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.islandx.app.IslandXApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (!Settings.canDrawOverlays(context)) return

        val pending = goAsync() // keeps the receiver alive while we read DataStore
        val app = context.applicationContext as IslandXApp
        app.appScope.launch {
            try {
                if (app.settings.flow.first().enabled) {
                    ContextCompat.startForegroundService(
                        context, Intent(context, IslandOverlayService::class.java)
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
