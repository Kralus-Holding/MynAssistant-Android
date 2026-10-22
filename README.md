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


## Remote-control UI (Android 0.5.2)

The PC controller shows the device name once with a pencil action beside its presence badge; the editor opens only when requested and displays save failures inline. The touchpad uses a dark grey dotted surface and has distinct enabled/disabled icons. Disabling it removes pointer gesture handlers from the touchpad surface so swipes can scroll the page. The keyboard row includes a text field, Backspace, and Enter. Text plus Enter are submitted as one queued command so they retain execution order. Phone records do not enter the PC controller selection.
