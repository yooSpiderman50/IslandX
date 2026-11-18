package com.islandx.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Holds all active events. UI shows events[0]; the rest is the swipe-able queue. */
object PriorityEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Only touched from the main thread (scope uses Dispatchers.Main.immediate).
    private val items = LinkedHashMap<String, IslandEvent>()
    private val timeouts = HashMap<String, Job>()
    private val _events = MutableStateFlow<List<IslandEvent>>(emptyList())
    val events: StateFlow<List<IslandEvent>> = _events.asStateFlow()

    /**
     * While true, expiring events are kept alive. The island sets this while the user has it
     * expanded, so a notification no longer disappears from under the finger after its 6 s TTL.
     * Expiry resumes as soon as the island collapses.
     */
    private val hold = MutableStateFlow(false)

    fun setHold(held: Boolean) {
        hold.value = held
    }

    /** ttlMs = 0 means the event stays until removed. */
    fun post(event: IslandEvent, ttlMs: Long = 0L) {
        scope.launch {
            timeouts.remove(event.id)?.cancel()
            items[event.id] = event
            if (ttlMs > 0) {
                timeouts[event.id] = launch {
                    delay(ttlMs)
                    hold.first { !it } // wait here while the island is expanded
                    items.remove(event.id)
                    timeouts.remove(event.id)
                    publish()
                }
            }
            publish()
        }
    }

    fun remove(id: String) {
        scope.launch {
            timeouts.remove(id)?.cancel()
            if (items.remove(id) != null) publish()
        }
    }

    private fun publish() {
        _events.value = items.values.sortedWith(
            compareByDescending<IslandEvent> { it.priority }.thenByDescending { it.stamp }
        )
    }
}
