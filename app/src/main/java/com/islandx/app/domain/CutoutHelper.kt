package com.islandx.app.domain

import android.app.Activity
import android.os.Build

data class CutoutGeometry(val widthDp: Int, val heightDp: Int, val offsetXDp: Int, val offsetYDp: Int)

object CutoutHelper {
    /** Reads the camera hole from the display cutout. Call it from an Activity in portrait. */
    fun detect(activity: Activity): CutoutGeometry? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val cutout = activity.window.decorView.rootWindowInsets?.displayCutout ?: return null
        val m = activity.resources.displayMetrics
        if (m.widthPixels > m.heightPixels) return null // landscape: rects are on the side
        val d = m.density
        val screenW = m.widthPixels
        val rects = cutout.boundingRects
        val rect = rects.firstOrNull { it.top <= 2 && it.left > 0 && it.right < screenW }
            ?: rects.minByOrNull { it.top }
            ?: return null

        val pad = 6f // pill is a bit bigger than the hole
        val top = maxOf(0f, rect.top / d - pad)
        val bottom = rect.bottom / d + pad
        val width = rect.width() / d + pad * 2
        val centerX = (rect.centerX() - screenW / 2f) / d
        return CutoutGeometry(
            widthDp = width.toInt().coerceIn(40, 260),
            heightDp = (bottom - top).toInt().coerceIn(20, 80),
            offsetXDp = centerX.toInt(),
            offsetYDp = top.toInt()
        )
    }
}
