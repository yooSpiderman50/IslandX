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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

val AccentColors = listOf(
    Color(0xFF4DD0E1), Color(0xFF69F0AE), Color(0xFFFFB74D), Color(0xFFFF80AB), Color(0xFFFFFFFF)
)
val AccentNames = listOf("Cyan", "Green", "Orange", "Pink", "White")

/**
 * Decodes the capsule photo ONCE, down-sampled to ~640 px, and shares it between every
 * IslandBackground layer. The old code decoded the full-resolution photo (a 12 MP picture is
 * ~48 MB as ARGB_8888) separately for every layer and on every settings change.
 */
private object BackgroundImageCache {
    private val mutex = Mutex()
    private var cachedUri: String? = null
    private var cachedImage: ImageBitmap? = null

    suspend fun load(ctx: Context, uriString: String, maxPx: Int = 640): ImageBitmap? =
        mutex.withLock {
            if (cachedUri == uriString && cachedImage != null) return@withLock cachedImage
            val decoded = withContext(Dispatchers.IO) { decode(ctx, uriString, maxPx) }
            cachedUri = uriString
            cachedImage = decoded
            decoded
        }

    private fun decode(ctx: Context, uriString: String, maxPx: Int): ImageBitmap? = try {
        val resolver = ctx.contentResolver
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun IslandBackground(settings: IslandSettings, modifier: Modifier, shape: RoundedCornerShape) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(
        initialValue = null, settings.backgroundImageUri, settings.backgroundStyle
    ) {
        value = if (settings.backgroundStyle == 3 && settings.backgroundImageUri.isNotBlank()) {
            BackgroundImageCache.load(context, settings.backgroundImageUri)
        } else null
    }
    Box(modifier = modifier.clip(shape)) {
        when (settings.backgroundStyle) {
            0 -> Box(Modifier.fillMaxSize().background(Color(0xFF0E0E12).copy(alpha = 0.62f)))
            1 -> Box(Modifier.fillMaxSize().background(Color.White))
            2 -> Box(Modifier.fillMaxSize().background(Color(0xFF050505)))
            3 -> if (image != null) {
                Image(image!!, contentDescription = "Capsule background photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
        when (settings.backgroundStyle) {
            0 -> {
                // Frosted-glass surface: the window blur is configured by the overlay service;
                // these translucent layers add the characteristic glass tint and reflected edge.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    Color.White.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.03f),
                    Color(0xFFB7D5DC).copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.10f)
                ))))
                Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.48f), shape))
            }
            3 -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        }
    }
}

private enum class Mode { IDLE, COMPACT, EXPANDED }
private data class ContentKey(val mode: Mode, val id: String?)

private const val COMPACT_SIDE = 72 // dp of content on each side of the camera hole

/**
 * Text / surface colours. The old code hard-coded white text, which was invisible on the "White"
 * capsule background. The palette follows the selected background instead.
 */
private data class IslandPalette(
    val ink: Color, val inkSecondary: Color, val inkBody: Color, val chip: Color, val card: Color
)

private val DarkPalette = IslandPalette(
    ink = Color.White, inkSecondary = Color(0xFFAAAAAA), inkBody = Color(0xFFDDDDDD),
    chip = Color(0xFF2A2A2A), card = Color(0xFF18181D)
)
private val LightPalette = IslandPalette(
    ink = Color(0xFF111114), inkSecondary = Color(0xFF5F5F66), inkBody = Color(0xFF2B2B30),
    chip = Color(0xFFE4E4E9), card = Color(0xFFF1F1F4)
)
private val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** True when the user chose "Reduced motion": infinite animations must stay still. */
private val LocalReducedMotion = staticCompositionLocalOf { false }

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

    // Keep notifications alive while the island is open (otherwise the 6 s TTL removes the item
    // the user is reading) and release the hold when the overlay leaves composition.
    LaunchedEffect(isExpanded) { PriorityEngine.setHold(isExpanded) }
    DisposableEffect(Unit) { onDispose { PriorityEngine.setHold(false) } }

    // Last known event per id, so the outgoing layer of the cross-fade still shows ITS content
    // instead of the new event (or nothing, once the old event was removed).
    val lastKnown = remember { HashMap<String, IslandEvent>() }
    events.forEach { lastKnown[it.id] = it }
    if (lastKnown.size > 24) {
        val live = events.mapTo(HashSet()) { it.id }
        lastKnown.keys.retainAll(live)
    }
    val palette = if (settings.backgroundStyle == 1) LightPalette else DarkPalette
    val reducedMotion = settings.animationStyle == 2

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

    // detectTapGestures delays every single tap by the double-tap timeout (~300 ms) when an
    // onDoubleTap handler exists. Only register it when double-tap can actually dismiss something.
    val canDismiss = event != null && !incomingCall
    val interactionModifier = Modifier
        .pointerInput(canDismiss) {
            detectTapGestures(
                onTap = { onTap.value() },
                onLongPress = { onLong.value() },
                onDoubleTap = if (canDismiss) ({ _: Offset -> onDouble.value() }) else null
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
        // animateContentSize is what animates the card HEIGHT when it grows to its content
        // (expand / tab switch). It also lags the spring-animated width slightly; splitting the
        // two needs on-device tuning, so it is intentionally left as it was.
        .animateContentSize(tween(durationMillis = animationDuration))
        .heightIn(min = h, max = maxOverlayHeight)
        .then(
            if (!popupMode) {
                Modifier.clip(shape).background(when (settings.backgroundStyle) { 1 -> Color.White; 2 -> Color(0xFF050505); 3 -> Color.Transparent; else -> Color.Transparent }).then(
                    if (settings.glow && event != null) Modifier.border(1.dp, accent.copy(alpha = 0.65f), shape)
                    else Modifier
                )
            } else Modifier
        )
        // Parent-level pointer handlers consume pointer events. Keep them on the
        // compact capsule only; otherwise they block buttons and the seek slider.
        .then(if (!isExpanded) interactionModifier else Modifier)

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalReducedMotion provides reducedMotion
    ) {
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
                        .background(when (settings.backgroundStyle) { 1 -> Color.White; 2 -> Color(0xFF050505); 3 -> Color.Transparent; else -> Color.Transparent })
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
                        .background(when (settings.backgroundStyle) { 1 -> Color.White; 2 -> Color(0xFF050505); 3 -> Color.Transparent; else -> Color.Transparent })
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
                // Use the event that belongs to THIS cross-fade layer, not the newest one.
                val layerEvent = key.id?.let { id -> events.firstOrNull { it.id == id } ?: lastKnown[id] }
                when (key.mode) {
                    Mode.IDLE -> Box(Modifier.fillMaxWidth())
                    Mode.COMPACT -> if (layerEvent != null) {
                        CompactContent(layerEvent, settings.widthDp, h, accent, settings.privacyMode)
                    }
                    Mode.EXPANDED -> ExpandedContent(
                        event = layerEvent,
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
    } // CompositionLocalProvider
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
                    trackColor = LocalPalette.current.ink.copy(alpha = 0.2f)
                )
            } else {
                SmallText(if (privacyMode) e.appName else e.title.ifBlank { e.appName }, LocalPalette.current.ink)
            }
        }
        is IslandEvent.Call -> SmallText(if (e.incoming) "Incoming" else "On call", Color(0xFF4CAF50))
        is IslandEvent.Battery -> SmallText(
            "${e.percent}%", if (e.charging) Color(0xFF69F0AE) else Color(0xFFFF5252)
        )
        is IslandEvent.Timer -> SmallText(fmt(e.seconds), Color(0xFFFFB74D))
        is IslandEvent.Media -> {
            if (e.playing) AudioVisualizerBars(true, accent)
            else Icon(Icons.Filled.Pause, null, tint = LocalPalette.current.inkSecondary, modifier = Modifier.size(16.dp))
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
        Icon(fallback, null, tint = LocalPalette.current.ink, modifier = Modifier.size(size))
    }
}

/**
 * Equaliser bars. PERF: the old version ran four infinite animations and read their values in the
 * composition phase, so the row was recomposed and re-measured every frame (60x per second) for
 * as long as music played. Now there is ONE transition and the value is read inside the Canvas
 * draw lambda, so only the draw phase is invalidated. Nothing runs when paused or when the user
 * selected "Reduced motion".
 */
@Composable
fun AudioVisualizerBars(playing: Boolean, color: Color) {
    val animate = playing && !LocalReducedMotion.current
    val phase = if (animate) {
        rememberInfiniteTransition(label = "viz").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
    } else null

    Canvas(Modifier.size(width = 21.dp, height = 16.dp)) {
        val p = phase?.value ?: 0f // read in the DRAW phase only
        val barW = 3.dp.toPx()
        val gap = 3.dp.toPx()
        // Integer speeds make the loop seamless when the 1200 ms phase restarts.
        val speeds = intArrayOf(1, 2, 3, 2)
        for (i in 0 until 4) {
            val frac = if (phase == null) 0.2f
            else 0.25f + 0.75f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (p * speeds[i] + i * 0.25f)))
            val barH = size.height * frac
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f)
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
            Modifier
                .fillMaxWidth()
                // README promises "swipe up: collapse", but the gesture only existed on the compact
                // capsule, so it never worked while the card was expanded in place.
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -40.dp.toPx()) onClose() },
                        onDragCancel = { total = 0f }
                    ) { change, dy ->
                        change.consume()
                        total += dy
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IslandTab.entries.forEach { item ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (item == tab) accent else LocalPalette.current.chip)
                            .clickable { onTabChange(item) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            item.label,
                            color = if (item == tab) Color.Black else LocalPalette.current.ink,
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
                    .background(LocalPalette.current.chip)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = LocalPalette.current.ink, fontSize = 22.sp, lineHeight = 22.sp)
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
        Text(title, color = LocalPalette.current.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = LocalPalette.current.inkSecondary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun NotificationListItem(e: IslandEvent.Notif, ctx: Context, accent: Color, privacyMode: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)).background(LocalPalette.current.card).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BitmapIcon(e.icon, Icons.Filled.Notifications, size = 36.dp, round = 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.appName, color = LocalPalette.current.inkSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (privacyMode) {
                    Text("Notification content hidden", color = LocalPalette.current.ink, fontSize = 13.sp)
                } else {
                    if (e.title.isNotBlank()) Text(e.title, color = LocalPalette.current.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (e.text.isNotBlank()) Text(e.text, color = LocalPalette.current.inkBody, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Open", accent, Color.Black) {
                e.open.fire(ctx)
                PriorityEngine.remove(e.id)
            }
            Pill("Dismiss", LocalPalette.current.chip, LocalPalette.current.ink) { PriorityEngine.remove(e.id) }
        }
    }
}

@Composable
private fun EventSummaryRow(e: IslandEvent, privacyMode: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp))
            .background(LocalPalette.current.card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(LocalPalette.current.chip), contentAlignment = Alignment.Center) {
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
            Text(heading, color = LocalPalette.current.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = LocalPalette.current.inkSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CustomizePanel(settings: IslandSettings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = (ctx.applicationContext as? IslandXApp)?.settings
    Column(Modifier.fillMaxWidth()) {
        Text("Accent colour", color = LocalPalette.current.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
        Text("More size, position, privacy and expansion settings are in the main IslandX app.", color = LocalPalette.current.inkSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun OverlaySwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LocalPalette.current.ink, modifier = Modifier.weight(1f), fontSize = 13.sp)
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
                    .background(if (i == selected) LocalPalette.current.ink else LocalPalette.current.inkSecondary.copy(alpha = 0.5f))
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
                .background(LocalPalette.current.chip),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Call, null, tint = Color(0xFF4CAF50))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                e.caller, color = LocalPalette.current.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(if (e.incoming) "Incoming call" else "On call", color = LocalPalette.current.inkSecondary, fontSize = 13.sp)
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
            Text(e.appName, color = LocalPalette.current.inkSecondary, fontSize = 12.sp, maxLines = 1)
            if (privacyMode) {
                Text("Notification content hidden", color = LocalPalette.current.ink, fontSize = 13.sp)
            } else {
                if (e.title.isNotBlank()) {
                    Text(
                        e.title, color = LocalPalette.current.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (e.text.isNotBlank()) {
                    Text(
                        e.text, color = LocalPalette.current.inkBody, fontSize = 14.sp,
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
            trackColor = LocalPalette.current.ink.copy(alpha = 0.2f)
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pill("Open", accent, Color.Black) {
            e.open.fire(ctx)
            PriorityEngine.remove(e.id)
        }
        Pill("Dismiss", LocalPalette.current.chip, LocalPalette.current.ink) { PriorityEngine.remove(e.id) }
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
                color = LocalPalette.current.inkSecondary, fontSize = 13.sp
            )
            Text("${e.percent}%", color = LocalPalette.current.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(
        progress = { e.percent / 100f },
        modifier = Modifier.fillMaxWidth(),
        color = color,
        trackColor = LocalPalette.current.ink.copy(alpha = 0.2f)
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
            color = LocalPalette.current.inkSecondary, fontSize = 13.sp
        )
        Text(
            fmt(e.seconds), color = LocalPalette.current.ink, fontSize = 42.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundButton(if (e.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, label = if (e.running) "Pause timer" else "Resume timer") {
                TimerController.toggle()
            }
            RoundButton(Icons.Filled.Refresh, label = "Reset timer") { TimerController.reset() }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, size: Dp = 46.dp, label: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(LocalPalette.current.chip)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = LocalPalette.current.ink)
    }
}

@Composable
private fun MediaCard(e: IslandEvent.Media, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BitmapIcon(e.art, Icons.Filled.MusicNote, size = 56.dp, round = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.title, color = LocalPalette.current.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(e.artist.ifBlank { "Unknown artist" }, color = LocalPalette.current.inkSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AudioVisualizerBars(e.playing, accent)
    }

    val duration = e.durationMs.coerceAtLeast(0L)
    var pos by remember(e.title, e.artist, e.durationMs) {
        mutableLongStateOf(e.positionMs.coerceAtLeast(0L))
    }
    var isSeeking by remember { mutableStateOf(false) }
    // After a seek the media app needs a moment to publish its new state. Until it does, extrapolate
    // from the seek target instead of from the stale pre-seek position (that made the thumb jump back).
    var seekAnchor by remember(e.title, e.artist, e.durationMs) { mutableStateOf<Pair<Long, Long>?>(null) }

    LaunchedEffect(e.title, e.artist, e.durationMs, e.positionMs, e.updatedAt, e.playing, e.speed) {
        while (true) {
            if (!isSeeking) {
                val now = SystemClock.elapsedRealtime()
                val anchor = seekAnchor
                val estimated = when {
                    anchor != null && anchor.second > e.updatedAt ->
                        anchor.first + if (e.playing) ((now - anchor.second) * e.speed).toLong() else 0L
                    e.playing ->
                        e.positionMs.coerceAtLeast(0L) +
                            ((now - e.updatedAt).coerceAtLeast(0L) * e.speed).toLong()
                    else -> e.positionMs.coerceAtLeast(0L)
                }
                pos = if (duration > 0L) estimated.coerceIn(0L, duration) else estimated
            }
            if (!e.playing) break
            delay(500) // text shows whole seconds; 300 ms recomposed the whole card for nothing
        }
    }

    Spacer(Modifier.height(10.dp))
    if (duration > 0L) {
        Slider(
            value = pos.coerceIn(0L, duration).toFloat(),
            onValueChange = { value -> isSeeking = true; pos = value.toLong() },
            onValueChangeFinished = {
                MediaBridge.seekTo(pos)
                seekAnchor = pos to SystemClock.elapsedRealtime()
                isSeeking = false
            },
            valueRange = 0f..duration.toFloat(),
            enabled = true
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatMediaTime(pos), color = LocalPalette.current.inkSecondary, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(formatMediaTime(duration), color = LocalPalette.current.inkSecondary, fontSize = 11.sp)
        }
    } else {
        LinearProgressIndicator(
            progress = { 0f },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = LocalPalette.current.ink.copy(alpha = 0.2f)
        )
        Text("Track position unavailable", color = LocalPalette.current.inkSecondary, fontSize = 11.sp)
    }

    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton(Icons.Filled.SkipPrevious, 42.dp, "Previous track") { MediaBridge.previous() }
        RoundButton(Icons.Filled.FastRewind, 42.dp, "Back 10 seconds") { MediaBridge.seekBy(-10_000L) }
        RoundButton(if (e.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 52.dp, if (e.playing) "Pause" else "Play") {
            MediaBridge.playPause()
        }
        RoundButton(Icons.Filled.FastForward, 42.dp, "Forward 10 seconds") { MediaBridge.seekBy(10_000L) }
        RoundButton(Icons.Filled.SkipNext, 42.dp, "Next track") { MediaBridge.next() }
    }
    Spacer(Modifier.height(4.dp))
    Text("Previous · −10s · Play/Pause · +10s · Next", color = LocalPalette.current.inkSecondary, fontSize = 10.sp)
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
            // Only hours:minutes are shown, so wake up at the next minute boundary, not every second.
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 20L)
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(timeFmt.format(now), color = LocalPalette.current.ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(dateFmt.format(now), color = LocalPalette.current.inkSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(Icons.Filled.FlashlightOn, label = "Torch") { Torch.toggle(ctx) }
            RoundButton(Icons.Filled.Wifi, label = "Wi-Fi settings") { openSettings(ctx, Settings.ACTION_WIFI_SETTINGS) }
            RoundButton(Icons.Filled.Bluetooth, label = "Bluetooth settings") { openSettings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) }
            RoundButton(Icons.Filled.VolumeUp, label = "Sound settings") { openSettings(ctx, Settings.ACTION_SOUND_SETTINGS) }
            RoundButton(Icons.Filled.Timer, label = "Start 5 minute timer") { TimerController.startCountdown(300) }
        }
        Spacer(Modifier.height(6.dp))
        Text("Torch · Wi-Fi · Bluetooth · Sound · 5 min timer", color = LocalPalette.current.inkSecondary, fontSize = 10.sp)
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
    @Volatile private var on = false
    private var registered = false

    fun toggle(ctx: Context) {
        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            if (!registered) {
                // Track the real state (the user can also switch the torch from quick settings).
                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        on = enabled
                    }
                }, null)
                registered = true
            }
            val id = cm.cameraIdList.first {
                val c = cm.getCameraCharacteristics(it)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            cm.setTorchMode(id, !on) // no CAMERA permission needed for the torch
        } catch (_: Exception) {
        }
    }
}
