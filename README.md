# MynAssistant Android + Desktop Agent

Native Android companion app for MynAssistant, with an isolated Windows companion agent under desktop-agent/. The existing MynAi/Python project is not modified.

## Android development

- Kotlin
- Jetpack Compose
- Firebase Authentication and Cloud Firestore
- GitHub Actions for cloud APK builds

## Automatic device discovery

- Android listens in real time to Firestore devices documents filtered by the signed-in user's ownerId.
- Windows Agent signs in via Google OAuth on first launch and registers the PC under the same Firebase user.
- Android remote actions are queued in Firestore commands; the Windows Agent consumes commands for its registered device.
- The Android app no longer requires the user to type a Windows IP address or local bridge token.

## Build outputs

- Android APK: published to GitHub Releases only after CI verifies the full-feature APK and its signing certificate. Actions artifact upload is disabled because the repository's Actions storage quota is full.
- Windows companion: built as a versioned prerelease ZIP through the Windows Agent workflow.

## Current scope

The new Windows Agent provides automatic device discovery, online heartbeats, volume/desktop/mouse commands, Unicode text entry, Backspace/Enter input, and an on-demand low-frame-rate JPEG desktop preview. Smooth full-screen remote desktop streaming is a later WebRTC milestone.


## Device registration (Android)

Version 0.2.2 registers the signed-in Android installation in Firestore `devices`. The record uses a per-installation ID that is hashed with the Firebase UID (the raw installation identifier is not used as the document ID). The app writes a heartbeat about once per minute while the activity is in the foreground and marks its record offline when the activity is paused. After a sign-out/session switch, heartbeats stop; the admin server treats records without a recent heartbeat as offline within its three-minute freshness window. Android presence registration does not enable Windows remote commands; those remain restricted to the `firebase-agent` connection mode.


## Full-feature Android APK recovery

The release line is based on the 0.4.9 app (versionCode 26) rather than the reduced 0.2.2 shell. Version 0.5.0 (versionCode 27) restores the Hey Meyene wake-word service, Vosk Turkish recognition, sherpa-onnx keyword spotting, model installers, accessibility integration, diagnostics, and their unit tests while retaining Firebase device registration/heartbeat. CI runs unit tests, verifies the signed APK, rejects unexpectedly small APKs, and publishes a prerelease APK asset on pushes to main. The APK is distributed through GitHub Releases; Actions-artifact upload is intentionally omitted because this repository's Actions storage quota is full.


## Account and auth polish (Android 0.5.7)

The device-name pencil sits beside the device name; Save uses the proper save icon. Online/offline badges adapt to light and dark themes. Touchpad dots avoid the `Touchpad` title. The launcher foreground is reduced into Android's adaptive safe zone so the full original mark remains visible. The shared auth form shows only one Google action according to the selected mode. Email/password registration requires at least eight characters. Email auth feedback is shown once inside the form, not copied into the navigation drawer. CI asserts that only one mode-specific Google action exists, old six-character guidance is absent, and auth warnings do not render in the drawer. The account page retains Google linking, email verification, SMS-verified phone numbers, and profile photo upload. Existing remote-control, keyboard and wake-word paths remain intact.

### Firebase setup required for account extras

The Android app already points to the `myncloud-24` Firebase project. Enable **Authentication → Phone** for SMS verification and publish `storage.rules` to the project's Storage bucket before using profile-photo upload. The Storage rules restrict writes to each signed-in user's own `profilePhotos/{uid}/...` path, images under 5 MiB. From a configured Firebase CLI environment, run `firebase deploy --only storage --project myncloud-24`. SMS delivery also depends on Firebase Phone Authentication, app verification and project quota/billing settings.
