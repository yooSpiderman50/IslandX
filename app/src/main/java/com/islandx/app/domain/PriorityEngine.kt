package com.islandx.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Holds all active events. UI shows events[0]; the rest is the swipe-able queue. */
object PriorityEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val items = LinkedHashMap<String, IslandEvent>()
    private val timeouts = HashMap<String, Job>()
    private val _events = MutableStateFlow<List<IslandEvent>>(emptyList())
    val events: StateFlow<List<IslandEvent>> = _events.asStateFlow()

    /** ttlMs = 0 means the event stays until removed. */
    fun post(event: IslandEvent, ttlMs: Long = 0L) {
        scope.launch {
            timeouts.remove(event.id)?.cancel()
            items[event.id] = event
            if (ttlMs > 0) {
                timeouts[event.id] = launch {
                    delay(ttlMs)
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
            items.remove(id)
            publish()
        }
    }

    private fun publish() {
        _events.value = items.values.sortedWith(
            compareByDescending<IslandEvent> { it.priority }.thenByDescending { it.stamp }
        )
    }
}
