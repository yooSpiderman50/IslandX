package com.islandx.app.ui

import android.os.Bundle
import android.provider.Settings
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.islandx.app.IslandXApp
import com.islandx.app.service.IslandOverlayService
import com.islandx.app.ui.theme.IslandXTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IslandXTheme {
                HomeScreen(activity = this, tick = resumeTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // (Re)start the overlay whenever the app is opened and the user wants it on.
        lifecycleScope.launch {
            val enabled = (application as IslandXApp).settings.flow.first().enabled
            if (enabled && Settings.canDrawOverlays(this@MainActivity)) {
                ContextCompat.startForegroundService(
                    this@MainActivity, Intent(this@MainActivity, IslandOverlayService::class.java)
                )
            }
        }
    }
}
