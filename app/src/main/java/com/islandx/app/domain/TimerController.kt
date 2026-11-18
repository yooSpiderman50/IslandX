package com.islandx.app.domain

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Countdown timer + stopwatch that publish themselves to the island.
 *
 * Fixes compared with 1.2.8:
 *  - time comes from SystemClock.elapsedRealtime() instead of counting `delay(1000)` calls, so
 *    the timer no longer drifts (every loop iteration used to be 1000 ms + work) and catches up
 *    correctly after the process was throttled;
 *  - a finished countdown now vibrates, so it is noticeable when the screen is off;
 *  - startCountdown(<=0) is ignored instead of finishing instantly.
 *
 * Call [init] once from Application.onCreate (needed for the vibration).
 */
object TimerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var appContext: Context? = null

    private var countdown = false
    private var running = false
    private var active = false

    // Whole seconds shown while paused, and the clock reference while running.
    private var pausedSeconds = 0L
    private var startElapsed = 0L // elapsedRealtime when the current run segment started
    private var baseSeconds = 0L  // seconds already counted before the current run segment

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun startCountdown(sec: Long) {
        if (sec <= 0L) return
        begin(countdown = true, from = sec)
    }

    fun startStopwatch() = begin(countdown = false, from = 0L)

    fun toggle() {
        if (!active) return
        if (running) {
            pausedSeconds = currentSeconds()
            running = false
            job?.cancel()
            publish(false)
        } else {
            baseSeconds = pausedSeconds
            startElapsed = SystemClock.elapsedRealtime()
            running = true
            loop()
        }
    }

    fun reset() {
        job?.cancel()
        active = false
        running = false
        pausedSeconds = 0
        PriorityEngine.remove("timer")
    }

    private fun begin(countdown: Boolean, from: Long) {
        job?.cancel()
        this.countdown = countdown
        pausedSeconds = from
        baseSeconds = from
        startElapsed = SystemClock.elapsedRealtime()
        running = true
        active = true
        loop()
    }

    /** Seconds to display right now. */
    private fun currentSeconds(): Long {
        if (!running) return pausedSeconds
        val elapsed = (SystemClock.elapsedRealtime() - startElapsed) / 1000L
        return if (countdown) (baseSeconds - elapsed).coerceAtLeast(0L) else baseSeconds + elapsed
    }

    private fun loop() {
        job?.cancel()
        publish(false)
        job = scope.launch {
            while (running) {
                // Sleep until the next whole-second boundary of the run segment (no drift).
                val sinceStart = SystemClock.elapsedRealtime() - startElapsed
                delay(1000L - (sinceStart % 1000L))
                if (countdown && currentSeconds() <= 0L) {
                    running = false
                    active = false
                    pausedSeconds = 0L
                    publish(true)
                    vibrateDone()
                    return@launch
                }
                publish(false)
            }
        }
    }

    private fun publish(finished: Boolean) {
        val shown = if (finished) 0L else currentSeconds()
        PriorityEngine.post(
            IslandEvent.Timer(shown, running, countdown, finished),
            if (finished) 8000L else 0L
        )
    }

    private fun vibrateDone() {
        val ctx = appContext ?: return
        try {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 300), -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(effect)
            }
        } catch (_: Exception) {
        }
    }
}
