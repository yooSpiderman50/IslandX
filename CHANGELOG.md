# Changelog

## 1.2.3 (media visibility fix)

- Keep the music event visible when an active media session does not publish metadata immediately.
- Prefer a playing or paused media session over unrelated/stale sessions returned by Android.
- Treat buffering/connecting/fast-forwarding/rewinding as active playback for capsule visibility.
- Updated version metadata to `1.2.3` / `versionCode 5`.

### Validation status

- Source-level checks passed for the updated media-session selection and nullable metadata handling.
- A full Android Gradle build and physical-device test have not been run in this environment. Run the GitHub Actions `Build APK` workflow before installing.

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
