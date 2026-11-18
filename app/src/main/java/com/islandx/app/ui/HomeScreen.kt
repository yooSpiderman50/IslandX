package com.islandx.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.islandx.app.IslandXApp
import com.islandx.app.data.IslandSettings
import com.islandx.app.data.K
import com.islandx.app.data.SettingsRepository
import com.islandx.app.domain.CutoutHelper
import com.islandx.app.domain.IslandEvent
import com.islandx.app.domain.PriorityEngine
import com.islandx.app.domain.TimerController
import com.islandx.app.service.IslandAccessibilityService
import com.islandx.app.service.IslandOverlayService
import com.islandx.app.ui.overlay.AccentColors
import com.islandx.app.ui.overlay.AccentNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val CardBg = Color(0xFF1B1B20)
private val Muted = Color(0xFFAAAAAA)

@Composable
fun HomeScreen(activity: ComponentActivity, tick: Int) {
    val app = activity.applicationContext as IslandXApp
    val repo = app.settings
    val s by repo.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showApps by remember { mutableStateOf(false) }
    var localTick by remember { mutableIntStateOf(0) }
    var syncKey by remember { mutableIntStateOf(0) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { localTick++ }

    // Every persisted URI grant counts against a per-app limit, so release the ones we stop using.
    fun releasePhotoPermission(uriString: String) {
        if (uriString.isBlank()) return
        try {
            activity.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) { }
    }

    val backgroundImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val previous = s.backgroundImageUri
            if (previous != uri.toString()) releasePhotoPermission(previous)
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            scope.launch {
                repo.update {
                    it[K.BACKGROUND_IMAGE_URI] = uri.toString()
                    it[K.BACKGROUND_STYLE] = 3
                }
            }
        }
    }

    val overlayOk = remember(tick, localTick) { Settings.canDrawOverlays(activity) }
    val accessibilityOk = remember(tick, localTick) {
        IslandAccessibilityService.isEnabled(activity)
    }
    val listenerOk = remember(tick, localTick) {
        NotificationManagerCompat.getEnabledListenerPackages(activity).contains(activity.packageName)
    }
    val postOk = remember(tick, localTick) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            activity, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    val batteryOk = remember(tick, localTick) {
        activity.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(activity.packageName)
    }

    fun <T> save(key: androidx.datastore.preferences.core.Preferences.Key<T>, v: T) {
        scope.launch { repo.set(key, v) }
    }

    if (showApps) {
        BackHandler { showApps = false }
        AppRulesScreen(s, repo) { showApps = false }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("IslandX", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Dynamic Island for your Android camera cutout", color = Muted, fontSize = 13.sp)

            // ---- master switch
            Block("Island") {
                SwitchRow("Enable IslandX", s.enabled) { on ->
                    save(K.ENABLED, on)
                    if (on && overlayOk) {
                        ContextCompat.startForegroundService(
                            activity, Intent(activity, IslandOverlayService::class.java)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        PriorityEngine.post(
                            IslandEvent.Notif(
                                "test", "IslandX", "Hello from IslandX", "Your island is working!",
                                null, -1, null, 70
                            ), 6000L
                        )
                    }) { Text("Test notification") }
                    OutlinedButton(onClick = {
                        PriorityEngine.post(IslandEvent.Battery(80, true), 5000L)
                    }) { Text("Test charging") }
                }
            }

            // ---- setup
            Block("Setup (do these once)") {
                PermRow("Display over other apps", "Fallback overlay permission", overlayOk) {
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}"))
                    )
                }
                PermRow(
                    "Accessibility overlay (recommended to test)",
                    "May improve island layer order; Android may still place system UI above it",
                    accessibilityOk
                ) {
                    activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                PermRow("Notification access", "Notifications, music and calls", listenerOk) {
                    activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                PermRow("Show notifications", "Android 13+: for the 'running' notice", postOk) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                PermRow("Battery: unrestricted", "Keeps the island alive in background", batteryOk) {
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${activity.packageName}")
                        )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Notification access greyed out? (Android 13+, app installed from APK): " +
                        "Settings > Apps > IslandX > ⋮ menu > Allow restricted settings, then try again.",
                    color = Muted, fontSize = 12.sp
                )
            }

            // ---- position
            Block("Position & size (live preview)") {
                Button(
                    onClick = {
                        val g = CutoutHelper.detect(activity)
                        if (g == null) {
                            Toast.makeText(
                                activity, "No camera cutout found (use portrait). Adjust manually.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            scope.launch {
                                repo.update {
                                    it[K.WIDTH] = g.widthDp
                                    it[K.HEIGHT] = g.heightDp
                                    it[K.OFFSET_X] = g.offsetXDp
                                    it[K.OFFSET_Y] = g.offsetYDp
                                }
                                delay(200)
                                syncKey++
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Auto-detect camera position") }
                SliderRow("Width (dp)", s.widthDp, 40..260, syncKey) { save(K.WIDTH, it) }
                SliderRow("Height (dp)", s.heightDp, 20..80, syncKey) { save(K.HEIGHT, it) }
                SliderRow("Horizontal offset (dp)", s.offsetX, -200..200, syncKey) { save(K.OFFSET_X, it) }
                SliderRow("Top offset (dp)", s.offsetY, 0..80, syncKey) { save(K.OFFSET_Y, it) }
                SliderRow("Corner radius (dp)", s.cornerDp, 8..40, syncKey) { save(K.CORNER, it) }
                Text(
                    "Tip: make the black pill just a little bigger than your camera hole.",
                    color = Muted, fontSize = 12.sp
                )
            }

            // ---- features
            Block("Features") {
                SwitchRow("Notifications", s.showNotifications) { save(K.NOTIFS, it) }
                SwitchRow("Music player", s.showMedia) { save(K.MEDIA, it) }
                SwitchRow("Calls", s.showCalls) { save(K.CALLS, it) }
                SwitchRow("Charging & low battery", s.showBattery) { save(K.BATTERY, it) }
                SwitchRow("Haptic feedback", s.haptics) { save(K.HAPTICS, it) }
                SwitchRow("Glow border on activity", s.glow) { save(K.GLOW, it) }
                SwitchRow("Hide in landscape", s.hideInLandscape) { save(K.HIDE_LANDSCAPE, it) }
                SwitchRow("Privacy mode (hide notification text)", s.privacyMode) { save(K.PRIVACY_MODE, it) }
                Spacer(Modifier.height(8.dp))
                Text("Capsule background", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Choose the background only; other IslandX features stay unchanged.", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Glass", "White", "Black").forEachIndexed { index, label ->
                        OutlinedButton(
                            onClick = { save(K.BACKGROUND_STYLE, index) },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (s.backgroundStyle == index) "✓ $label" else label, fontSize = 12.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { backgroundImageLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (s.backgroundStyle == 3 && s.backgroundImageUri.isNotBlank()) "Change photo" else "Choose photo") }
                    if (s.backgroundImageUri.isNotBlank()) {
                        OutlinedButton(onClick = {
                            releasePhotoPermission(s.backgroundImageUri)
                            // One atomic write instead of two racing ones.
                            scope.launch {
                                repo.update {
                                    it[K.BACKGROUND_STYLE] = 0
                                    it[K.BACKGROUND_IMAGE_URI] = ""
                                }
                            }
                        }) {
                            Text("Remove photo")
                        }
                    }
                }
                Text(
                    when (s.backgroundStyle) {
                        1 -> "White background selected"
                        2 -> "Black background selected"
                        3 -> if (s.backgroundImageUri.isNotBlank()) "Your photo is selected" else "Choose a photo to use it as the background"
                        else -> "Glass background selected"
                    }, color = Muted, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Text("Accent colour", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    AccentColors.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(if (i == s.accentIndex) 36.dp else 30.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable { save(K.ACCENT, i) }
                        )
                    }
                }
                Text(
                    "Selected: ${AccentNames.getOrElse(s.accentIndex) { "" }}",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showApps = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Per-app rules (${s.blockedApps.size} blocked)")
                }
            }

            // ---- expansion and animation
            Block("Expansion & animation") {
                Text("Expanded card style", color = Color.White, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { save(K.EXPANSION_MODE, 0) }) {
                        Text(if (s.expansionMode == 0) "✓ In place" else "In place")
                    }
                    OutlinedButton(onClick = { save(K.EXPANSION_MODE, 1) }) {
                        Text(if (s.expansionMode == 1) "✓ Popup card" else "Popup card")
                    }
                }
                Text(
                    if (s.expansionMode == 0) "The island grows into one card." else "The small island stays above a separate card.",
                    color = Muted, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Text("Animation style", color = Color.White, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { save(K.ANIMATION_STYLE, 0) }) {
                        Text(if (s.animationStyle == 0) "✓ Smooth" else "Smooth")
                    }
                    OutlinedButton(onClick = { save(K.ANIMATION_STYLE, 1) }) {
                        Text(if (s.animationStyle == 1) "✓ Snappy" else "Snappy")
                    }
                    OutlinedButton(onClick = { save(K.ANIMATION_STYLE, 2) }) {
                        Text(if (s.animationStyle == 2) "✓ Reduced" else "Reduced")
                    }
                }
                Text("Reduced motion minimizes size and content transitions.", color = Muted, fontSize = 12.sp)
            }

            // ---- timer
            var timerHours by remember { mutableStateOf("0") }
            var timerMinutes by remember { mutableStateOf("5") }
            var timerSeconds by remember { mutableStateOf("0") }
            val customTimerSeconds = (timerHours.toLongOrNull() ?: 0L) * 3600L +
                (timerMinutes.toLongOrNull() ?: 0L) * 60L +
                (timerSeconds.toLongOrNull() ?: 0L)
            val validCustomTimer = timerHours.toLongOrNull() != null &&
                timerMinutes.toLongOrNull() != null && timerSeconds.toLongOrNull() != null &&
                timerHours.toLongOrNull()!! >= 0 && timerMinutes.toLongOrNull()!! >= 0 &&
                timerSeconds.toLongOrNull()!! >= 0 && customTimerSeconds > 0 &&
                customTimerSeconds <= 7L * 24L * 3600L
            Block("Timer & stopwatch") {
                Text("Set any duration", color = Color.White, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = timerHours,
                        onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) timerHours = it },
                        label = { Text("Hours") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timerMinutes,
                        onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) timerMinutes = it },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timerSeconds,
                        onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) timerSeconds = it },
                        label = { Text("Seconds") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = { TimerController.startCountdown(customTimerSeconds) },
                    enabled = validCustomTimer,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start custom timer") }
                Text("You can set hours, minutes and seconds (up to 7 days).", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { TimerController.startCountdown(60) }) { Text("1 min") }
                    OutlinedButton(onClick = { TimerController.startCountdown(300) }) { Text("5 min") }
                    OutlinedButton(onClick = { TimerController.startCountdown(600) }) { Text("10 min") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { TimerController.startStopwatch() }) { Text("Stopwatch") }
                    TextButton(onClick = { TimerController.reset() }) { Text("Stop / reset") }
                }
                Text("Pause/resume from the expanded island.", color = Muted, fontSize = 12.sp)
            }

            // ---- gestures + OEM tips
            Block("Gestures") {
                Text(
                    "Tap: open app / expand  ·  Long-press or swipe down: expand  ·  Swipe up: collapse\n" +
                        "Swipe left/right: next item in queue  ·  Double-tap: dismiss item\n" +
                        "Expand with nothing active: clock + quick toggles",
                    color = Muted, fontSize = 13.sp
                )
            }
            Block("Phone-specific tips") {
                Text(
                    "Xiaomi/Redmi/Poco: Security > Permissions > Autostart ON; Battery saver = No restrictions; " +
                        "lock the app in Recents; allow 'Display pop-up windows while running in background'.\n\n" +
                        "Oppo/Realme/OnePlus: Settings > Battery > App battery management > IslandX > Allow background + auto-launch.\n\n" +
                        "Vivo/iQOO: i Manager > App management > Autostart ON; High background power consumption allowed.\n\n" +
                        "Samsung: Battery > Background usage limits > Never sleeping apps > add IslandX.",
                    color = Muted, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermRow(title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White)
            Text(desc, color = Muted, fontSize = 12.sp)
        }
        if (granted) {
            Text("✓ Granted", color = Color(0xFF69F0AE), fontSize = 13.sp)
        } else {
            Button(onClick = onClick) { Text("Grant") }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, syncKey: Int, onChange: (Int) -> Unit) {
    // Local state keeps dragging smooth; syncKey re-reads the stored value after auto-detect.
    var v by remember(syncKey) { mutableFloatStateOf(value.toFloat()) }
    // Slider emits a float on every pixel of movement. Each DataStore write rewrites the file and
    // fans out to the overlay, the listener and this screen, so only write when the INTEGER changes.
    var lastSent by remember(syncKey) { mutableIntStateOf(value) }
    Column {
        Text("$label: ${v.roundToInt()}", color = Color.White, fontSize = 13.sp)
        Slider(
            value = v,
            onValueChange = {
                v = it
                val rounded = it.roundToInt()
                if (rounded != lastSent) {
                    lastSent = rounded
                    onChange(rounded)
                }
            },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

// ------------------------------------------------------------------ per-app rules

@Composable
private fun AppRulesScreen(s: IslandSettings, repo: SettingsRepository, onBack: () -> Unit) {
    val ctx: Context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    // Loading labels for hundreds of apps used to run on the main thread (visible hitch / ANR risk).
    val loadedApps by produceState<List<Pair<String, String>>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != ctx.packageName }
                .sortedBy { it.second.lowercase() }
        }
    }
    val apps = loadedApps
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Apps allowed on the island", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        if (apps == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(apps, key = { it.first }) { (pkg, label) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = Color.White)
                        Text(pkg, color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = pkg !in s.blockedApps,
                        onCheckedChange = { allowed -> scope.launch { repo.setBlocked(pkg, !allowed) } }
                    )
                }
            }
        }
    }
}
