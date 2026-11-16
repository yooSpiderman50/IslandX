package com.islandx.app.ui.overlay

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.islandx.app.data.IslandSettings
import com.islandx.app.domain.IslandEvent
import com.islandx.app.domain.MediaBridge
import com.islandx.app.domain.PriorityEngine
import com.islandx.app.domain.TimerController
import com.islandx.app.domain.openIntent
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

val AccentColors = listOf(
    Color(0xFF4DD0E1), Color(0xFF69F0AE), Color(0xFFFFB74D), Color(0xFFFF80AB), Color(0xFFFFFFFF)
)
val AccentNames = listOf("Cyan", "Green", "Orange", "Pink", "White")

private enum class Mode { IDLE, COMPACT, EXPANDED }
private data class ContentKey(val mode: Mode, val id: String?)

private const val COMPACT_SIDE = 72 // dp of content on each side of the camera hole
private val Gray = Color(0xFFAAAAAA)
private val Chip = Color(0xFF2A2A2A)

/**
 * Gestures:
 *  tap          -> open the app of the notification/call, otherwise expand/collapse
 *  long press   -> expand
 *  swipe down   -> expand      swipe up -> collapse
 *  swipe left/right -> next item in queue (or dismiss when there is only one)
 *  double tap   -> dismiss current item
 */
@Composable
fun IslandOverlay(settings: IslandSettings, events: List<IslandEvent>) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val screenW = configuration.screenWidthDp.coerceAtLeast(1)
    val screenH = configuration.screenHeightDp.coerceAtLeast(1)
    var expanded by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }

    val safeIndex = if (events.isEmpty()) 0 else index.coerceIn(0, events.lastIndex)
    val event = events.getOrNull(safeIndex)
    val incomingCall = event is IslandEvent.Call && event.incoming
    val isExpanded = expanded || incomingCall
    val mode = when {
        isExpanded -> Mode.EXPANDED
        event != null -> Mode.COMPACT
        else -> Mode.IDLE
    }
    val accent = AccentColors.getOrElse(settings.accentIndex) { AccentColors[0] }

    // A new top item always starts collapsed.
    LaunchedEffect(event?.id) { expanded = false }

    fun buzz() {
        if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    val onTap = rememberUpdatedState<() -> Unit> {
        val open = event?.openIntent()
        if (!isExpanded && open != null) {
            open.fire(ctx)
            if (event is IslandEvent.Notif) PriorityEngine.remove(event.id)
        } else {
            expanded = !isExpanded
            buzz()
        }
    }
    val onLong = rememberUpdatedState<() -> Unit> {
        expanded = true
        buzz()
    }
    val onDouble = rememberUpdatedState<() -> Unit> {
        if (event != null && !incomingCall) PriorityEngine.remove(event.id)
    }
    val onSwipe = rememberUpdatedState<(Offset, Float) -> Unit> { total, threshold ->
        if (abs(total.y) > abs(total.x)) {
            if (total.y > threshold) {
                expanded = true
                buzz()
            } else if (total.y < -threshold) {
                expanded = false
            }
        } else if (abs(total.x) > threshold) {
            if (events.size > 1) {
                index = (safeIndex + (if (total.x < 0) 1 else -1) + events.size) % events.size
                buzz()
            } else if (event != null && !incomingCall) {
                PriorityEngine.remove(event.id)
            }
        }
    }

    val minH = if (mode == Mode.COMPACT) maxOf(settings.heightDp, 36) else settings.heightDp
    val targetW = when (mode) {
        Mode.IDLE -> settings.widthDp
        Mode.COMPACT -> minOf(settings.widthDp + 2 * COMPACT_SIDE, (screenW - 16).coerceAtLeast(1))
        Mode.EXPANDED -> minOf((screenW - 24).coerceAtLeast(1), 380)
    }
    val targetRadius = when (mode) {
        Mode.EXPANDED -> maxOf(settings.cornerDp, 28).toFloat()
        else -> minOf(settings.cornerDp.toFloat(), minH / 2f)
    }
    val w by animateDpAsState(
        targetW.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "w"
    )
    val h by animateDpAsState(
        minH.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "h"
    )
    val r by animateDpAsState(
        targetRadius.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "r"
    )
    val shape = RoundedCornerShape(r)

    Box(
        modifier = Modifier
            .width(w)
            .animateContentSize(spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
            .heightIn(
                min = h,
                max = maxOf(minH, screenH - settings.offsetY - 8).dp
            )
            .clip(shape)
            .background(Color.Black)
            .then(
                if (settings.glow && event != null)
                    Modifier.border(1.dp, accent.copy(alpha = 0.65f), shape)
                else Modifier
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap.value() },
                    onLongPress = { onLong.value() },
                    onDoubleTap = { onDouble.value() }
                )
            }
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = { onSwipe.value(total, 48.dp.toPx()) },
                    onDragCancel = { total = Offset.Zero },
                    onDrag = { change, amount ->
                        change.consume()
                        total += amount
                    }
                )
            }
    ) {
        Crossfade(
            targetState = ContentKey(mode, event?.id),
            animationSpec = tween(220),
            label = "content"
        ) { key ->
            when (key.mode) {
                Mode.IDLE -> Box(Modifier.fillMaxWidth())
                Mode.COMPACT -> if (event != null) {
                    CompactContent(event, settings.widthDp, h, accent)
                }
                Mode.EXPANDED -> ExpandedContent(
                    event = event,
                    count = events.size,
                    selected = safeIndex,
                    topPad = settings.heightDp.dp + 2.dp,
                    accent = accent
                )
            }
        }
    }
}

// ------------------------------------------------------------------ compact

@Composable
private fun CompactContent(e: IslandEvent, gapDp: Int, height: Dp, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(1f)
                .padding(start = 14.dp), contentAlignment = Alignment.CenterStart
        ) { LeftIcon(e) }
        Spacer(Modifier.width(gapDp.dp)) // the camera hole sits here
        Box(
            Modifier
                .weight(1f)
                .padding(end = 14.dp), contentAlignment = Alignment.CenterEnd
        ) { RightInfo(e, accent) }
    }
}

@Composable
private fun LeftIcon(e: IslandEvent) {
    when (e) {
        is IslandEvent.Notif -> BitmapIcon(e.icon, Icons.Filled.Notifications)
        is IslandEvent.Call -> Icon(
            Icons.Filled.Call, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp)
        )
        is IslandEvent.Battery -> Icon(
            if (e.charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryAlert,
            null,
            tint = if (e.charging) Color(0xFF69F0AE) else Color(0xFFFF5252),
            modifier = Modifier.size(22.dp)
        )
        is IslandEvent.Timer -> Icon(
            Icons.Filled.Timer, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(20.dp)
        )
        is IslandEvent.Media -> BitmapIcon(e.art, Icons.Filled.MusicNote, round = 6.dp)
    }
}

@Composable
private fun RightInfo(e: IslandEvent, accent: Color) {
    when (e) {
        is IslandEvent.Notif -> {
            if (e.progress >= 0) {
                CircularProgressIndicator(
                    progress = { e.progress / 100f },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = accent,
                    trackColor = Color(0x33FFFFFF)
                )
            } else {
                SmallText(e.title.ifBlank { e.appName }, Color.White)
            }
        }
        is IslandEvent.Call -> SmallText(if (e.incoming) "Incoming" else "On call", Color(0xFF4CAF50))
        is IslandEvent.Battery -> SmallText(
            "${e.percent}%", if (e.charging) Color(0xFF69F0AE) else Color(0xFFFF5252)
        )
        is IslandEvent.Timer -> SmallText(fmt(e.seconds), Color(0xFFFFB74D))
        is IslandEvent.Media -> {
            if (e.playing) AudioVisualizerBars(true, accent)
            else Icon(Icons.Filled.Pause, null, tint = Gray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SmallText(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun BitmapIcon(bmp: Bitmap?, fallback: ImageVector, size: Dp = 22.dp, round: Dp = 11.dp) {
    if (bmp != null) {
        val img = remember(bmp) { bmp.asImageBitmap() }
        Image(
            bitmap = img, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(round))
        )
    } else {
        Icon(fallback, null, tint = Color.White, modifier = Modifier.size(size))
    }
}

@Composable
fun AudioVisualizerBars(playing: Boolean, color: Color) {
    val t = rememberInfiniteTransition(label = "viz")
    val a by t.animateFloat(
        0.25f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "a"
    )
    val b by t.animateFloat(
        1f, 0.2f, infiniteRepeatable(tween(310, easing = LinearEasing), RepeatMode.Reverse), label = "b"
    )
    val c by t.animateFloat(
        0.4f, 0.9f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "c"
    )
    val d by t.animateFloat(
        0.9f, 0.3f, infiniteRepeatable(tween(370, easing = LinearEasing), RepeatMode.Reverse), label = "d"
    )
    Row(
        Modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(a, b, c, d).forEach { v ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(if (playing) v else 0.2f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

// ------------------------------------------------------------------ expanded

@Composable
private fun ExpandedContent(
    event: IslandEvent?, count: Int, selected: Int, topPad: Dp, accent: Color
) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = topPad, bottom = 16.dp)
    ) {
        when (event) {
            null -> ToolsPanel(accent)
            is IslandEvent.Call -> CallCard(event, ctx)
            is IslandEvent.Battery -> BatteryCard(event, accent)
            is IslandEvent.Timer -> TimerCard(event)
            is IslandEvent.Media -> MediaCard(event, accent)
            is IslandEvent.Notif -> NotifCard(event, ctx, accent)
        }
        if (count > 1) {
            Spacer(Modifier.height(12.dp))
            QueueDots(count, selected)
        }
    }
}

@Composable
private fun QueueDots(count: Int, selected: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        for (i in 0 until count) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (i == selected) Color.White else Color(0xFF555555))
            )
        }
    }
}

@Composable
private fun CallCard(e: IslandEvent.Call, ctx: Context) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Chip),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Call, null, tint = Color(0xFF4CAF50))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                e.caller, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(if (e.incoming) "Incoming call" else "On call", color = Gray, fontSize = 13.sp)
        }
    }
    if (e.actions.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            e.actions.forEachIndexed { i, a ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(actionColor(a.title, i, e.incoming))
                        .clickable { a.intent.fire(ctx) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        a.title, color = Color.White, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun actionColor(title: String, index: Int, incoming: Boolean): Color {
    val t = title.lowercase()
    val red = Color(0xFFE53935)
    val green = Color(0xFF2E7D32)
    val negative = listOf("decline", "reject", "hang", "end", "cut", "dismiss", "ignore", "काट")
    val positive = listOf("answer", "accept", "pick", "receive", "उठा")
    return when {
        negative.any { it in t } -> red
        positive.any { it in t } -> green
        !incoming -> red
        index == 0 -> red
        else -> green
    }
}

@Composable
private fun NotifCard(e: IslandEvent.Notif, ctx: Context, accent: Color) {
    Row(verticalAlignment = Alignment.Top) {
        BitmapIcon(e.icon, Icons.Filled.Notifications, size = 40.dp, round = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.appName, color = Gray, fontSize = 12.sp, maxLines = 1)
            if (e.title.isNotBlank()) {
                Text(
                    e.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (e.text.isNotBlank()) {
                Text(
                    e.text, color = Color(0xFFDDDDDD), fontSize = 14.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (e.progress >= 0) {
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { e.progress / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = Color(0x33FFFFFF)
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pill("Open", accent, Color.Black) {
            e.open.fire(ctx)
            PriorityEngine.remove(e.id)
        }
        Pill("Dismiss", Chip, Color.White) { PriorityEngine.remove(e.id) }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BatteryCard(e: IslandEvent.Battery, accent: Color) {
    val color = if (e.charging) Color(0xFF69F0AE) else Color(0xFFFF5252)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (e.charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryAlert,
            null, tint = color, modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (e.charging) "Charging" else "Battery low",
                color = Gray, fontSize = 13.sp
            )
            Text("${e.percent}%", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(
        progress = { e.percent / 100f },
        modifier = Modifier.fillMaxWidth(),
        color = color,
        trackColor = Color(0x33FFFFFF)
    )
}

@Composable
private fun TimerCard(e: IslandEvent.Timer) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when {
                e.finished -> "Time's up!"
                e.countdown -> "Timer"
                else -> "Stopwatch"
            },
            color = Gray, fontSize = 13.sp
        )
        Text(
            fmt(e.seconds), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundButton(if (e.running) Icons.Filled.Pause else Icons.Filled.PlayArrow) {
                TimerController.toggle()
            }
            RoundButton(Icons.Filled.Refresh) { TimerController.reset() }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, size: Dp = 46.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Chip)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White)
    }
}

@Composable
private fun MediaCard(e: IslandEvent.Media, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BitmapIcon(e.art, Icons.Filled.MusicNote, size = 56.dp, round = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(e.artist, color = Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AudioVisualizerBars(e.playing, accent)
    }

    var pos by remember(e) { mutableLongStateOf(e.positionMs) }
    LaunchedEffect(e) {
        while (true) {
            pos = if (e.playing)
                e.positionMs + ((SystemClock.elapsedRealtime() - e.updatedAt) * e.speed).toLong()
            else e.positionMs
            if (!e.playing) break
            delay(500)
        }
    }
    val fraction = if (e.durationMs > 0) (pos.toFloat() / e.durationMs).coerceIn(0f, 1f) else 0f
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth(),
        color = accent,
        trackColor = Color(0x33FFFFFF)
    )
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton(Icons.Filled.SkipPrevious) { MediaBridge.previous() }
        Spacer(Modifier.width(16.dp))
        RoundButton(if (e.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 54.dp) {
            MediaBridge.playPause()
        }
        Spacer(Modifier.width(16.dp))
        RoundButton(Icons.Filled.SkipNext) { MediaBridge.next() }
    }
}

/** Shown when you expand the island while nothing is happening: clock + quick toggles. */
@Composable
private fun ToolsPanel(accent: Color) {
    val ctx = LocalContext.current
    val timeFmt = remember { SimpleDateFormat("h:mm", Locale.US) }
    val dateFmt = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(timeFmt.format(now), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(dateFmt.format(now), color = Gray, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(Icons.Filled.FlashlightOn) { Torch.toggle(ctx) }
            RoundButton(Icons.Filled.Wifi) { openSettings(ctx, Settings.ACTION_WIFI_SETTINGS) }
            RoundButton(Icons.Filled.Bluetooth) { openSettings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) }
            RoundButton(Icons.Filled.VolumeUp) { openSettings(ctx, Settings.ACTION_SOUND_SETTINGS) }
            RoundButton(Icons.Filled.Timer) { TimerController.startCountdown(300) }
        }
        Spacer(Modifier.height(6.dp))
        Text("Torch · Wi-Fi · Bluetooth · Sound · 5 min timer", color = Gray, fontSize = 10.sp)
    }
}

// ------------------------------------------------------------------ helpers

fun fmt(sec: Long): String =
    if (sec >= 3600) "%d:%02d:%02d".format(Locale.US, sec / 3600, sec % 3600 / 60, sec % 60)
    else "%02d:%02d".format(Locale.US, sec / 60, sec % 60)

private fun openSettings(ctx: Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

private fun PendingIntent?.fire(ctx: Context) {
    val pi = this ?: return
    try {
        val opts = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }.toBundle()
        } else null
        pi.send(ctx, 0, null, null, null, null, opts)
    } catch (_: Exception) {
    }
}

private object Torch {
    private var on = false
    fun toggle(ctx: Context) {
        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.first {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            on = !on
            cm.setTorchMode(id, on) // no CAMERA permission needed for the torch
        } catch (_: Exception) {
            on = false
        }
    }
}
