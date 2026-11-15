package com.islandx.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Countdown timer + stopwatch that publish themselves to the island. */
object TimerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var seconds = 0L
    private var countdown = false
    private var running = false
    private var active = false

    fun startCountdown(sec: Long) {
        job?.cancel()
        seconds = sec
        countdown = true
        running = true
        active = true
        loop()
    }

    fun startStopwatch() {
        job?.cancel()
        seconds = 0
        countdown = false
        running = true
        active = true
        loop()
    }

    fun toggle() {
        if (!active) return
        if (running) {
            running = false
            job?.cancel()
            publish(false)
        } else {
            running = true
            loop()
        }
    }

    fun reset() {
        job?.cancel()
        active = false
        running = false
        seconds = 0
        PriorityEngine.remove("timer")
    }

    private fun loop() {
        job?.cancel()
        publish(false)
        job = scope.launch {
            while (running) {
                delay(1000)
                if (countdown) {
                    seconds -= 1
                    if (seconds <= 0) {
                        seconds = 0
                        running = false
                        active = false
                        publish(true)
                        return@launch
                    }
                } else {
                    seconds += 1
                }
                publish(false)
            }
        }
    }

    private fun publish(finished: Boolean) {
        PriorityEngine.post(
            IslandEvent.Timer(seconds, running, countdown, finished),
            if (finished) 8000L else 0L
        )
    }
}
