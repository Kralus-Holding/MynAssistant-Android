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

## Hey Meyene — offline voice wake word (Android 0.4.3 candidate)

- Listening is explicitly enabled or disabled from **Settings → Arka planda dinle**. While enabled, the app shows an ongoing foreground-service notification and Android's microphone privacy indicator.
- The first activation downloads the Turkish Vosk model (about 35 MB in this configuration). Once stored locally, recognition runs on-device without sending speech audio to a speech cloud service.
- A successful “Hey Meyene” detection opens the compact floating panel over the current app. The user can review the transcript before opening the command in MynAssistant.
- Wake phrase matching now accepts common Vosk spelling variants and records the last successful wake time/count locally. This diagnostic metadata does not include raw audio.
- Settings includes shortcuts to overlay permission and app battery/background settings. These shortcuts do not override Android/OEM power management, and continuous listening may be interrupted by device policy or process termination.
- The command composer handles navigation-bar and keyboard IME insets through one root-level path. Automated unit tests cover the wake phrase transcript matcher; physical-device keyboard, accuracy, battery, and long-run tests are still required.

**Accuracy note:** the current pipeline is Vosk speech-to-text followed by tolerant text matching, not a dedicated custom acoustic wake-word neural network. It should not be advertised as Gemini-level wake-word detection. See [the Android voice assistant roadmap](docs/ANDROID_VOICE_ASSISTANT_ROADMAP.md) for platform constraints, release test gates, and future engine options.

## Build outputs

- Android APK: Android APK workflow artifact MynAssistant-debug-apk.
- Windows companion: Windows Agent workflow artifact MynAssistant-Windows-Agent.

## Current scope

The new Windows Agent provides automatic device discovery, online heartbeats, volume/desktop/mouse commands, and an on-demand low-frame-rate JPEG desktop preview. Smooth full-screen remote desktop streaming is a later WebRTC milestone.
