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

## Hey Meyene — offline voice wake word

- Wake-word recognition uses the Vosk Android streaming engine, so audio capture is one continuous on-device stream while the user has explicitly enabled listening. This avoids cycling Android's one-utterance speech recognizer during silence.
- The first activation downloads the official lightweight Turkish model (about 35 MB) to the app's private storage. Internet is needed for that one-time download; after the model is installed, recognition is performed on-device without sending voice audio to a speech cloud service.
- While wake listening is enabled, Android shows its microphone privacy indicator and MynAssistant keeps a foreground-service notification visible. The microphone is not silently enabled; the user starts and stops listening from the app/notification.
- Saying “Hey Meyene” opens a compact floating panel over the current app. The panel pauses recognition when editing/confirming a command, then resumes wake listening after it is dismissed or the command is handed off.
- The Windows-like panel uses Android's “Display over other apps” permission. Device power management can still stop background services, so real-device testing is required for long-duration standby.
- Keyboard layout: the activity uses adjustResize and keeps the composer at the bottom of the resized window, without also applying a second root-level IME inset.

## Build outputs

- Android APK: Android APK workflow artifact MynAssistant-debug-apk.
- Windows companion: Windows Agent workflow artifact MynAssistant-Windows-Agent.

## Current scope

The new Windows Agent provides automatic device discovery, online heartbeats, volume/desktop/mouse commands, and an on-demand low-frame-rate JPEG desktop preview. Smooth full-screen remote desktop streaming is a later WebRTC milestone.
