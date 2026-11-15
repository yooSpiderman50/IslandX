package com.islandx.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore by preferencesDataStore(name = "island_settings")

data class IslandSettings(
    val enabled: Boolean = true,
    val widthDp: Int = 120,
    val heightDp: Int = 34,
    val offsetX: Int = 0,
    val offsetY: Int = 6,
    val cornerDp: Int = 20,
    val haptics: Boolean = true,
    val glow: Boolean = true,
    val showNotifications: Boolean = true,
    val showMedia: Boolean = true,
    val showCalls: Boolean = true,
    val showBattery: Boolean = true,
    val hideInLandscape: Boolean = true,
    val accentIndex: Int = 0,
    val blockedApps: Set<String> = emptySet()
)

object K {
    val ENABLED = booleanPreferencesKey("enabled")
    val WIDTH = intPreferencesKey("width")
    val HEIGHT = intPreferencesKey("height")
    val OFFSET_X = intPreferencesKey("offset_x")
    val OFFSET_Y = intPreferencesKey("offset_y")
    val CORNER = intPreferencesKey("corner")
    val HAPTICS = booleanPreferencesKey("haptics")
    val GLOW = booleanPreferencesKey("glow")
    val NOTIFS = booleanPreferencesKey("notifs")
    val MEDIA = booleanPreferencesKey("media")
    val CALLS = booleanPreferencesKey("calls")
    val BATTERY = booleanPreferencesKey("battery")
    val HIDE_LANDSCAPE = booleanPreferencesKey("hide_landscape")
    val ACCENT = intPreferencesKey("accent")
    val BLOCKED = stringSetPreferencesKey("blocked_apps")
}

class SettingsRepository(private val context: Context, scope: CoroutineScope) {

    val flow: Flow<IslandSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it.toSettings() }

    /** Always-available latest value, readable from services without suspending. */
    val state: StateFlow<IslandSettings> =
        flow.stateIn(scope, SharingStarted.Eagerly, IslandSettings())

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun update(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setBlocked(pkg: String, blocked: Boolean) {
        context.dataStore.edit {
            val cur = it[K.BLOCKED] ?: emptySet()
            it[K.BLOCKED] = if (blocked) cur + pkg else cur - pkg
        }
    }

    private fun Preferences.toSettings(): IslandSettings {
        val d = IslandSettings()
        return IslandSettings(
            enabled = this[K.ENABLED] ?: d.enabled,
            widthDp = this[K.WIDTH] ?: d.widthDp,
            heightDp = this[K.HEIGHT] ?: d.heightDp,
            offsetX = this[K.OFFSET_X] ?: d.offsetX,
            offsetY = this[K.OFFSET_Y] ?: d.offsetY,
            cornerDp = this[K.CORNER] ?: d.cornerDp,
            haptics = this[K.HAPTICS] ?: d.haptics,
            glow = this[K.GLOW] ?: d.glow,
            showNotifications = this[K.NOTIFS] ?: d.showNotifications,
            showMedia = this[K.MEDIA] ?: d.showMedia,
            showCalls = this[K.CALLS] ?: d.showCalls,
            showBattery = this[K.BATTERY] ?: d.showBattery,
            hideInLandscape = this[K.HIDE_LANDSCAPE] ?: d.hideInLandscape,
            accentIndex = this[K.ACCENT] ?: d.accentIndex,
            blockedApps = this[K.BLOCKED] ?: d.blockedApps
        )
    }
}
