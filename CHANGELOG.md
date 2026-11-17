# IslandX v1.2.7

- Fix expanded capsule/card height so it stays a compact floating card instead of covering most of the screen.
- Make background layers match the measured capsule/card size without forcing the parent to expand.
- Keep long expanded content scrollable and preserve existing themes, custom photo, music, notifications, timer, and gestures.

# IslandX v1.2.5

- Add a custom countdown timer with editable hours, minutes, and seconds (up to 7 days).
- Keep quick 1, 5, and 10 minute timer shortcuts and stopwatch.

# Changelog

## 1.2.4 (accessibility overlay experiment)

- Added a real `AccessibilityService` that can host the existing Compose island with `TYPE_ACCESSIBILITY_OVERLAY`.
- Added an Accessibility settings row in the app; the user must explicitly enable the service.
- Prevented the regular application overlay and accessibility overlay from intentionally displaying at the same time.
- Reused existing island UI, media controls, gestures, settings, and event queue.
- Preserved the regular “Display over other apps” overlay as a fallback when Accessibility Overlay is not enabled.
- Updated app version metadata to `1.2.4` / `versionCode 6`.

### Important limitation

This is an implementation to test, not a guarantee that every Android status-bar icon, heads-up notification, or manufacturer-specific system window will appear behind the island. Accessibility overlays can affect window layering, but final behavior depends on Android version and device software. Enable the service only if you are comfortable granting an accessibility service; this service declares that it does not retrieve screen content.

### Validation status

- Manifest and accessibility XML files were parsed successfully.
- Full Gradle build and physical-device testing have not been run in this environment. Build the APK with GitHub Actions first, then test on the target phone. If the build fails, share the exact Actions error log.


## 1.2.3 (cutout position fix)

- Removed the extra 6dp vertical clearance that pushed the overlay below the camera cutout.
- Made the default vertical offset 0dp; users can still adjust the island position in settings.
- Kept the existing overlay type, Compose gestures, and tap handling unchanged.

### Important limitation

This patch improves vertical positioning only. A normal `TYPE_APPLICATION_OVERLAY` cannot reliably draw above Android's system status-bar icons. Switching to `TYPE_ACCESSIBILITY_OVERLAY` requires a real `AccessibilityService` and user-enabled Accessibility permission; it is not safe to do by changing a single line. This patch does not yet add that service.

### Validation status

- Source changes inspected.
- Android Gradle build and physical-device test have not been run in this environment.

## 1.2.0 (development update)

- Added expanded-card tabs for Home, Music, Notifications, Controls, Events, and Style.
- Added two expansion layouts: expand in place or keep the compact capsule above a separate popup card.
- Added Smooth, Snappy, and Reduced motion animation choices.
- Added a privacy mode that hides notification title and message text in the island while retaining the app label.
- Added quick accent-colour, glow-border, and haptic controls in the expanded Style tab.
- Updated app version metadata to `1.2.0` / `versionCode 3`.

### Validation status

- Android manifest XML and GitHub Actions workflow YAML are checked for valid structure.
- Source changes were reviewed for consistent settings keys and Compose call sites.
- A full Gradle/Android build has **not** been run in this environment because the uploaded project has no Gradle wrapper and a Gradle executable/dependency download is unavailable here. Run the GitHub Actions workflow before installing this APK.
- Physical-device testing is still required for popup placement, tab touch behavior, notification privacy, media controls, calls, and foreground-service behavior.

---


## 1.1.0 (development update)

- Clamped overlay X/Y offsets to safer display bounds and removed `FLAG_LAYOUT_NO_LIMITS` to reduce off-screen placement risk.
- Added a maximum overlay height based on the available display area and scrolling for expanded content on shorter screens.
- Clamped battery percentage values before posting battery events.
- Removed stale battery events when the Battery feature is disabled.
- Reacted to Notification Access settings changes so disabled media, calls, and notification events are removed; active notifications are reconsidered when related features are re-enabled.
- Stored the call notification package name in call events so app-blocking rules can also clear a displayed call event.
- Updated app version metadata to `1.1.0` / `versionCode 2`.

### Validation status

- Android XML files parsed successfully.
- Source-level assertions passed for the intended changes.
- GitHub Actions workflow YAML parsed successfully.
- A full Gradle/Android build has not been run in this environment because a Gradle executable/wrapper is not present in the uploaded project and this environment cannot download dependencies. Run the repository's GitHub Actions workflow before installing this APK.
- Physical-device tests are still required for overlay/system-popup interactions, media players, call notifications, and Android foreground-service behavior.

## 1.2.6
- Added capsule background choices: Glass, White, Midnight gradient, and user-selected photo.
- Existing timer, music, notifications, gestures, and overlay options retained.
