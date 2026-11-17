package com.islandx.app.ui.overlay

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
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
import com.islandx.app.IslandXApp
import com.islandx.app.data.IslandSettings
import com.islandx.app.data.K
import com.islandx.app.domain.IslandEvent
import com.islandx.app.domain.MediaBridge
import com.islandx.app.domain.PriorityEngine
import com.islandx.app.domain.TimerController
import com.islandx.app.domain.openIntent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

val AccentColors = listOf(
    Color(0xFF4DD0E1), Color(0xFF69F0AE), Color(0xFFFFB74D), Color(0xFFFF80AB), Color(0xFFFFFFFF)
)
val AccentNames = listOf("Cyan", "Green", "Orange", "Pink", "White")

@Composable
private fun IslandBackground(settings: IslandSettings, modifier: Modifier, shape: RoundedCornerShape) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, settings.backgroundImageUri, settings.backgroundStyle) {
        value = null
        if (settings.backgroundStyle == 3 && settings.backgroundImageUri.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(settings.backgroundImageUri))?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                } catch (_: Exception) { null }
            }
        }
    }
    Box(modifier = modifier.clip(shape)) {
        when (settings.backgroundStyle) {
            1 -> Box(Modifier.fillMaxSize().background(Color.White))
            2 -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF243B55), Color(0xFF10182D)))))
            3 -> if (bitmap != null) {
                Image(bitmap!!.asImageBitmap(), contentDescription = "Capsule background photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
            else -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
        }
        when (settings.backgroundStyle) {
            0 -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.035f), Color.Black.copy(alpha = 0.22f)))))
            1 -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
            3 -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
        }
    }
}

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
    var selectedTab by remember { mutableStateOf(IslandTab.HOME) }

    val safeIndex = if (events.isEmpty()) 0 else index.coerceIn(0, events.lastIndex)
    val event = events.getOrNull(safeIndex)
    val incomingCall = event is IslandEvent.Call && event.incoming
    val isExpanded = expanded || incomingCall
    val mode = when {
        isExpanded -> Mode.EXPANDED
        event != null -> Mode.COMPACT
        else -> Mode.IDLE
    }
    val popupMode = isExpanded && settings.expansionMode == 1
    val accent = AccentColors.getOrElse(settings.accentIndex) { AccentColors[0] }

    // A new top item always starts collapsed; incoming calls remain auto-expanded.
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
    val compactHeight = maxOf(settings.heightDp, 36)
    val expandedWidth = minOf((screenW - 24).coerceAtLeast(1), 380)
    val compactWidth = minOf(settings.widthDp + 2 * COMPACT_SIDE, expandedWidth)
    val targetW = when (mode) {
        Mode.IDLE -> settings.widthDp
        Mode.COMPACT -> compactWidth
        Mode.EXPANDED -> expandedWidth
    }
    val targetRadius = when (mode) {
        Mode.EXPANDED -> maxOf(settings.cornerDp, 28).toFloat()
        else -> minOf(settings.cornerDp.toFloat(), minH / 2f)
    }
    val animationDuration = when (settings.animationStyle) {
        1 -> 150
        2 -> 1
        else -> 230
    }
    val dimensionSpec = when (settings.animationStyle) {
        1 -> tween<Dp>(durationMillis = animationDuration)
        2 -> tween<Dp>(durationMillis = 1)
        else -> spring<Dp>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
    }
    val w by animateDpAsState(targetW.dp, dimensionSpec, label = "w")
    val h by animateDpAsState(minH.dp, dimensionSpec, label = "h")
    val r by animateDpAsState(targetRadius.dp, dimensionSpec, label = "r")
    val shape = RoundedCornerShape(r)
    // Keep expanded UI as a compact floating card instead of allowing the ComposeView
    // to grow to almost the full screen height. Long content remains scrollable.
    val availableHeight = (screenH - settings.offsetY - 24).coerceAtLeast(180)
    val maxOverlayHeight = minOf(480, availableHeight).dp

    val interactionModifier = Modifier
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

    val baseModifier = Modifier
        .width(w)
        .animateContentSize(tween(durationMillis = animationDuration))
        .heightIn(min = h, max = maxOverlayHeight)
        .then(
            if (!popupMode) {
                Modifier.clip(shape).background(if (settings.backgroundStyle == 1) Color.White else if (settings.backgroundStyle == 2) Color(0xFF10182D) else Color.Black.copy(alpha = if (settings.backgroundStyle == 0) 0.72f else 1f)).then(
                    if (settings.glow && event != null) Modifier.border(1.dp, accent.copy(alpha = 0.65f), shape)
                    else Modifier
                )
            } else Modifier
        )
        // Parent-level pointer handlers consume pointer events. Keep them on the
        // compact capsule only; otherwise they block buttons and the seek slider.
        .then(if (!isExpanded) interactionModifier else Modifier)

    Box(modifier = baseModifier) {
        if (!popupMode) IslandBackground(settings, Modifier.matchParentSize(), shape)
        if (popupMode) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // The compact capsule remains around the cutout while the card opens below it.
                val compactShape = RoundedCornerShape(minOf(settings.cornerDp, compactHeight / 2).dp)
                Box(
                    Modifier
                        .width(compactWidth.dp)
                        .height(compactHeight.dp)
                        .clip(compactShape)
                        .background(if (settings.backgroundStyle == 1) Color.White else if (settings.backgroundStyle == 2) Color(0xFF10182D) else Color.Black.copy(alpha = if (settings.backgroundStyle == 0) 0.72f else 1f))
                        .then(
                            if (settings.glow && event != null)
                                Modifier.border(1.dp, accent.copy(alpha = 0.65f), compactShape)
                            else Modifier
                        )
                        .then(if (popupMode) interactionModifier else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    IslandBackground(settings, Modifier.matchParentSize(), compactShape)
                    if (event != null) CompactContent(event, settings.widthDp, compactHeight.dp, accent, settings.privacyMode)
                }
                Spacer(Modifier.height(8.dp))
                val popupShape = RoundedCornerShape(28.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(popupShape)
                        .background(if (settings.backgroundStyle == 1) Color.White else if (settings.backgroundStyle == 2) Color(0xFF10182D) else Color.Black.copy(alpha = if (settings.backgroundStyle == 0) 0.72f else 1f))
                        .then(
                            if (settings.glow && event != null)
                                Modifier.border(1.dp, accent.copy(alpha = 0.65f), popupShape)
                            else Modifier
                        )
                ) {
                    IslandBackground(settings, Modifier.matchParentSize(), popupShape)
                    ExpandedContent(
                        event = event,
                        allEvents = events,
                        count = events.size,
                        selected = safeIndex,
                        topPad = 12.dp,
                        accent = accent,
                        settings = settings,
                        tab = selectedTab,
                        onTabChange = { selectedTab = it },
                        onClose = { expanded = false; buzz() }
                    )
                }
            }
        } else {
            Crossfade(
                targetState = ContentKey(mode, event?.id),
                animationSpec = tween(animationDuration),
                label = "content"
            ) { key ->
                when (key.mode) {
                    Mode.IDLE -> Box(Modifier.fillMaxWidth())
                    Mode.COMPACT -> if (event != null) {
                        CompactContent(event, settings.widthDp, h, accent, settings.privacyMode)
                    }
                    Mode.EXPANDED -> ExpandedContent(
                        event = event,
                        allEvents = events,
                        count = events.size,
                        selected = safeIndex,
                        topPad = settings.heightDp.dp + 2.dp,
                        accent = accent,
                        settings = settings,
                        tab = selectedTab,
                        onTabChange = { selectedTab = it },
                        onClose = { expanded = false; buzz() }
                    )
                }
            }
        }
    }
}

private enum class IslandTab(val label: String) {
    HOME("Home"), MUSIC("Music"), NOTIFICATIONS("Notifs"), CONTROLS("Controls"), EVENTS("Events"), CUSTOMIZE("Style")
}

// ------------------------------------------------------------------ compact

@Composable
private fun CompactContent(e: IslandEvent, gapDp: Int, height: Dp, accent: Color, privacyMode: Boolean) {
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
        ) { RightInfo(e, accent, privacyMode) }
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
private fun RightInfo(e: IslandEvent, accent: Color, privacyMode: Boolean) {
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
                SmallText(if (privacyMode) e.appName else e.title.ifBlank { e.appName }, Color.White)
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
    event: IslandEvent?,
    allEvents: List<IslandEvent>,
    count: Int,
    selected: Int,
    topPad: Dp,
    accent: Color,
    settings: IslandSettings,
    tab: IslandTab,
    onTabChange: (IslandTab) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = topPad, bottom = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IslandTab.values().forEach { item ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (item == tab) accent else Chip)
                            .clickable { onTabChange(item) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            item.label,
                            color = if (item == tab) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Chip)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = Color.White, fontSize = 22.sp, lineHeight = 22.sp)
            }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            IslandTab.HOME -> {
                when (event) {
                    null -> ToolsPanel(accent)
                    is IslandEvent.Call -> CallCard(event, ctx)
                    is IslandEvent.Battery -> BatteryCard(event, accent)
                    is IslandEvent.Timer -> TimerCard(event)
                    is IslandEvent.Media -> MediaCard(event, accent)
                    is IslandEvent.Notif -> NotifCard(event, ctx, accent, settings.privacyMode)
                }
                if (count > 1) {
                    Spacer(Modifier.height(12.dp))
                    QueueDots(count, selected)
                }
            }
            IslandTab.MUSIC -> {
                val media = allEvents.filterIsInstance<IslandEvent.Media>().firstOrNull()
                if (media == null) EmptyTab("No active music", "Start playback in a media app that exposes Android media controls.")
                else MediaCard(media, accent)
            }
            IslandTab.NOTIFICATIONS -> {
                val notifications = allEvents.filterIsInstance<IslandEvent.Notif>()
                if (notifications.isEmpty()) EmptyTab("No active notifications", "New eligible notifications appear here briefly.")
                else notifications.forEach { NotificationListItem(it, ctx, accent, settings.privacyMode) }
            }
            IslandTab.CONTROLS -> ToolsPanel(accent)
            IslandTab.EVENTS -> {
                if (allEvents.isEmpty()) EmptyTab("Nothing happening", "Calls, music, charging and notifications appear here when active.")
                else allEvents.forEach { EventSummaryRow(it, settings.privacyMode) }
            }
            IslandTab.CUSTOMIZE -> CustomizePanel(settings)
        }
    }
}

@Composable
private fun EmptyTab(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = Gray, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun NotificationListItem(e: IslandEvent.Notif, ctx: Context, accent: Color, privacyMode: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)).background(Color(0xFF18181D)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BitmapIcon(e.icon, Icons.Filled.Notifications, size = 36.dp, round = 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.appName, color = Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (privacyMode) {
                    Text("Notification content hidden", color = Color.White, fontSize = 13.sp)
                } else {
                    if (e.title.isNotBlank()) Text(e.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (e.text.isNotBlank()) Text(e.text, color = Color(0xFFDDDDDD), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Open", accent, Color.Black) {
                e.open.fire(ctx)
                PriorityEngine.remove(e.id)
            }
            Pill("Dismiss", Chip, Color.White) { PriorityEngine.remove(e.id) }
        }
    }
}

@Composable
private fun EventSummaryRow(e: IslandEvent, privacyMode: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF18181D)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Chip), contentAlignment = Alignment.Center) {
            LeftIcon(e)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val heading = when (e) {
                is IslandEvent.Call -> e.caller.ifBlank { "Call" }
                is IslandEvent.Battery -> if (e.charging) "Charging" else "Battery low"
                is IslandEvent.Timer -> if (e.countdown) "Timer" else "Stopwatch"
                is IslandEvent.Media -> e.title
                is IslandEvent.Notif -> e.appName
            }
            val detail = when (e) {
                is IslandEvent.Call -> if (e.incoming) "Incoming call" else "Call in progress"
                is IslandEvent.Battery -> "${e.percent}%"
                is IslandEvent.Timer -> fmt(e.seconds)
                is IslandEvent.Media -> e.artist
                is IslandEvent.Notif -> if (privacyMode) "Notification content hidden" else e.title.ifBlank { e.text }
            }
            Text(heading, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Gray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CustomizePanel(settings: IslandSettings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = (ctx.applicationContext as? IslandXApp)?.settings
    Column(Modifier.fillMaxWidth()) {
        Text("Accent colour", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            AccentColors.forEachIndexed { i, color ->
                Box(
                    Modifier.size(if (i == settings.accentIndex) 36.dp else 30.dp)
                        .clip(CircleShape).background(color)
                        .then(if (i == settings.accentIndex) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                        .clickable { repo?.let { r -> scope.launch { r.set(K.ACCENT, i) } } }
                )
            }
        }
        OverlaySwitchRow("Glow border", settings.glow) { value ->
            repo?.let { r -> scope.launch { r.set(K.GLOW, value) } }
        }
        OverlaySwitchRow("Haptic feedback", settings.haptics) { value ->
            repo?.let { r -> scope.launch { r.set(K.HAPTICS, value) } }
        }
        Text("More size, position, privacy and expansion settings are in the main IslandX app.", color = Gray, fontSize = 12.sp)
    }
}

@Composable
private fun OverlaySwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange)
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
private fun NotifCard(e: IslandEvent.Notif, ctx: Context, accent: Color, privacyMode: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        BitmapIcon(e.icon, Icons.Filled.Notifications, size = 40.dp, round = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.appName, color = Gray, fontSize = 12.sp, maxLines = 1)
            if (privacyMode) {
                Text("Notification content hidden", color = Color.White, fontSize = 13.sp)
            } else {
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
            Text(e.artist.ifBlank { "Unknown artist" }, color = Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AudioVisualizerBars(e.playing, accent)
    }

    val duration = e.durationMs.coerceAtLeast(0L)
    var pos by remember(e.title, e.artist, e.durationMs) {
        mutableLongStateOf(e.positionMs.coerceAtLeast(0L))
    }
    var isSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(e.title, e.artist, e.durationMs, e.positionMs, e.updatedAt, e.playing, e.speed) {
        while (true) {
            if (!isSeeking) {
                val estimated = if (e.playing) {
                    e.positionMs.coerceAtLeast(0L) +
                        ((SystemClock.elapsedRealtime() - e.updatedAt).coerceAtLeast(0L) * e.speed).toLong()
                } else e.positionMs.coerceAtLeast(0L)
                pos = if (duration > 0L) estimated.coerceIn(0L, duration) else estimated
            }
            if (!e.playing) break
            delay(300)
        }
    }

    Spacer(Modifier.height(10.dp))
    if (duration > 0L) {
        Slider(
            value = pos.coerceIn(0L, duration).toFloat(),
            onValueChange = { value -> isSeeking = true; pos = value.toLong() },
            onValueChangeFinished = {
                MediaBridge.seekTo(pos)
                isSeeking = false
            },
            valueRange = 0f..duration.toFloat(),
            enabled = true
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatMediaTime(pos), color = Gray, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(formatMediaTime(duration), color = Gray, fontSize = 11.sp)
        }
    } else {
        LinearProgressIndicator(
            progress = { 0f },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = Color(0x33FFFFFF)
        )
        Text("Track position unavailable", color = Gray, fontSize = 11.sp)
    }

    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton(Icons.Filled.SkipPrevious, 42.dp) { MediaBridge.previous() }
        RoundButton(Icons.Filled.FastRewind, 42.dp) { MediaBridge.seekBy(-10_000L) }
        RoundButton(if (e.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 52.dp) {
            MediaBridge.playPause()
        }
        RoundButton(Icons.Filled.FastForward, 42.dp) { MediaBridge.seekBy(10_000L) }
        RoundButton(Icons.Filled.SkipNext, 42.dp) { MediaBridge.next() }
    }
    Spacer(Modifier.height(4.dp))
    Text("Previous · −10s · Play/Pause · +10s · Next", color = Gray, fontSize = 10.sp)
}

private fun formatMediaTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(Locale.US, totalSeconds / 60L, totalSeconds % 60L)
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
