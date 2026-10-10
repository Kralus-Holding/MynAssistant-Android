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

- Android APK: Android APK workflow artifact MynAssistant-debug-apk.
- Windows companion: Windows Agent workflow artifact MynAssistant-Windows-Agent.

## Current scope

The new Windows Agent provides automatic device discovery, online heartbeats, volume/desktop/mouse commands. Live desktop video streaming and a full remote desktop session are a later milestone; this initial version does not claim screen streaming.
