# IslandX – Dynamic Island for Android

Camera cutout (punch-hole) ke upar iPhone jaisa Dynamic Island.

## Kya-kya hai
- Camera cutout auto-detect + manual size/position sliders (live preview)
- Notifications (app icon, title, text, progress ring), tap = app khule
- Music player: album art, progress, play/pause/next/prev, visualizer bars
- Calls: incoming call par auto-expand + Answer/Decline buttons (call notification se)
- Charging animation, low battery warning
- Timer + stopwatch
- Quick toggles (torch, Wi-Fi, Bluetooth, sound, 5-min timer) + clock
- Priority queue (call > battery > notification > timer > music), swipe se switch
- Per-app allow/block, haptics, glow, accent colour, landscape hide, boot par auto-start
- Expanded tabs: Home, Music, Notifications, Controls, Events, Style
- Two expansion layouts: expand in place or separate popup card below the capsule
- Smooth / Snappy / Reduced motion options and notification privacy mode

## Gestures
Tap: open/expand | Long-press ya swipe down: expand | Swipe up: collapse
Swipe left/right: queue me next (ya dismiss) | Double-tap: dismiss

## Build (3 tarike)
1. **GitHub Actions (PC ya phone browser)**: repo banao, ye saari files upload karo, "Actions" tab me
   "Build APK" run hone do, end me artifact "IslandX-debug-apk" download karo.
2. **Android Studio**: folder open karo > Build > Build APK(s). (JDK 17)
3. **Command line**: `gradle assembleDebug` (Gradle 8.9, JDK 17, Android SDK 34)

APK: app/build/outputs/apk/debug/app-debug.apk

## Install ke baad
1. Display over other apps > ON (fallback overlay)
2. Accessibility overlay > enable "IslandX Overlay" only if you want to test improved window layering
3. Notification access > ON (greyed ho to: App info > ⋮ > Allow restricted settings)
4. Battery > Unrestricted
5. "Auto-detect camera position" dabao, phir sliders se fine-tune karo.

## Limitations
- Accessibility Overlay may improve layer order, but Android/device-specific system UI and heads-up notifications can still appear above the island. Verify on your own phone.
- Fullscreen video/game auto-hide abhi nahi hai (sirf landscape hide hai).
- Notification quick-reply, privacy dots (mic/camera) aur Maps-specific UI abhi nahi hain.
- Call buttons tabhi aate hain jab dialer ki call notification me Answer/Decline buttons ho.


## Version 1.2.0 update notes

- Added top tabs to the expanded island and a separate popup-card layout option.
- Added animation style selection and a privacy switch to hide notification title/message text.
- Added accent colour, glow and haptic controls inside the expanded Style tab.
- Updated version metadata to `1.2.0` (`versionCode` 3).

## Version 1.1.0 update notes

- Improved overlay bounds handling so the island window is less likely to extend beyond the visible screen.
- Expanded content can scroll on shorter displays instead of being clipped by the overlay window height limit.
- Settings changes now trigger media/call/notification event cleanup in the notification listener.
- Foreground service and Android permission requirements remain in place; Android may still show its required running-service notification.
- Version updated to `1.1.0` (`versionCode` 2).

This source update should be built and tested with GitHub Actions and on a physical Android device. System pop-up placement is controlled partly by Android and the device manufacturer, so overlap behavior must be verified on the target phone.

## Version 1.2.4 update notes

- Added an optional AccessibilityService-backed overlay using `TYPE_ACCESSIBILITY_OVERLAY`.
- Added an Accessibility settings shortcut in the app.
- The regular application overlay is suppressed while the accessibility overlay is enabled to avoid duplicate islands.
- Accessibility permission is optional and must be enabled manually by the user.
- Android Gradle build and physical-device behavior have not yet been verified; build with GitHub Actions and test on the target device.
